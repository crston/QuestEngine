package com.gmail.bobason01.questengine.command;

import com.gmail.bobason01.questengine.QuestEnginePlugin;
import com.gmail.bobason01.questengine.quest.QuestDef;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;

import java.util.*;
import java.util.concurrent.CompletableFuture;

public final class QuestCommand extends BaseCommand {

    private static final List<String> SUBS = Arrays.asList(
            "start", "cancel", "list", "public", "top", "abandonall", "points"
    );

    public QuestCommand(QuestEnginePlugin plugin) {
        super(plugin);
        PluginCommand cmd = plugin.getCommand("quest");
        if (cmd != null) {
            cmd.setExecutor(this);
            cmd.setTabCompleter(this);
        }
    }

    @Override
    public boolean onCommand(CommandSender s, Command c, String l, String[] a) {
        if (!(s instanceof Player p)) {
            s.sendMessage(plugin.msg().get("player_only"));
            return true;
        }

        if (a.length == 0) {
            plugin.gui().putSession(p, "list_search", "");
            plugin.gui().openList(p);
            return true;
        }

        String sub = a[0].toLowerCase(Locale.ROOT);

        switch (sub) {
            case "start" -> {
                if (a.length < 2) return msg(p, "invalid_args");
                QuestDef q = plugin.engine().quests().get(a[1]);
                if (q == null) return msg(p, "list_empty");
                plugin.engine().startQuest(p, q);
            }
            case "cancel" -> {
                if (a.length < 2) return msg(p, "invalid_args");
                QuestDef q = plugin.engine().quests().get(a[1]);
                if (q == null) return msg(p, "list_empty");
                plugin.engine().cancelQuest(p, q);
            }
            case "list" -> {
                plugin.gui().putSession(p, "list_search", "");
                plugin.gui().openList(p);
            }
            case "public" -> plugin.gui().openPublic(p);
            case "top" -> plugin.gui().openLeaderboard(p);
            case "abandonall" -> {
                plugin.engine().abandonAll(p);
                p.sendMessage(plugin.msg().get("abandon_all_done"));
            }
            case "points" -> showPoints(p);
            default -> msg(p, "invalid_args");
        }
        return true;
    }

    private boolean msg(Player p, String key) {
        p.sendMessage(plugin.msg().get(key));
        return true;
    }

    private void showPoints(Player p) {
        CompletableFuture.supplyAsync(() -> plugin.engine().progress().getPoints(p.getUniqueId()), plugin.engine().asyncPool())
                .thenAccept(points -> Bukkit.getScheduler().runTask(plugin, () ->
                        p.sendMessage(plugin.msg().get("list_header") + "§f " +
                                plugin.msg().get("list.points").replace("%points%", String.valueOf(points)))
                ));
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String l, String[] a) {
        if (a.length == 1) {
            return StringUtil.copyPartialMatches(a[0], SUBS, new ArrayList<>(SUBS.size()));
        }
        if (a.length == 2 && ("start".equalsIgnoreCase(a[0]) || "cancel".equalsIgnoreCase(a[0]))) {
            Collection<String> ids = plugin.engine().quests().ids();
            return StringUtil.copyPartialMatches(a[1], ids, new ArrayList<>(ids.size()));
        }
        return Collections.emptyList();
    }
}