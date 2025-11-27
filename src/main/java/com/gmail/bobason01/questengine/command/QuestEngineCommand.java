package com.gmail.bobason01.questengine.command;

import com.gmail.bobason01.questengine.QuestEnginePlugin;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.util.StringUtil;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class QuestEngineCommand extends BaseCommand {

    private static final List<String> SUBS = Arrays.asList("ping", "cache", "papi", "version");

    public QuestEngineCommand(QuestEnginePlugin plugin) {
        super(plugin);
        PluginCommand cmd = plugin.getCommand("questengine");
        if (cmd != null) {
            cmd.setExecutor(this);
            cmd.setTabCompleter(this);
        }
    }

    @Override
    public boolean onCommand(CommandSender s, Command c, String l, String[] a) {
        if (a.length == 0) {
            s.sendMessage("§e/questengine ping|cache|papi|version");
            return true;
        }

        switch (a[0].toLowerCase(Locale.ROOT)) {
            case "ping" -> s.sendMessage("§aQuestEngine active");
            case "cache" -> s.sendMessage("§eCached players: §f" + plugin.engine().progress().cacheSize());
            case "papi" -> s.sendMessage("§ePlaceholderAPI: §f" + (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")));
            case "version" -> s.sendMessage("§eVersion: §f" + plugin.getDescription().getVersion());
            default -> s.sendMessage("§e/questengine ping|cache|papi|version");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            return StringUtil.copyPartialMatches(args[0], SUBS, new java.util.ArrayList<>(SUBS.size()));
        }
        return Collections.emptyList();
    }
}