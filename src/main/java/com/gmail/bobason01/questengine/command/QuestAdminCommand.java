package com.gmail.bobason01.questengine.command;

import com.gmail.bobason01.questengine.QuestEnginePlugin;
import com.gmail.bobason01.questengine.quest.QuestDef;
import com.gmail.bobason01.questengine.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * QuestAdminCommand
 * - 관리자용 퀘스트 제어 명령어
 * - 색상 코드(&) 완전 지원
 * - prefix 제거 버전
 */
public final class QuestAdminCommand extends BaseCommand implements TabCompleter {

    private static final String SUB_RELOAD   = "reload";
    private static final String SUB_GIVE     = "give";
    private static final String SUB_STOP     = "stop";
    private static final String SUB_COMPLETE = "complete";
    private static final String SUB_RESET    = "reset";
    private static final String SUB_LIST     = "list";
    private static final String SUB_POINTS   = "points";
    private static final String SUB_RANK     = "rank";

    private static final List<String> SUBS = Arrays.asList(
            SUB_RELOAD, SUB_GIVE, SUB_STOP, SUB_COMPLETE, SUB_RESET, SUB_LIST, SUB_POINTS, SUB_RANK
    );

    private static final Set<String> SUBS_NEED_PLAYER = new HashSet<>(Arrays.asList(
            SUB_GIVE, SUB_STOP, SUB_COMPLETE, SUB_RESET, SUB_LIST, SUB_POINTS
    ));
    private static final Set<String> SUBS_NEED_QUEST = new HashSet<>(Arrays.asList(
            SUB_GIVE, SUB_STOP, SUB_COMPLETE, SUB_RESET
    ));

    private final Msg msg;

    public QuestAdminCommand(QuestEnginePlugin plugin) {
        super(plugin);
        this.msg = plugin.msg();
        PluginCommand cmd = plugin.getCommand("questadmin");
        if (cmd != null) {
            cmd.setExecutor(this);
            cmd.setTabCompleter(this);
        } else {
            plugin.getLogger().warning("questadmin command not found in plugin.yml");
        }
    }

    @Override
    public boolean onCommand(CommandSender s, Command c, String l, String[] a) {
        if (a.length == 0) {
            s.sendMessage(color(msg.get("admin.usage")));
            return true;
        }

        String sub = a[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case SUB_RELOAD -> {
                // 1. 퀘스트 다시 읽기
                plugin.engine().quests().reload();
                plugin.engine().quests().rebuildEventMap();

                // 2. 엔진 내부 캐시 갱신
                plugin.engine().refreshEventCache();

                // 3. (선택) 조건 캐시 / 타깃 매처 초기화
                plugin.engine().shutdown();
                plugin.engine().quests().reload(); // reload 다시 한번, shutdown 후 필요

                // 4. 메시지
                s.sendMessage(color("&a[QuestEngine] Reload complete"));
                return true;
            }

            case SUB_GIVE -> { doGive(s, a); return true; }
            case SUB_STOP -> { doStop(s, a); return true; }
            case SUB_COMPLETE -> { doComplete(s, a); return true; }
            case SUB_RESET -> { doReset(s, a); return true; }
            case SUB_LIST -> { doList(s, a); return true; }
            case SUB_POINTS -> { doPoints(s, a); return true; }
            case SUB_RANK -> { doRank(s); return true; }
            default -> {
                s.sendMessage(color(msg.get("admin.usage")));
                return true;
            }
        }
    }

    private Player findOnlinePlayer(String name) {
        if (name == null) return null;
        Player p = Bukkit.getPlayerExact(name);
        return p != null ? p : Bukkit.getPlayer(name);
    }

    private void doGive(CommandSender s, String[] a) {
        if (a.length < 3) { s.sendMessage(color(msg.get("admin.usage"))); return; }
        Player p = findOnlinePlayer(a[1]);
        QuestDef q = plugin.engine().quests().get(a[2]);
        if (p == null || q == null) { s.sendMessage(color(msg.get("admin.invalid_args"))); return; }

        plugin.engine().startQuest(p, q);
        String m = msg.get("admin.started");
        if (m == null) m = "&aStarted";
        s.sendMessage(color(m
                .replace("%quest_name%", q.display.title)
                .replace("%player%", p.getName())));
    }

    private void doStop(CommandSender s, String[] a) {
        if (a.length < 3) { s.sendMessage(color(msg.get("admin.usage"))); return; }
        Player p = findOnlinePlayer(a[1]);
        QuestDef q = plugin.engine().quests().get(a[2]);
        if (p == null || q == null) { s.sendMessage(color(msg.get("admin.invalid_args"))); return; }

        plugin.engine().stopQuest(p, q);
        String m = msg.get("admin.stopped");
        if (m == null) m = "&cStopped";
        s.sendMessage(color(m
                .replace("%quest_name%", q.display.title)
                .replace("%player%", p.getName())));
    }

    private void doComplete(CommandSender s, String[] a) {
        if (a.length < 3) { s.sendMessage(color(msg.get("admin.usage"))); return; }
        Player p = findOnlinePlayer(a[1]);
        QuestDef q = plugin.engine().quests().get(a[2]);
        if (p == null || q == null) { s.sendMessage(color(msg.get("admin.invalid_args"))); return; }

        plugin.engine().forceComplete(p, q);
        String m = msg.get("admin.completed");
        if (m == null) m = "&b%quest_name% 퀘스트를 완료 처리했습니다!";
        s.sendMessage(color(m
                .replace("%quest_name%", q.display.title)
                .replace("%player%", p.getName())));
    }

    private void doReset(CommandSender s, String[] a) {
        if (a.length < 3) { s.sendMessage(color(msg.get("admin.usage"))); return; }
        Player p = findOnlinePlayer(a[1]);
        if (p == null) { s.sendMessage(color(msg.get("admin.offline"))); return; }

        plugin.engine().progress().reset(p.getUniqueId(), p.getName(), a[2]);
        String m = msg.get("admin.reset_done");
        if (m == null) m = "&7Reset complete";
        s.sendMessage(color(m
                .replace("%quest_name%", a[2])
                .replace("%player%", p.getName())));
    }

    private void doList(CommandSender s, String[] a) {
        if (a.length < 2) { s.sendMessage(color(msg.get("admin.usage"))); return; }
        Player p = findOnlinePlayer(a[1]);
        if (p == null) { s.sendMessage(color(msg.get("admin.offline"))); return; }
        plugin.engine().listActiveTo(p);
    }

    private void doPoints(CommandSender s, String[] a) {
        if (a.length < 2) { s.sendMessage(color(msg.get("admin.usage"))); return; }
        Player p = findOnlinePlayer(a[1]);
        if (p == null) { s.sendMessage(color(msg.get("admin.offline"))); return; }
        int pts = plugin.engine().progress().getPoints(p.getUniqueId());

        String m = msg.get("admin.points");
        if (m == null) m = "&e%player% Quest Points: %points%";
        s.sendMessage(color(m
                .replace("%player%", p.getName())
                .replace("%points%", Integer.toString(pts))));
    }

    private void doRank(CommandSender s) {
        s.sendMessage(color(msg.get("admin.rank_calc")));

        CompletableFuture.runAsync(() -> {
            Map<UUID, Integer> all = plugin.engine().progress().getAllPoints();
            if (all == null || all.isEmpty()) {
                Bukkit.getScheduler().runTask(plugin, () -> s.sendMessage(color("&7데이터가 없습니다.")));
                return;
            }

            final int K = 10;
            PriorityQueue<Map.Entry<UUID, Integer>> pq =
                    new PriorityQueue<>(K, Comparator.comparingInt(Map.Entry::getValue));

            for (Map.Entry<UUID, Integer> e : all.entrySet()) {
                if (pq.size() < K) pq.offer(e);
                else if (e.getValue() > pq.peek().getValue()) {
                    pq.poll();
                    pq.offer(e);
                }
            }

            List<Map.Entry<UUID, Integer>> top = new ArrayList<>(pq);
            top.sort((x, y) -> Integer.compare(y.getValue(), x.getValue()));

            StringBuilder sb = new StringBuilder(128);
            sb.append("§a[Quest Points Ranking]\n");
            AtomicInteger rank = new AtomicInteger(1);

            for (Map.Entry<UUID, Integer> e : top) {
                UUID id = e.getKey();
                int points = e.getValue();
                String name;

                Player online = Bukkit.getPlayer(id);
                if (online != null) name = online.getName();
                else {
                    OfflinePlayer off = Bukkit.getOfflinePlayer(id);
                    name = (off != null && off.getName() != null) ? off.getName() : "Unknown";
                }

                sb.append("§7#").append(rank.getAndIncrement())
                        .append(" §f").append(name)
                        .append(" §8- §e").append(points).append("\n");
            }

            Bukkit.getScheduler().runTask(plugin, () -> s.sendMessage(sb.toString()));
        }, plugin.engine().asyncPool());
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String l, String[] a) {
        int len = a.length;
        if (len == 0) return Collections.emptyList();

        if (len == 1) {
            String prefix = a[0].toLowerCase(Locale.ROOT);
            List<String> out = new ArrayList<>();
            for (String sub : SUBS)
                if (sub.startsWith(prefix)) out.add(sub);
            return out;
        }

        String sub = a[0].toLowerCase(Locale.ROOT);

        if (len == 2 && SUBS_NEED_PLAYER.contains(sub)) {
            String prefix = a[1].toLowerCase(Locale.ROOT);
            List<String> out = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers())
                if (p.getName().toLowerCase(Locale.ROOT).startsWith(prefix))
                    out.add(p.getName());
            return out;
        }

        if (len == 3 && SUBS_NEED_QUEST.contains(sub)) {
            String prefix = a[2].toLowerCase(Locale.ROOT);
            List<String> out = new ArrayList<>();
            for (String id : plugin.engine().quests().ids())
                if (id.toLowerCase(Locale.ROOT).startsWith(prefix))
                    out.add(id);
            return out;
        }

        return Collections.emptyList();
    }

    private static String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s == null ? "" : s);
    }
}
