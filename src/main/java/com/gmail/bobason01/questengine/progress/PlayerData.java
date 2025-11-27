package com.gmail.bobason01.questengine.progress;

import java.io.Serializable;
import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * PlayerData (Thread-Safe Optimized)
 * - ReentrantReadWriteLock 도입으로 완벽한 동시성 제어
 * - ConcurrentHashMap 제거 (락으로 보호되는 일반 HashMap이 더 빠름)
 * - 불필요한 객체 생성 최소화
 */
public final class PlayerData implements Serializable {

    private static final long serialVersionUID = 2L; // 구조 변경 시 버전 업

    private final UUID id;
    private volatile String name;

    // 데이터 보호를 위한 RW 락
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    // 락으로 보호되므로 일반 HashMap 사용 (메모리 절약 + 속도 향상)
    private final Map<String, Node> map = new HashMap<>(16);
    private final LinkedHashSet<String> activeOrder = new LinkedHashSet<>(4);

    private static final class Node implements Serializable {
        boolean active;
        boolean completed;
        int value;
        int points;
        int completedCount;
    }

    public PlayerData(UUID id, String name) {
        this.id = id;
        this.name = (name == null ? "unknown" : name);
    }

    private static String norm(String id) {
        return id == null ? null : id.toLowerCase(Locale.ROOT);
    }

    private static int totalSlots(int repeat) {
        if (repeat < 0) return -1; // 무한
        if (repeat == 0) return 1; // 1회
        return repeat;
    }

    public UUID getId() { return id; }
    public String getName() { return name; }

    public void rename(String newName) {
        if (newName != null && !newName.isEmpty()) this.name = newName;
    }

    // --- Read Operations (Shared Lock) ---

    public boolean isActive(String questId) {
        lock.readLock().lock();
        try {
            Node n = map.get(norm(questId));
            return n != null && n.active;
        } finally {
            lock.readLock().unlock();
        }
    }

    public boolean isCompleted(String questId) {
        lock.readLock().lock();
        try {
            Node n = map.get(norm(questId));
            return n != null && n.completed;
        } finally {
            lock.readLock().unlock();
        }
    }

    public boolean canStart(String questId, int repeatConfig) {
        String key = norm(questId);
        if (key == null) return false;

        lock.readLock().lock();
        try {
            Node n = map.get(key);
            if (repeatConfig < 0) return true; // 무한 반복

            int slots = totalSlots(repeatConfig);
            if (n == null) return true; // 기록 없음 -> 가능
            if (n.completed) return false; // 이미 졸업함 -> 불가능
            return n.completedCount < slots; // 횟수 남음 -> 가능
        } finally {
            lock.readLock().unlock();
        }
    }

    public int valueOf(String questId) {
        lock.readLock().lock();
        try {
            Node n = map.get(norm(questId));
            return n == null ? 0 : n.value;
        } finally {
            lock.readLock().unlock();
        }
    }

    public int pointsOf(String questId) {
        lock.readLock().lock();
        try {
            Node n = map.get(norm(questId));
            return n == null ? 0 : n.points;
        } finally {
            lock.readLock().unlock();
        }
    }

    public int completedCountOf(String questId) {
        lock.readLock().lock();
        try {
            Node n = map.get(norm(questId));
            return n == null ? 0 : n.completedCount;
        } finally {
            lock.readLock().unlock();
        }
    }

    public List<String> activeIds() {
        lock.readLock().lock();
        try {
            return activeOrder.isEmpty() ? Collections.emptyList() : new ArrayList<>(activeOrder);
        } finally {
            lock.readLock().unlock();
        }
    }

    public List<String> completedIds() {
        lock.readLock().lock();
        try {
            if (map.isEmpty()) return Collections.emptyList();
            List<String> out = new ArrayList<>();
            for (Map.Entry<String, Node> e : map.entrySet()) {
                if (e.getValue().completed) out.add(e.getKey());
            }
            return out;
        } finally {
            lock.readLock().unlock();
        }
    }

    public int totalPoints() {
        lock.readLock().lock();
        try {
            int sum = 0;
            for (Node n : map.values()) {
                if (n.completed) sum += n.points;
            }
            return sum;
        } finally {
            lock.readLock().unlock();
        }
    }

    // --- Write Operations (Exclusive Lock) ---

    public void start(String questId) {
        String key = norm(questId);
        if (key == null) return;

        lock.writeLock().lock();
        try {
            Node n = map.computeIfAbsent(key, k -> new Node());
            n.active = true;
            activeOrder.add(key);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void cancel(String questId) {
        String key = norm(questId);
        if (key == null) return;

        lock.writeLock().lock();
        try {
            Node n = map.get(key);
            if (n == null) return;
            n.active = false;
            n.value = 0;
            activeOrder.remove(key);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void resetQuest(String questId) {
        String key = norm(questId);
        if (key == null) return;

        lock.writeLock().lock();
        try {
            Node n = map.get(key);
            if (n == null) return;
            n.active = false;
            n.completed = false;
            n.value = 0;
            n.completedCount = 0;
            n.points = 0;
            activeOrder.remove(key);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void complete(String questId, int points, int repeatConfig) {
        String key = norm(questId);
        if (key == null) return;

        lock.writeLock().lock();
        try {
            Node n = map.computeIfAbsent(key, k -> new Node());
            n.active = false;
            n.value = 0;
            if (points > n.points) n.points = points;

            activeOrder.remove(key);

            if (repeatConfig >= 0) {
                int slots = totalSlots(repeatConfig);
                if (n.completedCount < Integer.MAX_VALUE) n.completedCount++;
                if (n.completedCount >= slots) n.completed = true;
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    public int add(String questId, int amount) {
        String key = norm(questId);
        if (key == null) return 0;

        lock.writeLock().lock();
        try {
            if (amount == 0) {
                Node exist = map.get(key);
                return exist == null ? 0 : exist.value;
            }
            Node n = map.computeIfAbsent(key, k -> new Node());
            int v = Math.max(0, n.value + amount);
            n.value = v;
            return v;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void setRepeatCount(String questId, int count) {
        String key = norm(questId);
        if (key == null) return;

        lock.writeLock().lock();
        try {
            Node n = map.computeIfAbsent(key, k -> new Node());
            n.completedCount = Math.max(0, count);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void cancelAll() {
        lock.writeLock().lock();
        try {
            for (Node n : map.values()) {
                n.active = false;
                n.value = 0;
            }
            activeOrder.clear();
        } finally {
            lock.writeLock().unlock();
        }
    }

    // --- Legacy Support ---
    public void complete(String questId, int points) { complete(questId, points, 0); }
    public int getRepeatCount(String questId) { return completedCountOf(questId); }

    public Set<String> getCompletedQuests() {
        lock.readLock().lock();
        try { return new HashSet<>(completedIds()); } finally { lock.readLock().unlock(); }
    }

    public Set<String> getActiveQuests() {
        lock.readLock().lock();
        try { return new LinkedHashSet<>(activeOrder); } finally { lock.readLock().unlock(); }
    }
}