package com.gmail.bobason01.questengine.progress;

import java.io.Serializable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * PlayerData
 * 퀘스트 진행 상태를 보관하는 데이터 클래스
 * 모든 퀘스트 아이디는 소문자로 정규화한다
 */
public final class PlayerData implements Serializable {

    private static final long serialVersionUID = 1L;

    private final UUID id;
    private String name;

    // questId -> Node
    private final Map<String, Node> map = new ConcurrentHashMap<>(32, 0.75f, 2);

    // 활성 퀘스트 순서 정보
    private final LinkedHashSet<String> activeOrder = new LinkedHashSet<>(8);

    private static final class Node implements Serializable {
        boolean active;
        boolean completed;
        int value;
        int points;
    }

    public PlayerData(UUID id, String name) {
        this.id = id;
        this.name = name == null ? "unknown" : name;
    }

    private static String norm(String id) {
        return id == null ? null : id.toLowerCase(Locale.ROOT);
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void rename(String newName) {
        if (newName != null && !newName.isEmpty()) {
            this.name = newName;
        }
    }

    public boolean isActive(String questId) {
        questId = norm(questId);
        if (questId == null) return false;
        Node n = map.get(questId);
        return n != null && n.active;
    }

    public boolean isCompleted(String questId) {
        questId = norm(questId);
        if (questId == null) return false;
        Node n = map.get(questId);
        return n != null && n.completed;
    }

    public void start(String questId) {
        questId = norm(questId);
        if (questId == null) return;
        Node n = map.computeIfAbsent(questId, k -> new Node());
        n.active = true;
        activeOrder.add(questId);
    }

    public void cancel(String questId) {
        questId = norm(questId);
        if (questId == null) return;
        Node n = map.get(questId);
        if (n == null) return;
        n.active = false;
        n.value = 0;
        activeOrder.remove(questId);
    }

    public void complete(String questId, int points) {
        questId = norm(questId);
        if (questId == null) return;
        Node n = map.computeIfAbsent(questId, k -> new Node());
        n.active = false;
        n.completed = true;
        n.points = Math.max(n.points, points);
        activeOrder.remove(questId);
    }

    public int add(String questId, int amount) {
        questId = norm(questId);
        if (questId == null) return 0;
        if (amount == 0) {
            Node exist = map.get(questId);
            return exist == null ? 0 : exist.value;
        }
        Node n = map.computeIfAbsent(questId, k -> new Node());
        int v = n.value + amount;
        if (v < 0) v = 0;
        n.value = v;
        return v;
    }

    public int valueOf(String questId) {
        questId = norm(questId);
        if (questId == null) return 0;
        Node n = map.get(questId);
        return n == null ? 0 : n.value;
    }

    public int pointsOf(String questId) {
        questId = norm(questId);
        if (questId == null) return 0;
        Node n = map.get(questId);
        return n == null ? 0 : n.points;
    }

    public List<String> activeIds() {
        if (activeOrder.isEmpty()) return Collections.emptyList();
        return new ArrayList<>(activeOrder);
    }

    public List<String> completedIds() {
        if (map.isEmpty()) return Collections.emptyList();
        List<String> out = new ArrayList<>();
        for (Map.Entry<String, Node> e : map.entrySet()) {
            if (e.getValue().completed) {
                out.add(e.getKey());
            }
        }
        return out;
    }

    public void cancelAll() {
        for (Node n : map.values()) {
            n.active = false;
            n.value = 0;
        }
        activeOrder.clear();
    }

    public int totalPoints() {
        int sum = 0;
        for (Node n : map.values()) {
            if (n.completed) {
                sum += n.points;
            }
        }
        return sum;
    }

    // 예전 코드 호환용 메서드들

    public Set<String> getCompletedQuests() {
        return new HashSet<>(completedIds());
    }

    public Set<String> getActiveQuests() {
        return new LinkedHashSet<>(activeOrder);
    }
}
