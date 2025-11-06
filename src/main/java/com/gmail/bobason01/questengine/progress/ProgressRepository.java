package com.gmail.bobason01.questengine.progress;

import com.gmail.bobason01.questengine.QuestEnginePlugin;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * ProgressRepository
 * - 완전한 비동기 안전 / 고성능 캐시 중심 구조
 * - Java 직렬화 제거 (DataOutputStream 기반)
 * - 디스크 I/O 최소화, 메모리 우선 캐시
 * - TPS 안정 보장 (200명 이상 서버에서도 안정)
 */
public final class ProgressRepository {

    private final QuestEnginePlugin plugin;
    private final File folder;

    // 캐시: 플레이어 UUID → PlayerData
    private final ConcurrentMap<UUID, PlayerData> cache = new ConcurrentHashMap<>();

    // 락: 플레이어별/퀘스트별 동시 접근 제어
    private final ConcurrentMap<String, Object> locks = new ConcurrentHashMap<>();

    // 비동기 저장 큐
    private final BlockingQueue<UUID> saveQueue = new LinkedBlockingQueue<>();

    // 백그라운드 I/O 쓰레드
    private final ScheduledExecutorService ioExecutor =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "QuestEngine-IO");
                t.setDaemon(true);
                return t;
            });

    // 파일 목록 캐시
    private volatile File[] cachedFiles;
    private long lastScanTime = 0L;

    public ProgressRepository(QuestEnginePlugin plugin) {
        this.plugin = plugin;
        this.folder = new File(plugin.getDataFolder(), "playerdata");
        if (!folder.exists()) folder.mkdirs();

        // 3초마다 저장 큐 flush
        ioExecutor.scheduleAtFixedRate(this::flushQueue, 3, 3, TimeUnit.SECONDS);
    }

    private Object lockFor(UUID id, String qid) {
        return locks.computeIfAbsent(id.toString() + "|" + qid, k -> new Object());
    }

    private File fileOf(UUID id) {
        return new File(folder, id.toString() + ".dat");
    }

    // ============================================================
    // Core Access
    // ============================================================

    public PlayerData of(UUID id, String name) {
        return cache.computeIfAbsent(id, k -> load(id, name));
    }

    private PlayerData load(UUID id, String name) {
        File f = fileOf(id);
        if (!f.exists()) return new PlayerData(id, name);

        try (DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(f)))) {
            PlayerData data = new PlayerData(id, name);
            int count = in.readInt();
            for (int i = 0; i < count; i++) {
                String qid = in.readUTF();
                PlayerData.Node n = new PlayerData.Node();
                n.active = in.readBoolean();
                n.completed = in.readBoolean();
                n.value = in.readInt();
                n.points = in.readInt();
                if (n.active) data.start(qid);
                if (n.completed) data.complete(qid, n.points);
                if (n.value > 0) data.add(qid, n.value);
            }
            return data;
        } catch (Throwable t) {
            plugin.getLogger().warning("[QuestEngine] Failed to load data for " + id + ": " + t.getMessage());
            return new PlayerData(id, name);
        }
    }

    // ============================================================
    // Saving
    // ============================================================

    private void flushQueue() {
        Set<UUID> unique = new HashSet<>();
        saveQueue.drainTo(unique);
        for (UUID id : unique) {
            try {
                saveNow(id);
            } catch (Throwable ignored) {}
        }
    }

    private void enqueueSave(UUID id) {
        if (id != null) saveQueue.offer(id);
    }

    private void saveNow(UUID id) {
        PlayerData d = cache.get(id);
        if (d == null) return;

        File f = fileOf(id);
        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(f)))) {
            var active = d.activeIds();
            var completed = d.completedIds();
            Set<String> all = new HashSet<>(active);
            all.addAll(completed);

            out.writeInt(all.size());
            for (String qid : all) {
                out.writeUTF(qid);
                out.writeBoolean(d.isActive(qid));
                out.writeBoolean(d.isCompleted(qid));
                out.writeInt(d.valueOf(qid));
                out.writeInt(d.isCompleted(qid) ? d.pointsOf(qid) : 0);
            }
        } catch (Throwable t) {
            plugin.getLogger().warning("[QuestEngine] Save error for " + id + ": " + t.getMessage());
        }
    }

    // ============================================================
    // Public API
    // ============================================================

    public boolean isActive(UUID id, String name, String qid) {
        return of(id, name).isActive(qid);
    }

    public void start(UUID id, String name, String qid) {
        synchronized (lockFor(id, qid)) {
            of(id, name).start(qid);
        }
        enqueueSave(id);
    }

    public void cancel(UUID id, String name, String qid) {
        synchronized (lockFor(id, qid)) {
            of(id, name).cancel(qid);
        }
        enqueueSave(id);
    }

    public void complete(UUID id, String name, String qid, int pts) {
        synchronized (lockFor(id, qid)) {
            of(id, name).complete(qid, pts);
        }
        enqueueSave(id);
    }

    public int addProgress(UUID id, String name, String qid, int amt) {
        synchronized (lockFor(id, qid)) {
            int v = of(id, name).add(qid, amt);
            enqueueSave(id);
            return v;
        }
    }

    public int value(UUID id, String name, String qid) {
        return of(id, name).valueOf(qid);
    }

    public List<String> activeQuestIds(UUID id, String name) {
        return of(id, name).activeIds();
    }

    public void cancelAll(UUID id, String name) {
        PlayerData d = of(id, name);
        synchronized (d) {
            d.cancelAll();
        }
        enqueueSave(id);
    }

    public boolean isCompleted(UUID id, String name, String qid) {
        return of(id, name).isCompleted(qid);
    }

    public List<String> completedQuestIds(UUID id, String name) {
        return of(id, name).completedIds();
    }

    public int activeCount(UUID id, String name) {
        return of(id, name).activeCount();
    }

    public int completedCount(UUID id, String name) {
        return of(id, name).completedIds().size();
    }

    public int totalPoints(UUID id, String name) {
        return of(id, name).totalPoints();
    }

    public String firstActiveId(UUID id, String name) {
        return of(id, name).firstActiveId();
    }

    public int cacheSize() {
        return cache.size();
    }

    // ============================================================
    // Reset & Lifecycle
    // ============================================================

    public void reset(UUID id) {
        cache.remove(id);
        File f = fileOf(id);
        if (f.exists()) f.delete();
    }

    public void reset(UUID id, String name, String questId) {
        synchronized (lockFor(id, questId)) {
            of(id, name).cancel(questId);
        }
        enqueueSave(id);
    }

    public void close() {
        flushQueue();
        ioExecutor.shutdownNow();
        cache.clear();
        locks.clear();
    }

    // ============================================================
    // Batch Points Reader
    // ============================================================

    public int getPoints(UUID id) {
        PlayerData d = cache.get(id);
        if (d != null) return d.totalPoints();

        File f = fileOf(id);
        if (!f.exists()) return 0;

        try (DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(f)))) {
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
            return total;
        } catch (Throwable ignored) { return 0; }
    }

    public Map<UUID, Integer> getAllPoints() {
        Map<UUID, Integer> map = new HashMap<>();

        for (Map.Entry<UUID, PlayerData> e : cache.entrySet())
            map.put(e.getKey(), e.getValue().totalPoints());

        long now = System.currentTimeMillis();
        if (cachedFiles == null || now - lastScanTime > 10_000L) {
            cachedFiles = folder.listFiles((dir, name) -> name.endsWith(".dat"));
            lastScanTime = now;
        }

        if (cachedFiles == null) return map;

        for (File f : cachedFiles) {
            try {
                UUID id = UUID.fromString(f.getName().replace(".dat", ""));
                if (map.containsKey(id)) continue;
                try (DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(f)))) {
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
                }
            } catch (Throwable ignored) {}
        }
        return map;
    }

    public void preloadAll() {
        File[] files = folder.listFiles((dir, name) -> name.endsWith(".dat"));
        if (files == null) return;

        for (File f : files) {
            try {
                UUID id = UUID.fromString(f.getName().replace(".dat", ""));
                if (cache.containsKey(id)) continue;
                cache.put(id, load(id, "unknown"));
            } catch (Throwable ignored) {}
        }
    }
}
