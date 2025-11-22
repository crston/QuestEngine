package com.gmail.bobason01.questengine.progress;

import java.io.Serializable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * PlayerData
 * 반복 퀘스트 완전 지원 버전
 * - completedCount : 지금까지 완료한 누적 횟수
 *
 * repeat 규칙:
 *   repeat < 0  → 무한 반복 OK
 *   repeat = 0  → 총 1회 완료만 OK
 *   repeat > 0  → 총 repeat 회 완료 OK
 *
 * completedCount >= totalSlots(repeat) 이면 더 이상 시작 불가
 */
public final class PlayerData implements Serializable {

    private static final long serialVersionUID = 1L;

    private final UUID id;
    private String name;

    private final Map<String, Node> map = new ConcurrentHashMap<>(32, 0.75f, 2);
    private final LinkedHashSet<String> activeOrder = new LinkedHashSet<>(8);

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
        if (repeat < 0) return -1;
        if (repeat == 0) return 1;
        return repeat;
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

    /**
     * 반복 가능 여부 검사
     */
    public boolean canStart(String questId, int repeatConfig) {
        questId = norm(questId);
        if (questId == null) return false;

        Node n = map.get(questId);

        // repeat < 0 -> 무한 반복
        if (repeatConfig < 0) {
            return true;
        }

        int slots = totalSlots(repeatConfig);

        // 기록 없으면 처음 시작 OK
        if (n == null) return true;

        // 이미 완전 종료 상태면 불가
        if (n.completed) return false;

        // 누적 완료 횟수가 허용치를 넘었으면 불가
        if (n.completedCount >= slots) return false;

        return true;
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

    /**
     * 완전 초기화 (repeat 포함)
     */
    public void resetQuest(String questId) {
        questId = norm(questId);
        if (questId == null) return;

        Node n = map.get(questId);
        if (n == null) return;

        n.active = false;
        n.completed = false;
        n.value = 0;
        n.completedCount = 0;
        n.points = 0;

        activeOrder.remove(questId);
    }

    /**
     * 구버전 호환
     */
    public void complete(String questId, int points) {
        complete(questId, points, 0);
    }

    /**
     * 반복 퀘스트 완료 처리
     */
    public void complete(String questId, int points, int repeatConfig) {
        questId = norm(questId);
        if (questId == null) return;

        Node n = map.computeIfAbsent(questId, k -> new Node());

        n.active = false;
        n.value = 0;

        if (points > n.points) {
            n.points = points;
        }

        activeOrder.remove(questId);

        if (repeatConfig >= 0) {
            int slots = totalSlots(repeatConfig);
            if (n.completedCount < Integer.MAX_VALUE) {
                n.completedCount++;
            }
            if (n.completedCount >= slots) {
                n.completed = true;
            }
        }
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

    public int completedCountOf(String questId) {
        questId = norm(questId);
        if (questId == null) return 0;
        Node n = map.get(questId);
        return n == null ? 0 : n.completedCount;
    }

    public int getRepeatCount(String questId) {
        return completedCountOf(questId);
    }

    public void setRepeatCount(String questId, int count) {
        questId = norm(questId);
        if (questId == null) return;

        Node n = map.computeIfAbsent(questId, k -> new Node());
        n.completedCount = Math.max(0, count);
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

    public Set<String> getCompletedQuests() {
        return new HashSet<>(completedIds());
    }

    public Set<String> getActiveQuests() {
        return new LinkedHashSet<>(activeOrder);
    }
}
