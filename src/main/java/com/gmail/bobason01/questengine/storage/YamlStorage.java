package com.gmail.bobason01.questengine.storage;

import com.gmail.bobason01.questengine.QuestEnginePlugin;
import com.gmail.bobason01.questengine.progress.PlayerData;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;

public final class YamlStorage implements StorageProvider {

    private final QuestEnginePlugin plugin;
    private final File folder;

    public YamlStorage(QuestEnginePlugin plugin) {
        this.plugin = plugin;
        this.folder = new File(plugin.getDataFolder(), "playerdata_yaml");
        if (!folder.exists()) folder.mkdirs();
    }

    private File fileOf(UUID id) {
        return new File(folder, id.toString() + ".yml");
    }

    @Override
    public PlayerData load(UUID id, String name) {
        File f = fileOf(id);
        if (!f.exists()) return new PlayerData(id, name);

        YamlConfiguration yml = YamlConfiguration.loadConfiguration(f);
        PlayerData d = new PlayerData(id, name);

        for (String qid : yml.getKeys(false)) {
            if (qid.equalsIgnoreCase("meta_language")) {
                d.setLanguage(yml.getString(qid, "en"));
                continue;
            }

            boolean active = yml.getBoolean(qid + ".active", false);
            boolean completed = yml.getBoolean(qid + ".completed", false);
            int value = yml.getInt(qid + ".value", 0);
            int points = yml.getInt(qid + ".points", 0);
            int repeatCount = yml.getInt(qid + ".repeat_count", 0);

            d.restoreQuest(qid, active, completed, value, points, repeatCount);
        }
        return d;
    }

    @Override
    public void save(PlayerData d) {
        File f = fileOf(d.getId());
        YamlConfiguration yml = new YamlConfiguration();

        for (var entry : d.snapshot().entrySet()) {
            String qid = entry.getKey();
            PlayerData.QuestState state = entry.getValue();
            yml.set(qid + ".active", state.active());
            yml.set(qid + ".completed", state.completed());
            yml.set(qid + ".value", state.value());
            yml.set(qid + ".points", state.points());
            yml.set(qid + ".repeat_count", state.repeatCount());
        }

        yml.set("meta_language", d.getLanguage());

        try {
            yml.save(f);
        } catch (IOException e) {
            plugin.getLogger().warning("[YamlStorage] Save failed for " + d.getId() + ": " + e.getMessage());
        }
    }

    @Override
    public Map<UUID, Integer> loadAllPointsApprox() {
        Map<UUID, Integer> out = new HashMap<>();
        File[] files = folder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null) return out;

        for (File f : files) {
            try {
                UUID id = UUID.fromString(f.getName().replace(".yml", ""));
                YamlConfiguration yml = YamlConfiguration.loadConfiguration(f);
                int total = 0;
                for (String qid : yml.getKeys(false)) {
                    if (qid.equalsIgnoreCase("meta_language")) continue;

                    if (yml.getBoolean(qid + ".completed", false)) {
                        total += yml.getInt(qid + ".points", 0);
                    }
                }
                out.put(id, total);
            } catch (Throwable ignored) {}
        }
        return out;
    }

    @Override public void preloadAll() {}

    @Override
    public void reset(UUID id) {
        File f = fileOf(id);
        if (f.exists()) f.delete();
    }

    @Override
    public void resetQuest(UUID id, String questId) {
        File f = fileOf(id);
        if (!f.exists()) return;

        YamlConfiguration yml = YamlConfiguration.loadConfiguration(f);
        yml.set(questId, null);
        try {
            yml.save(f);
        } catch (IOException ignored) {}
    }

    @Override public void close() {}
}