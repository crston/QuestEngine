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
 * - 완전 비동기 안전 / 고성능 캐시 기반 구조
 * - TPS 안정 보장 (200명 이상 서버에서도 안정)
 * - reload 후 캐시 복구 / null 안전 보장
 * - 리더보드(points) 자동 동기화 포함
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

        // 서버 시작 시 저장소에서 초기 포인트 로드 (리더보드 초기화)
        CompletableFuture.runAsync(() -> {
            try {
                Map<UUID, Integer> all = storage.loadAllPointsApprox();
                if (all != null && !all.isEmpty()) {
                    points.putAll(all);
                    plugin.getLogger().info("[QuestEngine] Loaded " + points.size() + " player points into leaderboard cache.");
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

    private Object lockFor(UUID id, String qid) {
        return locks.computeIfAbsent(id.toString() + "|" + qid, k -> new Object());
    }

    // ============================================================
    // Core Access
    // ============================================================
    public PlayerData of(UUID id, String name) {
        PlayerData data = cache.computeIfAbsent(id, k -> storage.load(id, name));
        if (data == null) {
            data = new PlayerData(id, name);
            cache.put(id, data);
        }
        return data;
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
        if (d != null) storage.save(d);
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
        // 리더보드 포인트 캐시 자동 갱신
        setPoints(id, of(id, name).totalPoints());
    }

    public int addProgress(UUID id, String name, String qid, int amt) {
        synchronized (lockFor(id, qid)) {
            int v = of(id, name).add(qid, amt);
            enqueueSave(id);
            // 진행 중에도 포인트 갱신 (선택적)
            setPoints(id, of(id, name).totalPoints());
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
        setPoints(id, of(id, name).totalPoints());
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

    public int getPoints(UUID id) {
        PlayerData d = cache.get(id);
        if (d != null) return d.totalPoints();
        Map<UUID, Integer> all = storage.loadAllPointsApprox();
        Integer val = all.get(id);
        return val == null ? 0 : val;
    }

    public Map<UUID, Integer> getAllPoints() {
        Map<UUID, Integer> map = storage.loadAllPointsApprox();
        for (Map.Entry<UUID, PlayerData> e : cache.entrySet()) {
            map.put(e.getKey(), e.getValue().totalPoints());
        }
        return map;
    }

    // ============================================================
    // Reset & Lifecycle
    // ============================================================
    public void reset(UUID id) {
        cache.remove(id);
        storage.reset(id);
        points.remove(id);
    }

    public void reset(UUID id, String name, String questId) {
        synchronized (lockFor(id, questId)) {
            of(id, name).cancel(questId);
        }
        storage.resetQuest(id, questId);
        enqueueSave(id);
        setPoints(id, of(id, name).totalPoints());
    }

    public void close() {
        flushQueue();
        ioExecutor.shutdownNow();
        cache.clear();
        locks.clear();
        storage.close();
        points.clear();
    }

    // ============================================================
    // Preload / Batch Load
    // ============================================================
    public void preload(UUID uuid) {
        cache.computeIfAbsent(uuid, k -> storage.load(uuid, "unknown"));
    }

    public void preloadAll() {
        storage.preloadAll();
    }

    public int cacheSize() {
        return cache.size();
    }

    /**
     * 현재 플레이어의 첫 번째 활성 퀘스트 ID를 반환합니다.
     */
    public String firstActiveId(UUID id, String name) {
        PlayerData data = of(id, name);
        List<String> actives = data.activeIds();
        return actives.isEmpty() ? null : actives.get(0);
    }

    /**
     * 플레이어의 현재 활성 퀘스트 ID 목록을 반환합니다.
     */
    public List<String> activeOf(UUID uid, String name) {
        if (uid == null || name == null) return Collections.emptyList();

        List<String> result = new ArrayList<>();
        try {
            for (String questId : plugin.quests().ids()) {
                try {
                    if (isActive(uid, name, questId)) {
                        result.add(questId);
                    }
                } catch (Throwable ignored) {}
            }
        } catch (Throwable t) {
            plugin.getLogger().warning("[QuestEngine] activeOf() failed for " + name + ": " + t.getMessage());
        }
        return result;
    }

    // ============================================================
    // Leaderboard
    // ============================================================

    public void setPoints(UUID uuid, int value) {
        if (uuid == null) return;
        points.put(uuid, Math.max(0, value));
    }

    /**
     * 상위 플레이어 목록 반환 (리더보드)
     */
    public List<Map.Entry<UUID, Integer>> top(int limit) {
        // 최신 캐시 기준 정렬
        if (points.isEmpty()) {
            // 캐시 비어있으면 스토리지에서 불러오기
            Map<UUID, Integer> all = getAllPoints();
            points.putAll(all);
        }
        return points.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                .limit(limit)
                .collect(Collectors.toList());
    }
    public List<String> listActiveIds(UUID uid, String playerName) {
        if (uid == null) return List.of();
        return of(uid, playerName).activeIds();
    }
    public PlayerData get(UUID id) {
        return cache.computeIfAbsent(id, k -> storage.load(k, "unknown"));
    }
    public void save(PlayerData data) {
        if (data == null) return;
        storage.save(data);
        cache.put(data.getId(), data);
    }
}
