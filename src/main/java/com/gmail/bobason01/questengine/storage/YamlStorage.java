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
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(f);

        PlayerData d = new PlayerData(id, name);

        for (String qid : yml.getKeys(false)) {

            boolean active = yml.getBoolean(qid + ".active", false);
            boolean completed = yml.getBoolean(qid + ".completed", false);
            int value = yml.getInt(qid + ".value", 0);
            int points = yml.getInt(qid + ".points", 0);
            int repeatCount = yml.getInt(qid + ".repeat_count", 0);

            if (active) {
                d.start(qid);
            }

            if (value > 0) {
                d.add(qid, value);
            }

            if (repeatCount > 0) {
                d.setRepeatCount(qid, repeatCount);
            }

            if (completed) {
                d.setRepeatCount(qid, Math.max(1, repeatCount));
            }
        }

        return d;
    }

    @Override
    public void save(PlayerData d) {
        File f = fileOf(d.getId());
        YamlConfiguration yml = new YamlConfiguration();

        Set<String> all = new HashSet<>();
        all.addAll(d.getActiveQuests());
        all.addAll(d.getCompletedQuests());

        for (String qid : all) {

            boolean active = d.isActive(qid);
            boolean completed = d.isCompleted(qid);
            int value = d.valueOf(qid);
            int points = d.pointsOf(qid);
            int repeatCount = d.getRepeatCount(qid);

            yml.set(qid + ".active", active);
            yml.set(qid + ".completed", completed);
            yml.set(qid + ".value", value);
            yml.set(qid + ".points", points);
            yml.set(qid + ".repeat_count", repeatCount);
        }

        try {
            yml.save(f);
        } catch (IOException e) {
            plugin.getLogger().warning("YamlStorage save failed for " + d.getId() + ": " + e.getMessage());
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
                    boolean completed = yml.getBoolean(qid + ".completed", false);
                    int pts = yml.getInt(qid + ".points", 0);
                    if (completed) {
                        total += pts;
                    }
                }
                out.put(id, total);

            } catch (Throwable ignored) {}
        }

        return out;
    }

    @Override
    public void preloadAll() {}

    @Override
    public void reset(UUID id) {
        File f = fileOf(id);
        if (f.exists()) f.delete();
    }

    @Override
    public void resetQuest(UUID id, String questId) {
        File f = fileOf(id);
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(f);
        yml.set(questId, null);
        try {
            yml.save(f);
        } catch (Throwable ignored) {}
    }

    @Override
    public void close() {}
}
