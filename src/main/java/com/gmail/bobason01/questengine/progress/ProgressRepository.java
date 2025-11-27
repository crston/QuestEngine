package com.gmail.bobason01.questengine.progress;

import com.gmail.bobason01.questengine.QuestEnginePlugin;
import com.gmail.bobason01.questengine.quest.QuestDef;
import com.gmail.bobason01.questengine.storage.FileStorage;
import com.gmail.bobason01.questengine.storage.StorageProvider;
import com.gmail.bobason01.questengine.storage.YamlStorage;
import com.gmail.bobason01.questengine.storage.sql.MySQLStorage;
import com.gmail.bobason01.questengine.storage.sql.SQLiteStorage;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

public final class ProgressRepository {

    private final QuestEnginePlugin plugin;
    private final StorageProvider storage;

    // PlayerData는 내부적으로 Thread-Safe하므로 ConcurrentMap만으로 충분
    private final ConcurrentMap<UUID, PlayerData> cache = new ConcurrentHashMap<>();

    // 저장 대기열 (중복 방지 위해 Set으로 관리하고 Queue로 순서 보장할 수도 있으나, 여기선 최신 상태만 저장하면 되므로 Set 활용)
    private final Set<UUID> dirtyParams = ConcurrentHashMap.newKeySet();
    private final BlockingQueue<UUID> saveQueue = new LinkedBlockingQueue<>();

    // 랭킹용 포인트 캐시 (실시간 업데이트)
    private final Map<UUID, Integer> pointsCache = new ConcurrentHashMap<>();

    private final ScheduledExecutorService ioExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "QuestEngine-IO");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY); // IO는 낮은 우선순위
        return t;
    });

    public ProgressRepository(QuestEnginePlugin plugin) {
        this.plugin = plugin;
        this.storage = buildProvider(plugin);

        // 3초마다 저장 큐 처리 (Batch Processing)
        ioExecutor.scheduleAtFixedRate(this::flush, 3, 3, TimeUnit.SECONDS);

        // 초기 랭킹 데이터 비동기 로드
        CompletableFuture.runAsync(() -> {
            try {
                Map<UUID, Integer> loaded = storage.loadAllPointsApprox();
                if (loaded != null) {
                    pointsCache.putAll(loaded);
                    plugin.getLogger().info("[QuestEngine] Loaded leaderboard cache: " + loaded.size() + " entries.");
                }
            } catch (Throwable t) {
                plugin.getLogger().warning("[QuestEngine] Leaderboard load failed: " + t.getMessage());
            }
        }, ioExecutor);
    }

    private StorageProvider buildProvider(QuestEnginePlugin plugin) {
        String mode = plugin.getConfig().getString("storage.mode", "file").toLowerCase(Locale.ROOT);
        return switch (mode) {
            case "yaml" -> new YamlStorage(plugin);
            case "sqlite" -> new SQLiteStorage(plugin);
            case "mysql" -> new MySQLStorage(plugin);
            default -> new FileStorage(plugin);
        };
    }

    private String norm(String qid) {
        return qid == null ? null : qid.toLowerCase(Locale.ROOT);
    }

    // --- Core Accessor ---

    public PlayerData of(UUID id, String name) {
        // 1. 캐시 조회 (Fast Path)
        PlayerData data = cache.get(id);
        if (data != null) return data;

        // 2. 캐시 미스 -> 로드 (Blocking IO, 메인 스레드 주의)
        // 비동기 로딩을 하려면 CompletableFuture를 반환해야 하지만,
        // 기존 API 호환성을 위해 여기서는 동기 로딩을 유지하되 computeIfAbsent로 중복 로딩 방지
        return cache.computeIfAbsent(id, k -> {
            PlayerData loaded = storage.load(k, name);
            return loaded != null ? loaded : new PlayerData(k, name);
        });
    }

    // 편의용 오버로딩 (name이 없을 때)
    public PlayerData get(UUID id) {
        return of(id, "unknown");
    }

    // --- Save Logic ---

    private void markDirty(UUID id) {
        if (id != null && dirtyParams.add(id)) {
            saveQueue.offer(id);
        }
    }

    private void flush() {
        if (saveQueue.isEmpty()) return;

        // 현재 큐에 있는 것들만 스냅샷 떠서 저장
        Set<UUID> processing = new HashSet<>();
        saveQueue.drainTo(processing);

        for (UUID id : processing) {
            dirtyParams.remove(id); // 처리 시작 전 제거 (저장 중 변경되면 다시 큐에 들어감)
            try {
                PlayerData data = cache.get(id);
                if (data != null) storage.save(data);
            } catch (Throwable t) {
                plugin.getLogger().warning("Failed to save player data: " + id);
            }
        }
    }

    // --- Operations (Thread-Safe via PlayerData Lock) ---

    public boolean isActive(UUID id, String name, String qid) {
        return of(id, name).isActive(norm(qid));
    }

    public boolean isCompleted(UUID id, String name, String qid) {
        return of(id, name).isCompleted(norm(qid));
    }

    public boolean canStart(UUID id, String name, QuestDef def) {
        if (def == null) return false;
        return of(id, name).canStart(def.id, def.repeat);
    }

    public void start(UUID id, String name, String qid) {
        of(id, name).start(norm(qid));
        markDirty(id);
    }

    public void start(UUID id, String name, QuestDef def) {
        if (def != null) start(id, name, def.id);
    }

    public void cancel(UUID id, String name, String qid) {
        of(id, name).cancel(norm(qid));
        markDirty(id);
    }

    public void complete(UUID id, String name, String qid, int pts) {
        PlayerData d = of(id, name);
        d.complete(norm(qid), pts);
        updatePoints(id, d.totalPoints());
        markDirty(id);
    }

    public void complete(UUID id, String name, QuestDef def) {
        if (def == null) return;
        PlayerData d = of(id, name);
        d.complete(norm(def.id), def.points, def.repeat);
        updatePoints(id, d.totalPoints());
        markDirty(id);
    }

    public int addProgress(UUID id, String name, String qid, int amt) {
        PlayerData d = of(id, name);
        int val = d.add(norm(qid), amt);
        updatePoints(id, d.totalPoints());
        markDirty(id);
        return val;
    }

    public int value(UUID id, String name, String qid) {
        return of(id, name).valueOf(norm(qid));
    }

    public void reset(UUID id, String name, String qid) {
        String nQid = norm(qid);
        PlayerData d = of(id, name);
        d.resetQuest(nQid);

        // Storage 레벨에서도 삭제가 필요하다면 호출 (보통 save로 덮어쓰기 되므로 불필요할 수 있음)
        storage.resetQuest(id, nQid);

        updatePoints(id, d.totalPoints());
        markDirty(id);
    }

    public void reset(UUID id) {
        cache.remove(id);
        pointsCache.remove(id);
        storage.reset(id);
    }

    public void cancelAll(UUID id, String name) {
        PlayerData d = of(id, name);
        d.cancelAll();
        updatePoints(id, d.totalPoints());
        markDirty(id);
    }

    // --- Points & Leaderboard ---

    private void updatePoints(UUID id, int points) {
        pointsCache.put(id, points);
    }

    public int getPoints(UUID id) {
        return pointsCache.getOrDefault(id, 0);
    }

    public Map<UUID, Integer> getAllPoints() {
        return Collections.unmodifiableMap(pointsCache);
    }

    public List<Map.Entry<UUID, Integer>> top(int limit) {
        // 스트림 정렬은 무거우므로 PriorityQueue 등을 고려할 수 있으나,
        // 빈도가 낮다면 스트림도 OK. 여기서는 간단히 스트림 사용.
        return pointsCache.entrySet().stream()
                .sorted(Map.Entry.<UUID, Integer>comparingByValue().reversed()) // 내림차순
                .limit(limit)
                .collect(Collectors.toList());
    }

    // --- System Methods ---

    public void save(PlayerData d) {
        if (d != null) {
            cache.put(d.getId(), d);
            markDirty(d.getId());
        }
    }

    public void close() {
        ioExecutor.shutdownNow(); // 강제 종료 전
        flush(); // 남은 데이터 저장 시도
        try {
            // 잠시 대기하여 저장이 완료되도록 유도
            ioExecutor.awaitTermination(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        storage.close();
        cache.clear();
        pointsCache.clear();
    }

    public int cacheSize() { return cache.size(); }

    // --- Wrapper Accessors ---

    public List<String> activeQuestIds(UUID id, String name) {
        return of(id, name).activeIds();
    }

    public List<String> activeOf(UUID id, String name) {
        return activeQuestIds(id, name);
    }

    public List<String> completedQuestIds(UUID id, String name) {
        return of(id, name).completedIds();
    }

    public int activeCount(UUID id, String name) {
        return of(id, name).activeIds().size();
    }

    public int completedCount(UUID id, String name) {
        return of(id, name).completedIds().size();
    }

    public int totalPoints(UUID id, String name) {
        return of(id, name).totalPoints();
    }
}