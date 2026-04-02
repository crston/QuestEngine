package com.gmail.bobason01.questengine.quest;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.*;

public final class QuestRepository {

    private final Plugin plugin;
    private final File dir;

    // Volatile로 가시성 보장 (리로드 시 통째로 교체됨)
    private volatile Map<String, QuestDef> byId = Collections.emptyMap();
    private volatile Map<String, List<QuestDef>> byEvent = Collections.emptyMap();

    // 빈 배열 캐싱 (GC 최적화)
    private static final QuestDef[] EMPTY_ARRAY = new QuestDef[0];

    public QuestRepository(Plugin plugin, File dir) {
        this.plugin = plugin;
        this.dir = dir;

        if (!dir.exists() && !dir.mkdirs()) {
            plugin.getLogger().warning("[QuestEngine] Failed to create quest folder: " + dir.getAbsolutePath());
        }

        reload();
    }

    /**
     * 퀘스트 전체 리로드 (Hot-Swap)
     */
    public synchronized void reload() {
        File[] files = dir.listFiles((d, n) -> n.endsWith(".yml") || n.endsWith(".yaml"));
        if (files == null || files.length == 0) {
            plugin.getLogger().info("[QuestEngine] No quest files found in " + dir.getName());
            this.byId = Collections.emptyMap();
            this.byEvent = Collections.emptyMap();
            return;
        }

        // 임시 맵 생성
        Map<String, QuestDef> newById = new HashMap<>(files.length);
        Map<String, List<QuestDef>> newByEvent = new HashMap<>();

        int count = 0;
        for (File f : files) {
            try {
                // QuestDef.load가 최적화됨
                QuestDef q = QuestDef.load(f);
                if (q == null || q.id == null || q.id.isBlank()) {
                    plugin.getLogger().warning("[QuestEngine] Skipped invalid quest file: " + f.getName());
                    continue;
                }

                String lid = q.id.toLowerCase(Locale.ROOT);
                newById.put(lid, q);

                // Event Map 구성
                if (q.event != null && !q.event.isBlank()) {
                    String evtKey = q.event.trim().toUpperCase(Locale.ROOT);
                    newByEvent.computeIfAbsent(evtKey, k -> new ArrayList<>()).add(q);
                }
                count++;

            } catch (Throwable t) {
                plugin.getLogger().warning("[QuestEngine] Failed to load quest " + f.getName() + ": " + t.getMessage());
            }
        }

        // Event Map 불변 리스트로 변환
        for (Map.Entry<String, List<QuestDef>> e : newByEvent.entrySet()) {
            e.setValue(List.copyOf(e.getValue())); // Java 10+
        }

        // Atomic Swap (참조 교체)
        this.byId = Map.copyOf(newById); // Java 10+
        this.byEvent = Map.copyOf(newByEvent);

        plugin.getLogger().info("[QuestEngine] Loaded " + count + " quests (Hot-Swapped)");
    }

    // --- Accessors (Thread-Safe via Volatile Swap) ---

    public void rebuildEventMap() {
        // reload() 메서드 안에서 이미 수행되므로, 별도로 호출할 필요 없음.
        // 하위 호환성을 위해 남겨두되, 실제로는 reload를 호출하거나 아무것도 안 함.
        // 여기서는 reload가 이미 완료된 상태라고 가정하고 로그만 출력.
        plugin.getLogger().info("[QuestEngine] Event map is managed by reload()");
    }

    public QuestDef get(String id) {
        if (id == null) return null;
        return byId.get(id.toLowerCase(Locale.ROOT));
    }

    public QuestDef byId(String id) {
        return get(id);
    }

    public Set<String> ids() {
        return byId.keySet();
    }

    public QuestDef[] byEvent(String eventKey) {
        if (eventKey == null || eventKey.isBlank()) return EMPTY_ARRAY;
        List<QuestDef> list = byEvent.get(eventKey.trim().toUpperCase(Locale.ROOT));
        return list == null ? EMPTY_ARRAY : list.toArray(EMPTY_ARRAY);
    }

    public Collection<QuestDef> all() {
        return byId.values();
    }

    public void saveToFile(QuestDef quest) {
        if (quest == null || quest.id == null) return;
        File out = new File(dir, quest.id + ".yml");
        try {
            YamlConfiguration yaml = QuestDef.toYaml(quest);
            yaml.save(out);
            plugin.getLogger().info("[QuestEngine] Saved quest " + quest.id);
            // 저장 후 리로드는 호출자가 결정 (보통 에디터 저장 후 reload 호출함)
        } catch (IOException ex) {
            plugin.getLogger().warning("[QuestEngine] Failed to save quest " + quest.id + ": " + ex.getMessage());
        }
    }
}