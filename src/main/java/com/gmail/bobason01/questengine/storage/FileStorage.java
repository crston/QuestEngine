package com.gmail.bobason01.questengine.storage;

import com.gmail.bobason01.questengine.QuestEnginePlugin;
import com.gmail.bobason01.questengine.progress.PlayerData;

import java.io.*;
import java.util.*;

public final class FileStorage implements StorageProvider {

    private final QuestEnginePlugin plugin;
    private final File folder;

    public FileStorage(QuestEnginePlugin plugin) {
        this.plugin = plugin;
        this.folder = new File(plugin.getDataFolder(), "playerdata");
        if (!folder.exists()) folder.mkdirs();
    }

    private File fileOf(UUID id) {
        return new File(folder, id.toString() + ".dat");
    }

    @Override
    public PlayerData load(UUID id, String name) {
        File f = fileOf(id);
        if (!f.exists()) return new PlayerData(id, name);
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(f)))) {
            PlayerData data = new PlayerData(id, name);
            int count = in.readInt();
            for (int i = 0; i < count; i++) {
                String qid = in.readUTF();
                boolean active = in.readBoolean();
                boolean completed = in.readBoolean();
                int value = in.readInt();
                int points = in.readInt();
                if (active) data.start(qid);
                if (completed) data.complete(qid, points);
                if (value > 0) data.add(qid, value);
            }
            return data;
        } catch (Throwable t) {
            plugin.getLogger().warning("[FileStorage] load failed for " + id + ": " + t.getMessage());
            return new PlayerData(id, name);
        }
    }

    @Override
    public void save(PlayerData d) {
        File f = fileOf(d.getId());
        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(f)))) {
            Set<String> all = new HashSet<>(d.activeIds());
            all.addAll(d.completedIds());
            out.writeInt(all.size());
            for (String qid : all) {
                out.writeUTF(qid);
                out.writeBoolean(d.isActive(qid));
                out.writeBoolean(d.isCompleted(qid));
                out.writeInt(d.valueOf(qid));
                out.writeInt(d.isCompleted(qid) ? d.pointsOf(qid) : 0);
            }
        } catch (Throwable t) {
            plugin.getLogger().warning("[FileStorage] save failed for " + d.getId() + ": " + t.getMessage());
        }
    }

    @Override
    public Map<UUID, Integer> loadAllPointsApprox() {
        Map<UUID, Integer> map = new HashMap<>();
        File[] files = folder.listFiles((dir, name) -> name.endsWith(".dat"));
        if (files == null) return map;
        for (File f : files) {
            try (DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(f)))) {
                UUID id = UUID.fromString(f.getName().replace(".dat", ""));
                int total = 0;
                int count = in.readInt();
                for (int i = 0; i < count; i++) {
                    in.readUTF();
                    in.readBoolean();
                    boolean completed = in.readBoolean();
                    in.readInt();
                    int pts = in.readInt();
                    if (completed) total += pts;
                }
                map.put(id, total);
            } catch (Throwable ignored) {}
        }
        return map;
    }

    @Override
    public void preloadAll() {
        File[] files = folder.listFiles((dir, name) -> name.endsWith(".dat"));
        if (files == null) return;
        // No op here. Higher layer caches loaded data on demand.
    }

    @Override
    public void reset(UUID id) {
        File f = fileOf(id);
        if (f.exists()) f.delete();
    }

    @Override
    public void resetQuest(UUID id, String questId) {
        PlayerData d = load(id, "unknown");
        d.cancel(questId);
        save(d);
    }

    @Override
    public void close() {
        // no resources
    }
}
