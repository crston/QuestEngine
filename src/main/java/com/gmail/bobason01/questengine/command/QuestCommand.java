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
            "start", "cancel", "list", "public", "top", "abandonall", "points", "lang"
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
            s.sendMessage(plugin.msg().getRaw("en", "player_only"));
            return true;
        }

        if (a.length == 0) {
            plugin.gui().putSession(p, "list_search", "");
            plugin.gui().openList(p, 0);
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
                plugin.gui().openList(p, 0);
            }
            case "public" -> plugin.gui().openPublic(p, 0);
            case "top" -> plugin.gui().openLeaderboard(p);
            case "abandonall" -> {
                plugin.engine().abandonAll(p);
                p.sendMessage(plugin.msg().get(p, "abandon_all_done"));
            }
            case "points" -> showPoints(p);
            case "lang" -> handleLang(p, a);
            default -> msg(p, "invalid_args");
        }
        return true;
    }

    private void handleLang(Player p, String[] a) {
        if (a.length < 2) {
            plugin.gui().openLanguageMenu(p);
            return;
        }

        String targetLang = a[1].toLowerCase(Locale.ROOT);
        if (plugin.msg().getAvailableLanguages().contains(targetLang)) {
            var data = plugin.progress().of(p.getUniqueId(), p.getName());
            data.setLanguage(targetLang);
            plugin.progress().save(data);

            p.sendMessage(plugin.msg().get(p, "language_changed").replace("%lang%", targetLang.toUpperCase()));
        } else {
            p.sendMessage("§cInvalid language");
        }
    }

    private boolean msg(Player p, String key) {
        p.sendMessage(plugin.msg().get(p, key));
        return true;
    }

    private void showPoints(Player p) {
        CompletableFuture.supplyAsync(() -> plugin.progress().getPoints(p.getUniqueId()), plugin.engine().asyncPool())
                .thenAccept(points -> Bukkit.getScheduler().runTask(plugin, () ->
                        p.sendMessage(plugin.msg().get(p, "list_header") + "\n" +
                                plugin.msg().get(p, "gui.list.points").replace("%points%", String.valueOf(points)))
                ));
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String l, String[] a) {
        if (a.length == 1) {
            return StringUtil.copyPartialMatches(a[0], SUBS, new ArrayList<>());
        }
        if (a.length == 2) {
            String sub = a[0].toLowerCase(Locale.ROOT);
            if (sub.equals("start") || sub.equals("cancel")) {
                return StringUtil.copyPartialMatches(a[1], plugin.engine().quests().ids(), new ArrayList<>());
            }
            if (sub.equals("lang")) {
                return StringUtil.copyPartialMatches(a[1], plugin.msg().getAvailableLanguages(), new ArrayList<>());
            }
        }
        return Collections.emptyList();
    }
}