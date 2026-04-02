package com.gmail.bobason01.questengine.progress;

import com.gmail.bobason01.questengine.quest.QuestDef;
import java.io.Serializable;
import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public final class PlayerData implements Serializable {

    private static final long serialVersionUID = 5L;

    private final UUID id;
    private volatile String name;
    private String language = "en"; // Default language

    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final Map<String, Node> map = new HashMap<>(16);
    private final LinkedHashSet<String> activeOrder = new LinkedHashSet<>(4);

    private static final class Node implements Serializable {
        private static final long serialVersionUID = 1L;
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

    private String norm(String id) {
        return id == null ? null : id.toLowerCase(Locale.ROOT);
    }

    private static int totalSlots(int repeat) {
        if (repeat < 0) return -1; // Infinite
        if (repeat == 0) return 1; // Once
        return repeat;
    }

    // --- Language Management ---

    public String getLanguage() {
        lock.readLock().lock();
        try {
            return (language == null || language.isEmpty()) ? "en" : language;
        } finally {
            lock.readLock().unlock();
        }
    }

    public void setLanguage(String lang) {
        lock.writeLock().lock();
        try {
            this.language = (lang == null) ? "en" : lang.toLowerCase(Locale.ROOT);
        } finally {
            lock.writeLock().unlock();
        }
    }

    // --- Metadata ---

    public UUID getId() { return id; }
    public String getName() { return name; }

    // --- Read Operations (Resolving Symbols) ---

    public boolean isActive(String qid) {
        lock.readLock().lock();
        try { Node n = map.get(norm(qid)); return n != null && n.active; } finally { lock.readLock().unlock(); }
    }

    public boolean isCompleted(String qid) {
        lock.readLock().lock();
        try { Node n = map.get(norm(qid)); return n != null && n.completed; } finally { lock.readLock().unlock(); }
    }

    public int valueOf(String qid) {
        lock.readLock().lock();
        try { Node n = map.get(norm(qid)); return n == null ? 0 : n.value; } finally { lock.readLock().unlock(); }
    }

    public int pointsOf(String qid) {
        lock.readLock().lock();
        try { Node n = map.get(norm(qid)); return n == null ? 0 : n.points; } finally { lock.readLock().unlock(); }
    }

    public int getRepeatCount(String qid) {
        lock.readLock().lock();
        try { Node n = map.get(norm(qid)); return n == null ? 0 : n.completedCount; } finally { lock.readLock().unlock(); }
    }

    /** * Overloaded canStart for QuestDef object
     */
    public boolean canStart(String qid, QuestDef def) {
        return canStart(qid, def.repeat);
    }

    /** * Overloaded canStart for raw repeat integer
     */
    public boolean canStart(String qid, int repeatConfig) {
        String key = norm(qid);
        lock.readLock().lock();
        try {
            Node n = map.get(key);
            if (repeatConfig < 0) return true;
            int max = totalSlots(repeatConfig);
            if (n == null) return true;
            if (n.completed) return false;
            return n.completedCount < max;
        } finally {
            lock.readLock().unlock();
        }
    }

    public List<String> activeIds() {
        lock.readLock().lock();
        try { return new ArrayList<>(activeOrder); } finally { lock.readLock().unlock(); }
    }

    public List<String> completedIds() {
        lock.readLock().lock();
        try {
            List<String> out = new ArrayList<>();
            for (Map.Entry<String, Node> e : map.entrySet()) if (e.getValue().completed) out.add(e.getKey());
            return out;
        } finally { lock.readLock().unlock(); }
    }

    public int totalPoints() {
        lock.readLock().lock();
        try {
            int sum = 0;
            for (Node n : map.values()) if (n.completed) sum += n.points;
            return sum;
        } finally { lock.readLock().unlock(); }
    }

    // --- Write Operations (Resolving Symbols) ---

    public void start(String qid) {
        String key = norm(qid);
        lock.writeLock().lock();
        try {
            Node n = map.computeIfAbsent(key, k -> new Node());
            n.active = true;
            activeOrder.add(key);
        } finally { lock.writeLock().unlock(); }
    }

    public void cancel(String qid) {
        String key = norm(qid);
        lock.writeLock().lock();
        try {
            Node n = map.get(key);
            if (n != null) { n.active = false; n.value = 0; }
            activeOrder.remove(key);
        } finally { lock.writeLock().unlock(); }
    }

    public void complete(String qid, int pts) {
        complete(qid, pts, 0);
    }

    public void complete(String qid, int pts, int repeatConfig) {
        String key = norm(qid);
        lock.writeLock().lock();
        try {
            Node n = map.computeIfAbsent(key, k -> new Node());
            n.active = false;
            n.value = 0;
            if (pts > n.points) n.points = pts;
            activeOrder.remove(key);
            n.completedCount++;
            int max = totalSlots(repeatConfig);
            if (repeatConfig >= 0 && n.completedCount >= max) n.completed = true;
        } finally { lock.writeLock().unlock(); }
    }

    public int add(String qid, int amt) {
        lock.writeLock().lock();
        try {
            Node n = map.computeIfAbsent(norm(qid), k -> new Node());
            n.value = Math.max(0, n.value + amt);
            return n.value;
        } finally { lock.writeLock().unlock(); }
    }

    public void resetQuest(String qid) {
        String key = norm(qid);
        lock.writeLock().lock();
        try {
            Node n = map.get(key);
            if (n == null) return;
            n.active = false; n.completed = false; n.value = 0;
            n.points = 0; n.completedCount = 0;
            activeOrder.remove(key);
        } finally { lock.writeLock().unlock(); }
    }

    public void setRepeatCount(String qid, int count) {
        lock.writeLock().lock();
        try {
            Node n = map.computeIfAbsent(norm(qid), k -> new Node());
            n.completedCount = Math.max(0, count);
        } finally { lock.writeLock().unlock(); }
    }

    public void cancelAll() {
        lock.writeLock().lock();
        try {
            for (Node n : map.values()) { n.active = false; n.value = 0; }
            activeOrder.clear();
        } finally { lock.writeLock().unlock(); }
    }

    // --- Legacy Bridge ---
    public Set<String> getActiveQuests() { return new LinkedHashSet<>(activeIds()); }
    public Set<String> getCompletedQuests() { return new HashSet<>(completedIds()); }
}