package com.gmail.bobason01.questengine.storage;

import com.gmail.bobason01.questengine.QuestEnginePlugin;
import com.gmail.bobason01.questengine.progress.PlayerData;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
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
                int repeatCount = 0;
                if (in.available() > 0) {
                    try { repeatCount = in.readInt(); } catch (EOFException ignored) {}
                }

                if (active) data.start(qid);
                if (value > 0) data.add(qid, value);

                if (repeatCount > 0) data.setRepeatCount(qid, repeatCount);

                if (completed) {
                    data.complete(qid, points);
                    if (data.getRepeatCount(qid) == 0) data.setRepeatCount(qid, 1);
                }
            }

            if (in.available() > 0) {
                try {
                    String lang = in.readUTF();
                    data.setLanguage(lang);
                } catch (EOFException ignored) {}
            }

            return data;
        } catch (Throwable t) {
            plugin.getLogger().warning("[FileStorage] Load failed for " + id + ": " + t.getMessage());
            return new PlayerData(id, name);
        }
    }

    @Override
    public void save(PlayerData d) {
        File f = fileOf(d.getId());
        File tmp = new File(folder, d.getId() + ".dat.tmp");

        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(tmp)))) {
            Set<String> all = new HashSet<>();
            all.addAll(d.activeIds());
            all.addAll(d.completedIds());

            out.writeInt(all.size());
            for (String qid : all) {
                out.writeUTF(qid);
                out.writeBoolean(d.isActive(qid));
                out.writeBoolean(d.isCompleted(qid));
                out.writeInt(d.valueOf(qid));
                out.writeInt(d.pointsOf(qid));
                out.writeInt(d.getRepeatCount(qid));
            }

            out.writeUTF(d.getLanguage());

            out.flush();
            out.close();

            Files.move(tmp.toPath(), f.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);

        } catch (Throwable t) {
            plugin.getLogger().warning("[FileStorage] Save failed for " + d.getId() + ": " + t.getMessage());
            if (tmp.exists()) tmp.delete();
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

                    if (in.available() >= 4) in.skipBytes(4);

                    if (completed) total += pts;
                }
                map.put(id, total);
            } catch (Throwable ignored) {}
        }
        return map;
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
        PlayerData d = load(id, "unknown");
        d.resetQuest(questId);
        save(d);
    }

    @Override
    public void close() {}
}