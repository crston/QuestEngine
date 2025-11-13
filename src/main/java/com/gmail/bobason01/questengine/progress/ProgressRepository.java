package com.gmail.bobason01.questengine.progress;

import com.gmail.bobason01.questengine.QuestEnginePlugin;
import com.gmail.bobason01.questengine.storage.*;
import com.gmail.bobason01.questengine.storage.sql.MySQLStorage;
import com.gmail.bobason01.questengine.storage.sql.SQLiteStorage;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * ProgressRepository
 * 고성능 비동기 안전 퀘스트 진행 저장소
 * 모든 questId 는 lower-case 로 정규화하여 unknown quest 문제를 방지
 */
public final class ProgressRepository {

    private final QuestEnginePlugin plugin;
    private final StorageProvider storage;

    private final ConcurrentMap<UUID, PlayerData> cache = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Object> locks = new ConcurrentHashMap<>();
    private final BlockingQueue<UUID> saveQueue = new LinkedBlockingQueue<>();
    private final Map<UUID, Integer> points = new ConcurrentHashMap<>();

    private final ScheduledExecutorService ioExecutor =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "QuestEngine-IO");
                t.setDaemon(true);
                return t;
            });

    public ProgressRepository(QuestEnginePlugin plugin) {
        this.plugin = plugin;
        this.storage = buildProvider(plugin);

        ioExecutor.scheduleAtFixedRate(this::flushQueue, 3, 3, TimeUnit.SECONDS);

        CompletableFuture.runAsync(() -> {
            try {
                Map<UUID, Integer> all = storage.loadAllPointsApprox();
                if (all != null && !all.isEmpty()) {
                    points.putAll(all);
                    plugin.getLogger().info("[QuestEngine] Loaded " + points.size() + " leaderboard entries.");
                }
            } catch (Throwable t) {
                plugin.getLogger().warning("[QuestEngine] Failed to preload points: " + t.getMessage());
            }
        });
    }

    private StorageProvider buildProvider(QuestEnginePlugin plugin) {
        String mode = plugin.getConfig().getString("storage.mode", "file").toLowerCase(Locale.ROOT);
        switch (mode) {
            case "yaml": return new YamlStorage(plugin);
            case "sqlite": return new SQLiteStorage(plugin);
            case "mysql": return new MySQLStorage(plugin);
            default: return new FileStorage(plugin);
        }
    }

    private String norm(String qid) {
        return qid == null ? null : qid.toLowerCase(Locale.ROOT);
    }

    private Object lockFor(UUID id, String qid) {
        return locks.computeIfAbsent(id.toString() + "|" + norm(qid), k -> new Object());
    }

    public PlayerData of(UUID id, String name) {
        PlayerData data = cache.computeIfAbsent(id, k -> storage.load(id, name));
        if (data == null) {
            data = new PlayerData(id, name);
            cache.put(id, data);
        }
        return data;
    }

    private void enqueueSave(UUID id) {
        if (id != null) saveQueue.offer(id);
    }

    private void flushQueue() {
        Set<UUID> set = new HashSet<>();
        saveQueue.drainTo(set);
        for (UUID id : set) {
            try {
                saveNow(id);
            } catch (Throwable ignored) {}
        }
    }

    private void saveNow(UUID id) {
        PlayerData d = cache.get(id);
        if (d != null) storage.save(d);
    }

    public boolean isActive(UUID id, String name, String qid) {
        return of(id, name).isActive(norm(qid));
    }

    public boolean isCompleted(UUID id, String name, String qid) {
        return of(id, name).isCompleted(norm(qid));
    }

    public void start(UUID id, String name, String qid) {
        qid = norm(qid);
        synchronized (lockFor(id, qid)) {
            of(id, name).start(qid);
        }
        enqueueSave(id);
    }

    public void cancel(UUID id, String name, String qid) {
        qid = norm(qid);
        synchronized (lockFor(id, qid)) {
            of(id, name).cancel(qid);
        }
        enqueueSave(id);
    }

    public void complete(UUID id, String name, String qid, int pts) {
        qid = norm(qid);
        synchronized (lockFor(id, qid)) {
            of(id, name).complete(qid, pts);
        }
        enqueueSave(id);
        setPoints(id, of(id, name).totalPoints());
    }

    public int addProgress(UUID id, String name, String qid, int amt) {
        qid = norm(qid);
        synchronized (lockFor(id, qid)) {
            int v = of(id, name).add(qid, amt);
            enqueueSave(id);
            setPoints(id, of(id, name).totalPoints());
            return v;
        }
    }

    public int value(UUID id, String name, String qid) {
        return of(id, name).valueOf(norm(qid));
    }

    public List<String> activeIds(UUID id, String name) {
        return of(id, name).activeIds();
    }

    public List<String> activeOf(UUID uid, String name) {
        return activeQuestIds(uid, name);
    }

    public List<String> completedIds(UUID id, String name) {
        return of(id, name).completedIds();
    }

    public void cancelAll(UUID id, String name) {
        PlayerData data = of(id, name);
        synchronized (data) {
            data.cancelAll();
        }
        enqueueSave(id);
        setPoints(id, data.totalPoints());
    }

    public void reset(UUID id) {
        cache.remove(id);
        storage.reset(id);
        points.remove(id);
    }

    public void reset(UUID id, String name, String qid) {
        qid = norm(qid);
        synchronized (lockFor(id, qid)) {
            of(id, name).cancel(qid);
        }
        storage.resetQuest(id, qid);
        enqueueSave(id);
        setPoints(id, of(id, name).totalPoints());
    }

    public void preload(UUID id) {
        cache.computeIfAbsent(id, k -> storage.load(id, "unknown"));
    }

    public void preloadAll() {
        storage.preloadAll();
    }

    public PlayerData get(UUID id) {
        return cache.computeIfAbsent(id, k -> storage.load(k, "unknown"));
    }

    public void save(PlayerData d) {
        if (d == null) return;
        storage.save(d);
        cache.put(d.getId(), d);
    }

    public void close() {
        flushQueue();
        ioExecutor.shutdownNow();
        cache.clear();
        locks.clear();
        storage.close();
        points.clear();
    }

    public void setPoints(UUID id, int val) {
        if (id == null) return;
        points.put(id, Math.max(0, val));
    }

    public int getPoints(UUID id) {
        if (id == null) return 0;
        PlayerData data = cache.get(id);
        return data != null ? data.totalPoints() : 0;
    }

    /* ==================================================================
       조회 헬퍼 메서드 세트
       ================================================================== */

    public int getPoints(UUID id, String name) {
        return getPoints(id);
    }

    public int cacheSize() {
        return cache.size();
    }

    public Map<UUID, Integer> getAllPoints() {
        Map<UUID, Integer> map = storage.loadAllPointsApprox();
        for (Map.Entry<UUID, PlayerData> e : cache.entrySet()) {
            map.put(e.getKey(), e.getValue().totalPoints());
        }
        return map;
    }

    public int activeCount(UUID id, String name) {
        PlayerData d = get(id);
        return d == null ? 0 : d.activeIds().size();
    }

    public int completedCount(UUID id, String name) {
        PlayerData d = get(id);
        return d == null ? 0 : d.completedIds().size();
    }

    public int totalPoints(UUID id, String name) {
        PlayerData d = get(id);
        return d == null ? 0 : d.totalPoints();
    }

    public String firstActiveId(UUID id, String name) {
        PlayerData d = get(id);
        if (d == null) return null;
        List<String> list = d.activeIds();
        return list.isEmpty() ? null : list.get(0);
    }

    public List<String> activeQuestIds(UUID id, String name) {
        PlayerData d = get(id);
        return d == null ? Collections.emptyList() : d.activeIds();
    }

    public List<String> completedQuestIds(UUID id, String name) {
        PlayerData d = get(id);
        return d == null ? Collections.emptyList() : new ArrayList<>(d.completedIds());
    }

    public List<Map.Entry<UUID, Integer>> top(int limit) {
        Map<UUID, Integer> map = getAllPoints();
        return map.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                .limit(limit)
                .collect(Collectors.toList());
    }
}
