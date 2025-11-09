package com.gmail.bobason01.questengine.progress;

import java.io.Serializable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * PlayerData
 * - 고성능 퀘스트 상태 저장 클래스
 * - ConcurrentHashMap 기반 병렬 안전성 유지
 * - activeOrder는 LinkedHashSet으로 O(1) 삽입·삭제·순서 보장
 * - 힙 할당 최소화 및 GC 부하 최소화
 * - 외부 패키지 접근 완전 허용 (public)
 */
public final class PlayerData implements Serializable {

    private static final long serialVersionUID = 1L;

    private final UUID id;
    private final String name;

    // questId → Node
    private final Map<String, Node> map = new ConcurrentHashMap<>(32, 0.75f, 2);

    // 안정적 순서를 가진 활성 퀘스트
    private final LinkedHashSet<String> activeOrder = new LinkedHashSet<>(8);

    public PlayerData(UUID id, String name) {
        this.id = id;
        this.name = name;
    }

    // ------------------------------------------------
    // 내부 상태 노드
    // ------------------------------------------------

    public static final class Node implements Serializable {
        private static final long serialVersionUID = 1L;
        public boolean active;
        public boolean completed;
        public int value;
        public int points;
    }

    // ------------------------------------------------
    // 퀘스트 상태 관련
    // ------------------------------------------------

    public boolean isActive(String qid) {
        Node n = map.get(qid);
        return n != null && n.active;
    }

    public boolean isCompleted(String qid) {
        Node n = map.get(qid);
        return n != null && n.completed;
    }

    public void start(String qid) {
        qid = qid.intern();
        Node n = map.computeIfAbsent(qid, k -> new Node());
        n.active = true;
        n.completed = false;
        n.value = 0;
        synchronized (activeOrder) {
            activeOrder.add(qid);
        }
    }

    public void cancel(String qid) {
        Node n = map.get(qid);
        if (n != null) n.active = false;
        synchronized (activeOrder) {
            activeOrder.remove(qid);
        }
    }

    public void complete(String qid, int pts) {
        Node n = map.computeIfAbsent(qid, k -> new Node());
        n.active = false;
        n.completed = true;
        n.points += pts;
        synchronized (activeOrder) {
            activeOrder.remove(qid);
        }
    }

    public int add(String qid, int amt) {
        Node n = map.computeIfAbsent(qid, k -> new Node());
        n.value += amt;
        return n.value;
    }

    public int valueOf(String qid) {
        Node n = map.get(qid);
        return (n == null) ? 0 : n.value;
    }

    public int pointsOf(String qid) {
        Node n = map.get(qid);
        return (n == null) ? 0 : n.points;
    }

    // ------------------------------------------------
    // 집계 및 조회
    // ------------------------------------------------

    public List<String> activeIds() {
        synchronized (activeOrder) {
            if (activeOrder.isEmpty()) return Collections.emptyList();
            return List.copyOf(activeOrder);
        }
    }

    public void cancelAll() {
        for (Node n : map.values()) n.active = false;
        synchronized (activeOrder) {
            activeOrder.clear();
        }
    }

    public int activeCount() {
        int c = 0;
        for (Node n : map.values()) {
            if (n.active) c++;
        }
        return c;
    }

    public int totalPoints() {
        int sum = 0;
        for (Node n : map.values()) sum += n.points;
        return sum;
    }

    public String firstActiveId() {
        synchronized (activeOrder) {
            if (activeOrder.isEmpty()) return null;
            Iterator<String> it = activeOrder.iterator();
            return it.hasNext() ? it.next() : null;
        }
    }

    public List<String> completedIds() {
        List<String> out = new ArrayList<>(map.size() / 2);
        for (Map.Entry<String, Node> e : map.entrySet()) {
            if (e.getValue().completed) out.add(e.getKey());
        }
        return out.isEmpty() ? Collections.emptyList() : out;
    }

    // ------------------------------------------------
    // Getter (for storage, repository, debug)
    // ------------------------------------------------

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Map<String, Node> getMap() {
        return map;
    }
}
