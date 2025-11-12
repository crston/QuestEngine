package com.gmail.bobason01.questengine.quest;

import org.bukkit.plugin.Plugin;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * QuestRepository
 * 고성능 퀘스트 정의 저장소
 * - quests 폴더 내 YAML 퀘스트 자동 로드
 * - ID / EVENT 인덱스 동시 관리
 * - 대소문자 불일치 방지 (이벤트 키 자동 대문자화)
 * - reload() 시 스레드 세이프한 캐시 갱신
 */
public final class QuestRepository {

    private final Plugin plugin;
    private final File dir;

    /** id → QuestDef */
    private final Map<String, QuestDef> byId = new ConcurrentHashMap<>();
    /** event → [QuestDef...] */
    private final Map<String, List<QuestDef>> byEvent = new ConcurrentHashMap<>();

    public QuestRepository(Plugin plugin, File dir) {
        this.plugin = plugin;
        this.dir = dir;

        if (!dir.exists() && !dir.mkdirs()) {
            plugin.getLogger().warning("[QuestEngine] Failed to create quest folder: " + dir.getAbsolutePath());
        }

        reload();
        rebuildEventMap();
    }

    // ---------------------------------------------------------
    // 로드 및 빌드
    // ---------------------------------------------------------
    public void reload() {
        byId.clear();

        File[] files = dir.listFiles((d, n) -> n.endsWith(".yml") || n.endsWith(".yaml"));
        if (files == null || files.length == 0) {
            plugin.getLogger().info("[QuestEngine] No quest files found in " + dir.getName());
            return;
        }

        int count = 0;
        for (File f : files) {
            try {
                QuestDef q = QuestDef.loadFromFile(f);
                if (q == null || q.id == null || q.id.isBlank()) {
                    plugin.getLogger().warning("[QuestEngine] Skipped invalid quest file: " + f.getName());
                    continue;
                }
                byId.put(q.id, q);
                count++;
            } catch (Throwable t) {
                plugin.getLogger().warning("[QuestEngine] Failed to load quest " + f.getName() + ": " + t.getMessage());
            }
        }

        plugin.getLogger().info("[QuestEngine] Loaded " + count + " quests from " + dir.getName());
    }

    /**
     * event → quests 맵 재구성
     * 대소문자 안전하게 전부 대문자 키로 변환.
     */
    public void rebuildEventMap() {
        byEvent.clear();
        for (QuestDef q : byId.values()) {
            if (q == null || q.event == null || q.event.isBlank()) continue;
            String key = q.event.trim().toUpperCase(Locale.ROOT);
            byEvent.computeIfAbsent(key, k -> new ArrayList<>()).add(q);
        }

        // 불변 리스트로 교체 (외부 수정 방지)
        for (Map.Entry<String, List<QuestDef>> e : byEvent.entrySet()) {
            e.setValue(List.copyOf(e.getValue()));
        }

        plugin.getLogger().info("[QuestEngine] Event map built (" + byEvent.size() + " event types)");
    }

    // ---------------------------------------------------------
    // 조회 메서드
    // ---------------------------------------------------------
    /** 퀘스트 ID로 직접 조회 */
    public QuestDef get(String id) {
        if (id == null) return null;
        return byId.get(id);
    }

    /** 모든 퀘스트 ID 반환 */
    public Set<String> ids() {
        return Collections.unmodifiableSet(byId.keySet());
    }

    /** 이벤트 이름으로 퀘스트 목록 조회 (대소문자 무시) */
    public QuestDef[] byEvent(String eventKey) {
        if (eventKey == null || eventKey.isBlank()) return new QuestDef[0];
        List<QuestDef> list = byEvent.get(eventKey.trim().toUpperCase(Locale.ROOT));
        if (list == null || list.isEmpty()) return new QuestDef[0];
        return list.toArray(new QuestDef[0]);
    }

    /** 전체 퀘스트 목록 반환 */
    public Collection<QuestDef> all() {
        return Collections.unmodifiableCollection(byId.values());
    }

    // ---------------------------------------------------------
    // 유틸
    // ---------------------------------------------------------
    /** 디스크에 퀘스트를 새로 저장 (테스트용 또는 동적 생성용) */
    public void saveToFile(QuestDef quest) {
        if (quest == null || quest.id == null) return;
        File out = new File(dir, quest.id + ".yml");
        try {
            YamlConfiguration yaml = QuestDef.toYaml(quest);
            yaml.save(out);
            plugin.getLogger().info("[QuestEngine] Saved quest " + quest.id + " to file.");
        } catch (IOException ex) {
            plugin.getLogger().warning("[QuestEngine] Failed to save quest " + quest.id + ": " + ex.getMessage());
        }
    }
    public QuestDef byId(String id) {
        return byId.get(id.toLowerCase(Locale.ROOT));
    }
}
