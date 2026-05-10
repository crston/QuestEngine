package com.gmail.bobason01.questengine.quest;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.*;

public final class QuestRepository {

    private final Plugin plugin;
    private final File dir;

    private volatile Map<String, QuestDef> byId = Collections.emptyMap();
    private volatile Map<String, List<QuestDef>> byEvent = Collections.emptyMap();

    private static final QuestDef[] EMPTY_ARRAY = new QuestDef[0];

    public QuestRepository(Plugin plugin, File dir) {
        this.plugin = plugin;
        this.dir = dir;

        if (!dir.exists() && !dir.mkdirs()) {
            plugin.getLogger().warning("[QuestEngine] Failed to create quest folder: " + dir.getAbsolutePath());
        }

        reload();
    }

    public synchronized void reload() {
        File[] files = dir.listFiles((d, n) -> n.endsWith(".yml") || n.endsWith(".yaml"));
        if (files == null || files.length == 0) {
            plugin.getLogger().info("[QuestEngine] No quest files found in " + dir.getName());
            this.byId = Collections.emptyMap();
            this.byEvent = Collections.emptyMap();
            return;
        }

        Map<String, QuestDef> newById = new HashMap<>(files.length);
        Map<String, List<QuestDef>> newByEvent = new HashMap<>();

        int count = 0;
        for (File f : files) {
            try {
                QuestDef q = QuestDef.load(f);
                if (q == null || q.id == null || q.id.isBlank()) {
                    plugin.getLogger().warning("[QuestEngine] Skipped invalid quest file: " + f.getName());
                    continue;
                }

                String lid = q.id.toLowerCase(Locale.ROOT);
                newById.put(lid, q);

                if (q.event != null && !q.event.isBlank()) {
                    String evtKey = q.event.trim().toUpperCase(Locale.ROOT);
                    newByEvent.computeIfAbsent(evtKey, k -> new ArrayList<>()).add(q);
                }
                count++;

            } catch (Throwable t) {
                plugin.getLogger().warning("[QuestEngine] Failed to load quest " + f.getName() + ": " + t.getMessage());
            }
        }

        for (Map.Entry<String, List<QuestDef>> e : newByEvent.entrySet()) {
            e.setValue(List.copyOf(e.getValue()));
        }

        this.byId = Map.copyOf(newById);
        this.byEvent = Map.copyOf(newByEvent);

        plugin.getLogger().info("[QuestEngine] Loaded " + count + " quests (Hot-Swapped)");
    }

    public void rebuildEventMap() {
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
        } catch (IOException ex) {
            plugin.getLogger().warning("[QuestEngine] Failed to save quest " + quest.id + ": " + ex.getMessage());
        }
    }
}