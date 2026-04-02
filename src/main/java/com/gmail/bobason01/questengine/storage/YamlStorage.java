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
            boolean active = yml.getBoolean(qid + ".active", false);
            boolean completed = yml.getBoolean(qid + ".completed", false);
            int value = yml.getInt(qid + ".value", 0);
            int points = yml.getInt(qid + ".points", 0);
            int repeatCount = yml.getInt(qid + ".repeat_count", 0);

            if (active) d.start(qid);
            if (value > 0) d.add(qid, value);
            if (repeatCount > 0) d.setRepeatCount(qid, repeatCount);

            if (completed) {
                d.complete(qid, points); // 완료 상태 설정
                // 완료했는데 카운트가 0이면 보정
                if (d.getRepeatCount(qid) == 0) d.setRepeatCount(qid, 1);
            }
        }
        return d;
    }

    @Override
    public void save(PlayerData d) {
        File f = fileOf(d.getId());
        YamlConfiguration yml = new YamlConfiguration();

        // 중복 제거를 위해 Set 사용
        Set<String> all = new HashSet<>();
        all.addAll(d.activeIds());
        all.addAll(d.completedIds());

        for (String qid : all) {
            yml.set(qid + ".active", d.isActive(qid));
            yml.set(qid + ".completed", d.isCompleted(qid));
            yml.set(qid + ".value", d.valueOf(qid));
            yml.set(qid + ".points", d.pointsOf(qid));
            yml.set(qid + ".repeat_count", d.getRepeatCount(qid));
        }

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
                // 전체 로드는 무거우므로, 포인트 계산만 빠르게 하기 위해선 별도 캐시 파일이 낫지만,
                // 여기선 YAML 파싱을 최소화할 방법이 없으므로 그대로 진행
                YamlConfiguration yml = YamlConfiguration.loadConfiguration(f);
                int total = 0;
                for (String qid : yml.getKeys(false)) {
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
        yml.set(questId, null); // 섹션 삭제
        try {
            yml.save(f);
        } catch (IOException ignored) {}
    }

    @Override public void close() {}
}