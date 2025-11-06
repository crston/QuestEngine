package com.gmail.bobason01.questengine.command;

import com.gmail.bobason01.questengine.QuestEnginePlugin;
import com.gmail.bobason01.questengine.quest.QuestDef;
import com.gmail.bobason01.questengine.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

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
            s.sendMessage(msg.get("admin.usage"));
            return true;
        }

        String sub = a[0].toLowerCase(Locale.ROOT);
        if (SUB_RELOAD.equals(sub)) {
            plugin.engine().quests().reload();
            s.sendMessage(msg.get("admin.reloaded"));
            return true;
        }
        if (SUB_GIVE.equals(sub))     { doGive(s, a); return true; }
        if (SUB_STOP.equals(sub))     { doStop(s, a); return true; }
        if (SUB_COMPLETE.equals(sub)) { doComplete(s, a); return true; }
        if (SUB_RESET.equals(sub))    { doReset(s, a); return true; }
        if (SUB_LIST.equals(sub))     { doList(s, a); return true; }
        if (SUB_POINTS.equals(sub))   { doPoints(s, a); return true; }
        if (SUB_RANK.equals(sub))     { doRank(s); return true; }

        s.sendMessage(msg.get("admin.usage"));
        return true;
    }

    private Player findOnlinePlayer(String name) {
        if (name == null) return null;
        Player p = Bukkit.getPlayerExact(name);
        if (p != null) return p;
        return Bukkit.getPlayer(name);
    }

    private void doGive(CommandSender s, String[] a) {
        if (a.length < 3) { s.sendMessage(msg.get("admin.usage")); return; }
        Player p = findOnlinePlayer(a[1]);
        QuestDef q = plugin.engine().quests().get(a[2]);
        if (p == null || q == null) { s.sendMessage(msg.get("admin.invalid_args")); return; }

        plugin.engine().startQuest(p, q);

        String m = msg.get("admin.started");
        if (m == null) m = "&aStarted";
        s.sendMessage(
                m.replace("%quest_name%", q.display.title).replace("%player%", p.getName())
        );
    }

    private void doStop(CommandSender s, String[] a) {
        if (a.length < 3) { s.sendMessage(msg.get("admin.usage")); return; }
        Player p = findOnlinePlayer(a[1]);
        QuestDef q = plugin.engine().quests().get(a[2]);
        if (p == null || q == null) { s.sendMessage(msg.get("admin.invalid_args")); return; }

        plugin.engine().stopQuest(p, q);

        String m = msg.get("admin.stopped");
        if (m == null) m = "&cStopped";
        s.sendMessage(
                m.replace("%quest_name%", q.display.title).replace("%player%", p.getName())
        );
    }

    private void doComplete(CommandSender s, String[] a) {
        if (a.length < 3) { s.sendMessage(msg.get("admin.usage")); return; }
        Player p = findOnlinePlayer(a[1]);
        QuestDef q = plugin.engine().quests().get(a[2]);
        if (p == null || q == null) { s.sendMessage(msg.get("admin.invalid_args")); return; }

        plugin.engine().forceComplete(p, q);

        String m = msg.get("admin.completed");
        if (m == null) m = "&eCompleted";
        s.sendMessage(
                m.replace("%quest_name%", q.display.title).replace("%player%", p.getName())
        );
    }

    private void doReset(CommandSender s, String[] a) {
        if (a.length < 3) { s.sendMessage(msg.get("admin.usage")); return; }
        Player p = findOnlinePlayer(a[1]);
        if (p == null) { s.sendMessage(msg.get("admin.offline")); return; }

        plugin.engine().progress().reset(p.getUniqueId(), p.getName(), a[2]);

        String m = msg.get("admin.reset_done");
        if (m == null) m = "&7Reset";
        s.sendMessage(
                m.replace("%quest_name%", a[2]).replace("%player%", p.getName())
        );
    }

    private void doList(CommandSender s, String[] a) {
        if (a.length < 2) { s.sendMessage(msg.get("admin.usage")); return; }
        Player p = findOnlinePlayer(a[1]);
        if (p == null) { s.sendMessage(msg.get("admin.offline")); return; }
        plugin.engine().listActiveTo(p);
    }

    private void doPoints(CommandSender s, String[] a) {
        if (a.length < 2) { s.sendMessage("/questadmin points <player>"); return; }
        Player p = findOnlinePlayer(a[1]);
        if (p == null) { s.sendMessage(msg.get("admin.offline")); return; }
        int pts = plugin.engine().progress().getPoints(p.getUniqueId());

        String m = msg.get("admin.points");
        if (m == null) m = "&e%player% points %points%";
        s.sendMessage(
                m.replace("%player%", p.getName()).replace("%points%", Integer.toString(pts))
        );
    }

    private void doRank(CommandSender s) {
        String calcMsg = msg.get("admin.rank_calc");
        if (calcMsg == null) calcMsg = "&7Calculating ranking";
        s.sendMessage(calcMsg);

        // 상위 10명만 O(n log k)로 추출
        CompletableFuture.runAsync(() -> {
            Map<UUID, Integer> all = plugin.engine().progress().getAllPoints();
            if (all == null || all.isEmpty()) {
                Bukkit.getScheduler().runTask(plugin, () -> s.sendMessage("No data"));
                return;
            }

            final int K = 10;
            PriorityQueue<Map.Entry<UUID, Integer>> pq =
                    new PriorityQueue<>(K, Comparator.comparingInt(Map.Entry::getValue));

            for (Map.Entry<UUID, Integer> e : all.entrySet()) {
                if (pq.size() < K) {
                    pq.offer(e);
                } else if (e.getValue() > pq.peek().getValue()) {
                    pq.poll();
                    pq.offer(e);
                }
            }

            List<Map.Entry<UUID, Integer>> top = new ArrayList<>(pq);
            top.sort((x, y) -> Integer.compare(y.getValue(), x.getValue()));

            StringBuilder sb = new StringBuilder(128);
            sb.append("§a[Quest Points Ranking]\n");
            AtomicInteger rank = new AtomicInteger(1);
            for (int i = 0; i < top.size(); i++) {
                Map.Entry<UUID, Integer> e = top.get(i);
                UUID id = e.getKey();
                int points = e.getValue();

                Player online = Bukkit.getPlayer(id);
                String name;
                if (online != null) name = online.getName();
                else {
                    OfflinePlayer off = Bukkit.getOfflinePlayer(id);
                    name = off != null && off.getName() != null ? off.getName() : "Unknown";
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
            List<String> out = new ArrayList<>(SUBS.size());
            for (int i = 0; i < SUBS.size(); i++) {
                String sub = SUBS.get(i);
                if (sub.startsWith(prefix)) out.add(sub);
            }
            return out;
        }

        String sub = a[0].toLowerCase(Locale.ROOT);

        if (len == 2 && SUBS_NEED_PLAYER.contains(sub)) {
            String prefix = a[1].toLowerCase(Locale.ROOT);
            Collection<? extends Player> online = Bukkit.getOnlinePlayers();
            List<String> out = new ArrayList<>(online.size());
            for (Player p : online) {
                String name = p.getName();
                if (name != null && name.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                    out.add(name);
                }
            }
            return out;
        }

        if (len == 3 && SUBS_NEED_QUEST.contains(sub)) {
            String prefix = a[2].toLowerCase(Locale.ROOT);
            Collection<String> ids = plugin.engine().quests().ids();
            List<String> out = new ArrayList<>(Math.max(8, ids.size()));
            for (String id : ids) {
                if (id != null && id.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                    out.add(id);
                }
            }
            return out;
        }

        return Collections.emptyList();
    }
}
