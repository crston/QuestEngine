package com.gmail.bobason01.questengine.command;

import com.gmail.bobason01.questengine.QuestEnginePlugin;
import com.gmail.bobason01.questengine.quest.QuestDef;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;

import java.util.*;
import java.util.concurrent.CompletableFuture;

public final class QuestAdminCommand extends BaseCommand {

    private static final List<String> SUBS = Arrays.asList(
            "reload", "give", "stop", "complete", "reset", "list", "points", "rank"
    );

    private static final Set<String> SUBS_NEED_PLAYER = new HashSet<>(Arrays.asList(
            "give", "stop", "complete", "reset", "list", "points"
    ));

    private static final Set<String> SUBS_NEED_QUEST = new HashSet<>(Arrays.asList(
            "give", "stop", "complete", "reset"
    ));

    public QuestAdminCommand(QuestEnginePlugin plugin) {
        super(plugin);
        PluginCommand cmd = plugin.getCommand("questadmin");
        if (cmd != null) {
            cmd.setExecutor(this);
            cmd.setTabCompleter(this);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(color(msg.get("admin.usage")));
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "reload" -> handleReload(sender);
            case "give" -> handleGive(sender, args);
            case "stop" -> handleStop(sender, args);
            case "complete" -> handleComplete(sender, args);
            case "reset" -> handleReset(sender, args);
            case "list" -> handleList(sender, args);
            case "points" -> handlePoints(sender, args);
            case "rank" -> handleRank(sender);
            default -> sender.sendMessage(color(msg.get("admin.usage")));
        }
        return true;
    }

    private void handleReload(CommandSender sender) {
        try {
            plugin.msg().reload();
            plugin.engine().refreshEventCache();
            sender.sendMessage(color("a[QuestEngine] Reload complete"));
        } catch (Throwable t) {
            sender.sendMessage(color("c[QuestEngine] Reload failed " + t.getMessage()));
        }
    }

    private void handleGive(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(color(msg.get("admin.usage")));
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        QuestDef def = plugin.engine().quests().get(args[2]);
        if (target == null || def == null) {
            sender.sendMessage(color(msg.get("admin.invalid_args")));
            return;
        }
        plugin.engine().startQuest(target, def);
        sender.sendMessage(color(msg.get("admin.started").replace("%quest_name%", def.display.title).replace("%player%", target.getName())));
    }

    private void handleStop(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(color(msg.get("admin.usage")));
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        QuestDef def = plugin.engine().quests().get(args[2]);
        if (target == null || def == null) {
            sender.sendMessage(color(msg.get("admin.invalid_args")));
            return;
        }
        plugin.engine().stopQuest(target, def);
        sender.sendMessage(color(msg.get("admin.stopped").replace("%quest_name%", def.display.title).replace("%player%", target.getName())));
    }

    private void handleComplete(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(color(msg.get("admin.usage")));
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        QuestDef def = plugin.engine().quests().get(args[2]);
        if (target == null || def == null) {
            sender.sendMessage(color(msg.get("admin.invalid_args")));
            return;
        }
        plugin.engine().forceComplete(target, def);
        sender.sendMessage(color(msg.get("admin.completed").replace("%quest_name%", def.display.title).replace("%player%", target.getName())));
    }

    private void handleReset(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(color(msg.get("admin.usage")));
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(color(msg.get("admin.offline")));
            return;
        }
        String questId = args[2];
        plugin.engine().progress().reset(target.getUniqueId(), target.getName(), questId);
        var pd = plugin.engine().progress().get(target.getUniqueId());
        if (pd != null) {
            pd.cancel(questId);
            pd.setRepeatCount(questId, 0);
        }
        sender.sendMessage(color(msg.get("admin.reset_done").replace("%quest_name%", questId).replace("%player%", target.getName())));
    }

    private void handleList(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(color(msg.get("admin.usage")));
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(color(msg.get("admin.offline")));
            return;
        }
        plugin.engine().listActiveTo(target);
    }

    private void handlePoints(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(color(msg.get("admin.usage")));
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(color(msg.get("admin.offline")));
            return;
        }
        int pts = plugin.engine().progress().getPoints(target.getUniqueId());
        sender.sendMessage(color(msg.get("admin.points").replace("%player%", target.getName()).replace("%points%", String.valueOf(pts))));
    }

    private void handleRank(CommandSender sender) {
        sender.sendMessage(color(msg.get("admin.rank_calc")));
        CompletableFuture.runAsync(() -> {
            Map<UUID, Integer> all = plugin.engine().progress().getAllPoints();
            if (all == null || all.isEmpty()) {
                sender.sendMessage(color("7No data"));
                return;
            }

            PriorityQueue<Map.Entry<UUID, Integer>> pq = new PriorityQueue<>(11, Comparator.comparingInt(Map.Entry::getValue));
            for (Map.Entry<UUID, Integer> e : all.entrySet()) {
                pq.offer(e);
                if (pq.size() > 10) pq.poll();
            }

            List<Map.Entry<UUID, Integer>> top = new ArrayList<>(pq);
            top.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));

            StringBuilder sb = new StringBuilder();
            sb.append("§aQuest Points Ranking\n");
            int rank = 1;
            for (Map.Entry<UUID, Integer> e : top) {
                String name = "Unknown";
                OfflinePlayer off = Bukkit.getOfflinePlayer(e.getKey());
                if (off.getName() != null) name = off.getName();
                sb.append("§7#").append(rank++).append(" §f").append(name).append(" §8- §e").append(e.getValue()).append("\n");
            }
            String result = sb.toString();
            Bukkit.getScheduler().runTask(plugin, () -> sender.sendMessage(result));
        }, plugin.engine().asyncPool());
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            return StringUtil.copyPartialMatches(args[0], SUBS, new ArrayList<>());
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2 && SUBS_NEED_PLAYER.contains(sub)) {
            return null;
        }
        if (args.length == 3 && SUBS_NEED_QUEST.contains(sub)) {
            return StringUtil.copyPartialMatches(args[2], plugin.engine().quests().ids(), new ArrayList<>());
        }
        return Collections.emptyList();
    }

    private static String color(String s) {
        return s == null ? "" : ChatColor.translateAlternateColorCodes('&', s);
    }
}