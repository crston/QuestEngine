package com.gmail.bobason01.questengine.papi;

import com.gmail.bobason01.questengine.QuestEnginePlugin;
import com.gmail.bobason01.questengine.progress.ProgressRepository;
import com.gmail.bobason01.questengine.quest.QuestDef;
import com.gmail.bobason01.questengine.quest.QuestRepository;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class QuestPapiExpansion extends PlaceholderExpansion {

    private final QuestEnginePlugin plugin;

    // 캐시는 짧게 유지 (0.1초)하여 실시간성 보장하되 TPS 방어
    private static final Map<String, CacheNode> CACHE = new ConcurrentHashMap<>(256);
    private static final long TTL_NANOS = 100_000_000L; // 100ms
    private static final int BAR_LEN = 20;

    private record CacheNode(String val, long expireTime) {}

    public QuestPapiExpansion(QuestEnginePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier() { return "questengine"; }

    @Override
    public String getAuthor() { return "crston"; }

    @Override
    public String getVersion() { return plugin.getDescription().getVersion(); }

    @Override
    public boolean persist() { return true; }

    /**
     * 캐시 정리 (플레이어 퇴장 시 호출 권장)
     */
    public void clearCache(UUID uid) {
        CACHE.entrySet().removeIf(entry -> entry.getKey().startsWith(uid.toString()));
    }

    @Override
    public String onPlaceholderRequest(Player p, String params) {
        if (p == null || params == null || params.isEmpty()) return "";

        // 캐시 키 생성 (String concat 대신 복합 키 사용 고려 가능하나 PAPI 특성상 String이 편함)
        String key = p.getUniqueId().toString().concat(":").concat(params);
        long now = System.nanoTime();

        CacheNode node = CACHE.get(key);
        if (node != null && now < node.expireTime) {
            return node.val;
        }

        String val = compute(p, params.toLowerCase(Locale.ROOT));

        // 결과가 null이 아니면 캐시 저장
        if (val != null) {
            CACHE.put(key, new CacheNode(val, now + TTL_NANOS));
        }

        return val == null ? "" : val;
    }

    private String compute(Player p, String id) {
        ProgressRepository repo = plugin.engine().progress();
        QuestRepository quests = plugin.engine().quests();
        UUID uid = p.getUniqueId();
        String name = p.getName();

        // 1. 통계 데이터 (Fast Path)
        switch (id) {
            case "active_count": return String.valueOf(repo.activeCount(uid, name));
            case "completed_count": return String.valueOf(repo.completedCount(uid, name));
            case "total_points": return String.valueOf(repo.totalPoints(uid, name));
            case "active_list_ids": return joinList(repo.activeQuestIds(uid, name));
            case "completed_list_ids": return joinList(repo.completedQuestIds(uid, name));
            case "active_list_names": return joinQuestNames(repo.activeQuestIds(uid, name), quests);
        }

        // 2. 동적 파싱 (split 최소화)
        // active_{index}_{field}
        if (id.startsWith("active_")) {
            int firstUnderscore = 6; // "active".length() + 1
            int secondUnderscore = id.indexOf('_', firstUnderscore);

            if (secondUnderscore != -1) {
                String indexStr = id.substring(firstUnderscore, secondUnderscore);
                String field = id.substring(secondUnderscore + 1);

                int index = parseInt(indexStr);
                if (index > 0) {
                    List<String> activeList = repo.activeQuestIds(uid, name);
                    if (index <= activeList.size()) {
                        String qid = activeList.get(index - 1);
                        QuestDef q = quests.get(qid);
                        return q == null ? "none" : getField(q, qid, field, repo, uid, name);
                    }
                }
                return "none";
            }
        }

        // qid_{questid}_{field}
        if (id.startsWith("qid_")) {
            int firstUnderscore = 3;
            int secondUnderscore = id.lastIndexOf('_'); // 필드는 마지막에 온다고 가정

            if (secondUnderscore != -1 && secondUnderscore > firstUnderscore) {
                String qid = id.substring(firstUnderscore + 1, secondUnderscore); // qid_{...}_field
                // 하지만 퀘스트 ID에 언더바가 있을 수 있으므로 로직 주의
                // 정확히는 qid_{id}_{field} 이므로 파싱이 모호할 수 있음.
                // 안전하게 split(3) 유지하되 limit 사용
                String[] parts = id.split("_", 3);
                if (parts.length == 3) {
                    QuestDef q = quests.get(parts[1]);
                    return q == null ? "none" : getField(q, parts[1], parts[2], repo, uid, name);
                }
            }
        }

        return null;
    }

    private String getField(QuestDef q, String qid, String field, ProgressRepository repo, UUID uid, String name) {
        return switch (field) {
            case "id" -> q.id;
            case "name" -> q.name; // 색상 코드 미적용 원본
            case "title" -> q.display != null ? ChatColor.translateAlternateColorCodes('&', q.display.title) : q.name;
            case "reward" -> q.display != null ? ChatColor.translateAlternateColorCodes('&', q.display.reward) : "";
            case "points" -> String.valueOf(q.points);
            case "target" -> String.valueOf(q.amount);
            case "progress" -> String.valueOf(repo.value(uid, name, qid));
            case "percent" -> percent(repo.value(uid, name, qid), q.amount);
            case "bar" -> bar(repo.value(uid, name, qid), q.amount);
            case "state" -> repo.isActive(uid, name, qid) ? "active" : (repo.isCompleted(uid, name, qid) ? "completed" : "none");
            default -> "none";
        };
    }

    private String joinQuestNames(List<String> ids, QuestRepository repo) {
        if (ids.isEmpty()) return "";
        StringBuilder sb = new StringBuilder(ids.size() * 16);
        for (int i = 0; i < ids.size(); i++) {
            if (i > 0) sb.append(", ");
            QuestDef q = repo.get(ids.get(i));
            sb.append(q == null ? ids.get(i) : q.name);
        }
        return sb.toString();
    }

    private String joinList(List<String> ids) {
        if (ids.isEmpty()) return "";
        return String.join(", ", ids);
    }

    private String percent(int v, int t) {
        if (t <= 0) return "0%";
        int p = (int) ((Math.min((double)v / t, 1.0)) * 100);
        return p + "%";
    }

    private String bar(int v, int t) {
        if (t <= 0) t = 1;
        double pct = Math.min(1.0, Math.max(0.0, (double) v / t));
        int fill = (int) (pct * BAR_LEN);

        return "§a" + "■".repeat(fill) + "§7" + "■".repeat(BAR_LEN - fill);
    }

    private int parseInt(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}