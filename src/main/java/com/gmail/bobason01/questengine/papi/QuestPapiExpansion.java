package com.gmail.bobason01.questengine.papi;

import com.gmail.bobason01.questengine.QuestEnginePlugin;
import com.gmail.bobason01.questengine.progress.ProgressRepository;
import com.gmail.bobason01.questengine.quest.QuestDef;
import com.gmail.bobason01.questengine.quest.QuestRepository;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class QuestPapiExpansion extends PlaceholderExpansion {

    private final QuestEnginePlugin plugin;

    private static final Map<String, CacheNode> CACHE = new ConcurrentHashMap<>(256);
    private static final long TTL_NANOS = 100_000_000L;
    private static final int BAR_LEN = 20;

    private record CacheNode(String val, long expireTime) {}

    public QuestPapiExpansion(QuestEnginePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "questengine";
    }

    @Override
    public @NotNull String getAuthor() {
        return "crston";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    public void clearCache(UUID uid) {
        CACHE.entrySet().removeIf(entry -> entry.getKey().startsWith(uid.toString()));
    }

    @Override
    public String onPlaceholderRequest(Player p, @NotNull String params) {
        if (p == null || params.isEmpty()) return "";

        String key = p.getUniqueId().toString().concat(":").concat(params);
        long now = System.nanoTime();

        CacheNode node = CACHE.get(key);
        if (node != null && now < node.expireTime) {
            return node.val;
        }

        String val = compute(p, params.toLowerCase(Locale.ROOT));

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

        switch (id) {
            case "active_count": return String.valueOf(repo.activeCount(uid, name));
            case "completed_count": return String.valueOf(repo.completedCount(uid, name));
            case "total_points": return String.valueOf(repo.totalPoints(uid, name));
            case "active_list_ids": return joinList(repo.activeQuestIds(uid, name));
            case "completed_list_ids": return joinList(repo.completedQuestIds(uid, name));
            case "active_list_names": return joinQuestNames(repo.activeQuestIds(uid, name), quests);
        }

        if (id.startsWith("active_")) {
            String[] parts = id.split("_", 3);
            if (parts.length == 3) {
                int index = parseInt(parts[1]);
                if (index > 0) {
                    List<String> activeList = repo.activeQuestIds(uid, name);
                    if (index <= activeList.size()) {
                        String qid = activeList.get(index - 1);
                        QuestDef q = quests.get(qid);
                        return q == null ? "" : getField(q, qid, parts[2], repo, uid, name);
                    }
                }
                return "";
            }
        }

        if (id.startsWith("qid_")) {
            String sub = id.substring(4);
            int lastUnderscore = sub.lastIndexOf('_');

            if (lastUnderscore != -1) {
                String qid = sub.substring(0, lastUnderscore);
                String field = sub.substring(lastUnderscore + 1);

                QuestDef q = quests.get(qid);

                // qid 안에 언더스코어가 포함되어 있는 퀘스트의 경우 예외 처리
                // ex) qid_zombie_kill_1_title (qid: zombie_kill_1, field: title)
                if (q == null) {
                    for (int i = sub.length() - 1; i > 0; i--) {
                        if (sub.charAt(i) == '_') {
                            String potentialQid = sub.substring(0, i);
                            String potentialField = sub.substring(i + 1);
                            QuestDef potentialQ = quests.get(potentialQid);
                            if (potentialQ != null) {
                                return getField(potentialQ, potentialQid, potentialField, repo, uid, name);
                            }
                        }
                    }
                } else {
                    return getField(q, qid, field, repo, uid, name);
                }
            }
        }

        return "";
    }

    private String getField(QuestDef q, String qid, String field, ProgressRepository repo, UUID uid, String name) {
        return switch (field) {
            case "id" -> q.id;
            case "name" -> q.name;
            case "title" -> q.display != null ? ChatColor.translateAlternateColorCodes('&', q.display.title) : q.name;
            case "reward" -> q.display != null ? ChatColor.translateAlternateColorCodes('&', q.display.reward) : "";
            case "description" -> {
                if (q.display != null && q.display.description != null && !q.display.description.isEmpty()) {
                    yield ChatColor.translateAlternateColorCodes('&', q.display.description.get(0));
                }
                yield "";
            }
            case "description_full" -> {
                if (q.display != null && q.display.description != null && !q.display.description.isEmpty()) {
                    StringBuilder descBuilder = new StringBuilder();
                    for (int i = 0; i < q.display.description.size(); i++) {
                        descBuilder.append(ChatColor.translateAlternateColorCodes('&', q.display.description.get(i)));
                        if (i < q.display.description.size() - 1) descBuilder.append("\n");
                    }
                    yield descBuilder.toString();
                }
                yield "";
            }
            case "icon" -> q.display != null && q.display.icon != null ? q.display.icon : "BOOK";
            case "cmd" -> q.display != null ? String.valueOf(q.display.model) : "0";
            case "party" -> String.valueOf(q.party);
            case "points" -> String.valueOf(q.points);
            case "target" -> String.valueOf(q.amount);
            case "progress" -> String.valueOf(repo.value(uid, name, qid));
            case "percent" -> percent(repo.value(uid, name, qid), q.amount);
            case "bar" -> bar(repo.value(uid, name, qid), q.amount);
            case "state" -> {
                if (repo.isActive(uid, name, qid)) yield "active";
                if (repo.isCompleted(uid, name, qid)) yield "completed";
                yield "";
            }
            default -> "";
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
        int p = (int) ((Math.min((double) v / t, 1.0)) * 100);
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