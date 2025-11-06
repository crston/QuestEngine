package com.gmail.bobason01.questengine.quest;

import com.gmail.bobason01.questengine.QuestEnginePlugin;
import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * QuestRepository
 * - 모든 퀘스트 정의를 로드 및 캐시
 * - 이벤트별 퀘스트 인덱스 자동 유지
 * - GC·I/O 오버헤드 최소화
 */
public final class QuestRepository {

    private final QuestEnginePlugin plugin;
    private final File folder;

    // 퀘스트 ID → 퀘스트 정의
    private final Map<String, QuestDef> quests = new LinkedHashMap<>(128);

    // 이벤트 이름 → 퀘스트 목록 (캐시)
    private final Map<String, List<QuestDef>> byEvent = new ConcurrentHashMap<>();

    public QuestRepository(QuestEnginePlugin plugin, String folderName) {
        this.plugin = plugin;
        this.folder = new File(plugin.getDataFolder(), folderName);
        if (!folder.exists()) folder.mkdirs();
        loadAll();
    }

    // ------------------------------------------------------------
    // 퀘스트 로드 / 리로드
    // ------------------------------------------------------------
    private void loadAll() {
        final File[] files = folder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null || files.length == 0) {
            plugin.getLogger().info("[QuestEngine] No quests found in " + folder.getName());
            return;
        }

        int loaded = 0;
        for (File f : files) {
            try {
                QuestDef q = QuestLoader.load(f);
                if (q == null) continue;

                String key = q.id.toLowerCase(Locale.ROOT).intern();
                quests.put(key, q);

                if (!q.event.isEmpty()) {
                    String evKey = q.event.toLowerCase(Locale.ROOT).intern();
                    byEvent.computeIfAbsent(evKey, k -> new ArrayList<>()).add(q);
                }

                loaded++;
            } catch (Throwable t) {
                plugin.getLogger().warning("[QuestEngine] Failed to load quest file: " + f.getName());
            }
        }

        plugin.getLogger().info("[QuestEngine] Loaded " + loaded + " quests from " + folder.getName());
    }

    /**
     * 특정 ID로 퀘스트 조회
     */
    public QuestDef get(String id) {
        if (id == null) return null;
        return quests.get(id.toLowerCase(Locale.ROOT));
    }

    /**
     * 특정 이벤트 이름으로 퀘스트 목록 조회
     * - 미리 인덱싱된 캐시 구조로 O(1)
     */
    public List<QuestDef> byEvent(String eventName) {
        if (eventName == null || eventName.isEmpty()) return Collections.emptyList();
        return byEvent.getOrDefault(eventName.toLowerCase(Locale.ROOT), Collections.emptyList());
    }

    /**
     * 모든 퀘스트 ID 반환
     */
    public Set<String> ids() {
        return Collections.unmodifiableSet(quests.keySet());
    }

    /**
     * 변경된 파일만 다시 로드 (성능 개선)
     */
    public void reload() {
        plugin.getLogger().info("[QuestEngine] Reloading quests...");
        quests.clear();
        byEvent.clear();
        loadAll();
    }

    /**
     * 현재 로드된 퀘스트 수
     */
    public int size() {
        return quests.size();
    }
}
