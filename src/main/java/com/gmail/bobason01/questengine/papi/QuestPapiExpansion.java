package com.gmail.bobason01.questengine.papi;

import com.gmail.bobason01.questengine.QuestEnginePlugin;
import com.gmail.bobason01.questengine.progress.ProgressRepository;
import com.gmail.bobason01.questengine.quest.QuestDef;
import com.gmail.bobason01.questengine.quest.QuestRepository;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * QuestPapiExpansion
 * - PlaceholderAPI 통합 확장
 * - 초고속 캐싱(1s TTL) + GC-free 연산 구조
 */
public final class QuestPapiExpansion extends PlaceholderExpansion {

    private final QuestEnginePlugin plugin;

    private static final Map<String, CacheNode> CACHE = new ConcurrentHashMap<>(256);
    private static final long TTL_NANOS = 1_000_000_000L; // 1s
    private static final int BAR_LEN = 20;

    private record CacheNode(String val, long time) {}

    public QuestPapiExpansion(QuestEnginePlugin plugin) {
        this.plugin = plugin;
    }

    @Override public String getIdentifier() { return "questengine"; }
    @Override public String getAuthor() { return "crston"; }
    @Override public String getVersion() { return plugin.getDescription().getVersion(); }
    @Override public boolean persist() { return true; }

    @Override
    public String onPlaceholderRequest(Player p, String rawId) {
        if (p == null || rawId == null || rawId.isEmpty()) return "";

        String key = p.getUniqueId() + "|" + rawId;
        long now = System.nanoTime();
        CacheNode node = CACHE.get(key);
        if (node != null && now - node.time <= TTL_NANOS) return node.val;

        String id = rawId.toLowerCase(Locale.ROOT);
        String val = compute(p, id);

        CACHE.put(key, new CacheNode(val, now));
        return val;
    }

    private String compute(Player p, String id) {
        ProgressRepository repo = plugin.engine().progress();
        QuestRepository quests = plugin.engine().quests();

        UUID uid = p.getUniqueId();
        String name = p.getName();

        // --- 단순 전역 ---
        switch (id) {
            case "active_count": return String.valueOf(repo.activeCount(uid, name));
            case "completed_count": return String.valueOf(repo.completedCount(uid, name));
            case "total_points": return String.valueOf(repo.totalPoints(uid, name));
        }

        // --- 첫 번째 활성 퀘스트 ---
        if (id.equals("first_active_id")) {
            String cur = repo.firstActiveId(uid, name);
            return cur == null ? "" : cur;
        }

        if (id.startsWith("active_")) {
            // "active_" 뒤를 분리
            String[] parts = id.substring(7).split("_");
            if (parts.length == 2) {
                String kind = parts[0];
                int index = fastParseInt(parts[1], 0);
                if (index >= 1) {
                    List<String> active = repo.activeQuestIds(uid, name);
                    if (index <= active.size()) {
                        String qid = active.get(index - 1);
                        QuestDef q = quests.get(qid);
                        if (q == null) return "";
                        return getActiveField(q, kind, repo, uid, name, qid);
                    }
                }
            }
        }

        // --- current_* ---
        if (id.startsWith("current_")) {
            String cur = repo.firstActiveId(uid, name);
            if (cur == null) return "";
            QuestDef q = quests.get(cur);
            if (q == null) return "";
            return getCurrentField(id, q, repo, uid, name, cur);
        }

        // --- name_<id> 등 ---
        int idx = id.indexOf('_');
        if (idx > 0) {
            String kind = id.substring(0, idx);
            String qid = id.substring(idx + 1);
            QuestDef q = quests.get(qid);
            return getByIdField(kind, q, repo, uid, name, qid);
        }

        // --- 리스트 ---
        switch (id) {
            case "active_list_names": return joinQuestNames(repo.activeQuestIds(uid, name), quests);
            case "active_list_ids": return joinList(repo.activeQuestIds(uid, name));
            case "completed_list_ids": return joinList(repo.completedQuestIds(uid, name));
            default: return "";
        }
    }

    private static String getActiveField(QuestDef q, String kind, ProgressRepository repo, UUID uid, String name, String qid) {
        int v;
        return switch (kind) {
            case "id" -> q.id;
            case "name" -> q.name;
            case "title" -> q.display.title;
            case "reward" -> q.display.reward;
            case "points" -> Integer.toString(q.points);
            case "target" -> Integer.toString(q.amount);
            case "progress" -> (v = repo.value(uid, name, qid)) + "/" + q.amount;
            case "percent" -> percent(repo.value(uid, name, qid), q.amount);
            case "bar" -> bar(repo.value(uid, name, qid), q.amount);
            default -> "";
        };
    }

    private static String getCurrentField(String id, QuestDef q, ProgressRepository repo, UUID uid, String name, String qid) {
        int v;
        return switch (id) {
            case "current_id" -> q.id;
            case "current_name" -> q.name;
            case "current_title" -> q.display.title;
            case "current_reward" -> q.display.reward;
            case "current_target" -> Integer.toString(q.amount);
            case "current_points" -> Integer.toString(q.points);
            case "current_progress" -> (v = repo.value(uid, name, qid)) + "/" + q.amount;
            case "current_percent" -> percent(repo.value(uid, name, qid), q.amount);
            case "current_bar" -> bar(repo.value(uid, name, qid), q.amount);
            default -> "";
        };
    }

    private static String getByIdField(String kind, QuestDef q, ProgressRepository repo, UUID uid, String name, String qid) {
        if (q == null) {
            return switch (kind) {
                case "active", "completed" -> "false";
                default -> "";
            };
        }
        int v;
        return switch (kind) {
            case "name" -> q.name;
            case "title" -> q.display.title;
            case "reward" -> q.display.reward;
            case "points" -> Integer.toString(q.points);
            case "target" -> Integer.toString(q.amount);
            case "progress" -> Integer.toString(repo.value(uid, name, qid));
            case "percent" -> percent(repo.value(uid, name, qid), q.amount);
            case "bar" -> bar(repo.value(uid, name, qid), q.amount);
            case "active" -> Boolean.toString(repo.isActive(uid, name, qid));
            case "completed" -> Boolean.toString(repo.isCompleted(uid, name, qid));
            default -> "";
        };
    }

    private static String joinQuestNames(List<String> ids, QuestRepository quests) {
        int size = ids.size();
        if (size == 0) return "";
        StringBuilder sb = new StringBuilder(size * 12);
        for (int i = 0; i < size; i++) {
            QuestDef q = quests.get(ids.get(i));
            if (i > 0) sb.append(", ");
            sb.append(q == null ? ids.get(i) : q.name);
        }
        return sb.toString();
    }

    private static String joinList(List<String> ids) {
        int size = ids.size();
        if (size == 0) return "";
        StringBuilder sb = new StringBuilder(size * 10);
        for (int i = 0; i < size; i++) {
            if (i > 0) sb.append(", ");
            sb.append(ids.get(i));
        }
        return sb.toString();
    }

    private static String percent(int v, int t) {
        if (t <= 0) return "0%";
        double pct = v * 100.0 / t;
        if (pct < 0) pct = 0;
        if (pct > 100) pct = 100;
        return (int) Math.round(pct) + "%";
    }

    private static String bar(int v, int t) {
        if (t <= 0) t = 1;
        double pct = v / (double) t;
        if (pct < 0) pct = 0;
        if (pct > 1) pct = 1;
        int fill = (int) (pct * BAR_LEN);
        StringBuilder sb = new StringBuilder(BAR_LEN * 3);
        sb.append("§a");
        for (int i = 0; i < fill; i++) sb.append('■');
        sb.append("§7");
        for (int i = fill; i < BAR_LEN; i++) sb.append('■');
        return sb.toString();
    }

    private static int fastParseInt(String s, int from) {
        int n = 0;
        for (int i = from; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c < '0' || c > '9') break;
            n = n * 10 + (c - '0');
        }
        return n == 0 ? -1 : n;
    }
}
