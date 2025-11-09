package com.gmail.bobason01.questengine.quest;

import com.gmail.bobason01.questengine.QuestEnginePlugin;

import java.io.*;
import java.net.JarURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * QuestRepository
 * - YAML 퀘스트 파일을 전부 로드 및 캐시
 * - 이벤트별 인덱스 O(1) 조회
 * - jar 내부 quests 폴더도 자동 추출
 * - 완전 불변 구조 유지로 GC 부담 최소화
 */
public final class QuestRepository {

    private final QuestEnginePlugin plugin;
    private final File folder;

    /** 퀘스트 ID → 정의 */
    private volatile Map<String, QuestDef> quests = Map.of();

    /** 이벤트 이름 → 퀘스트 배열 (GC-free 접근) */
    private volatile Map<String, QuestDef[]> byEvent = Map.of();

    public QuestRepository(QuestEnginePlugin plugin, String folderName) {
        this.plugin = plugin;
        this.folder = new File(plugin.getDataFolder(), folderName);
        if (!folder.exists()) folder.mkdirs();
        reload();
    }

    // =============================================================
    // 리로드 (외부 + JAR 내부 quests 폴더 모두)
    // =============================================================

    public synchronized void reload() {
        long start = System.nanoTime();

        // jar 내부 기본 퀘스트 자동 추출
        extractBundledQuests();

        File[] files = folder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null || files.length == 0) {
            quests = Map.of();
            byEvent = Map.of();
            plugin.getLogger().warning("[QuestEngine] No quests found in " + folder.getPath());
            return;
        }

        Map<String, QuestDef> tmpQuests = new HashMap<>(Math.max(16, files.length));
        Map<String, List<QuestDef>> tmpByEvent = new HashMap<>(128);

        for (File f : files) {
            try {
                QuestDef q = QuestDef.load(f);
                if (q == null) continue;

                String idKey = safeLower(q.id);
                if (idKey == null || idKey.isEmpty()) {
                    plugin.getLogger().warning("[QuestEngine] Quest file missing id: " + f.getName());
                    continue;
                }
                tmpQuests.put(idKey, q);

                String evKey = safeLower(q.event);
                if (evKey == null || evKey.isEmpty()) evKey = "*";
                tmpByEvent.computeIfAbsent(evKey, k -> new ArrayList<>()).add(q);

            } catch (Throwable t) {
                plugin.getLogger().warning("[QuestEngine] Failed to load " + f.getName() + ": " + t.getMessage());
            }
        }

        // List → Array 변환 (고정 배열 접근)
        Map<String, QuestDef[]> eventMap = new HashMap<>(tmpByEvent.size());
        for (Map.Entry<String, List<QuestDef>> e : tmpByEvent.entrySet()) {
            eventMap.put(e.getKey(), e.getValue().toArray(QuestDef[]::new));
        }

        quests = Map.copyOf(tmpQuests);
        byEvent = Map.copyOf(eventMap);

        long took = (System.nanoTime() - start) / 1_000_000L;
        plugin.getLogger().info("[QuestEngine] Loaded " + quests.size() + " quests (" + took + "ms)");
    }

    // =============================================================
    // 이벤트 캐시 재구성 (디스크 접근 없음)
    // =============================================================

    public synchronized void rebuildEventMap() {
        Map<String, List<QuestDef>> tmpByEvent = new HashMap<>(128);
        for (QuestDef q : quests.values()) {
            if (q == null) continue;
            String evKey = safeLower(q.event);
            if (evKey == null || evKey.isEmpty()) evKey = "*";
            tmpByEvent.computeIfAbsent(evKey, k -> new ArrayList<>()).add(q);
        }

        Map<String, QuestDef[]> eventMap = new HashMap<>(tmpByEvent.size());
        for (Map.Entry<String, List<QuestDef>> e : tmpByEvent.entrySet()) {
            eventMap.put(e.getKey(), e.getValue().toArray(QuestDef[]::new));
        }

        byEvent = Map.copyOf(eventMap);
    }

    // =============================================================
    // 조회 메서드
    // =============================================================

    public QuestDef get(String id) {
        if (id == null) return null;
        return quests.get(id.toLowerCase(Locale.ROOT));
    }

    public QuestDef[] byEvent(String eventName) {
        if (eventName == null || eventName.isEmpty()) return new QuestDef[0];
        return byEvent.getOrDefault(eventName.toLowerCase(Locale.ROOT), new QuestDef[0]);
    }

    public Set<String> ids() {
        return quests.keySet();
    }

    /**
     * 전체 퀘스트 컬렉션 반환 (GUI / 디버그 / 명령어용)
     * Map.copyOf 기반으로 GC 안전한 불변 컬렉션 제공.
     */
    public Collection<QuestDef> all() {
        return Collections.unmodifiableCollection(quests.values());
    }

    public int size() {
        return quests.size();
    }

    // =============================================================
    // 내부 유틸
    // =============================================================

    private String safeLower(String s) {
        return s == null ? null : s.toLowerCase(Locale.ROOT);
    }

    // =============================================================
    // jar 내부 quests/ 자동 추출 (getFile() 사용하지 않음)
    // =============================================================

    private void extractBundledQuests() {
        try {
            URL res = plugin.getClass().getClassLoader().getResource("quests/");
            if (res == null) return;

            if ("jar".equals(res.getProtocol())) {
                JarURLConnection conn = (JarURLConnection) res.openConnection();
                try (JarFile jar = conn.getJarFile()) {
                    Enumeration<JarEntry> entries = jar.entries();
                    while (entries.hasMoreElements()) {
                        JarEntry e = entries.nextElement();
                        if (e.isDirectory()) continue;
                        String name = e.getName();
                        if (!name.startsWith("quests/") || !name.endsWith(".yml")) continue;

                        String fileName = name.substring("quests/".length());
                        File out = new File(folder, fileName);
                        if (out.exists()) continue;

                        out.getParentFile().mkdirs();
                        try (InputStream in = jar.getInputStream(e);
                             OutputStream os = Files.newOutputStream(out.toPath())) {
                            in.transferTo(os);
                        } catch (Throwable ex) {
                            plugin.getLogger().warning("[QuestEngine] Failed to extract quest " + name + ": " + ex.getMessage());
                        }
                    }
                }
            } else if ("file".equals(res.getProtocol())) {
                // 개발 환경용 (resources 폴더가 파일 시스템일 때)
                File dir = new File(res.toURI());
                File[] list = dir.listFiles((d, n) -> n.endsWith(".yml"));
                if (list == null) return;
                for (File f : list) {
                    File out = new File(folder, f.getName());
                    if (!out.exists()) Files.copy(f.toPath(), out.toPath());
                }
            }

        } catch (Throwable t) {
            plugin.getLogger().warning("[QuestEngine] Failed to extract internal quests: " + t.getMessage());
        }
    }
}
