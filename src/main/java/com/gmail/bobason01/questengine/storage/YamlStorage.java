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
            if (active) d.start(qid);
            if (completed) d.complete(qid, points);
            if (value > 0) d.add(qid, value);
        }
        return d;
    }

    @Override
    public void save(PlayerData d) {
        File f = fileOf(d.getId());
        YamlConfiguration yml = new YamlConfiguration();
        Set<String> all = new HashSet<>(d.activeIds());
        all.addAll(d.completedIds());
        for (String qid : all) {
            yml.set(qid + ".active", d.isActive(qid));
            yml.set(qid + ".completed", d.isCompleted(qid));
            yml.set(qid + ".value", d.valueOf(qid));
            yml.set(qid + ".points", d.isCompleted(qid) ? d.pointsOf(qid) : 0);
        }
        try {
            yml.save(f);
        } catch (IOException e) {
            plugin.getLogger().warning("[YamlStorage] save failed for " + d.getId() + ": " + e.getMessage());
        }
    }

    @Override
    public Map<UUID, Integer> loadAllPointsApprox() {
        Map<UUID, Integer> map = new HashMap<>();
        File[] files = folder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null) return map;
        for (File f : files) {
            try {
                UUID id = UUID.fromString(f.getName().replace(".yml", ""));
                YamlConfiguration yml = YamlConfiguration.loadConfiguration(f);
                int total = 0;
                for (String qid : yml.getKeys(false)) {
                    boolean completed = yml.getBoolean(qid + ".completed", false);
                    int pts = yml.getInt(qid + ".points", 0);
                    if (completed) total += pts;
                }
                map.put(id, total);
            } catch (Throwable ignored) {}
        }
        return map;
    }

    @Override
    public void preloadAll() {
        // No op
    }

    @Override
    public void reset(UUID id) {
        File f = fileOf(id);
        if (f.exists()) f.delete();
    }

    @Override
    public void resetQuest(UUID id, String questId) {
        File f = fileOf(id);
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(f);
        if (yml.contains(questId)) {
            yml.set(questId, null);
            try {
                yml.save(f);
            } catch (IOException ignored) {}
        }
    }

    @Override
    public void close() {
        // no resources
    }
}
