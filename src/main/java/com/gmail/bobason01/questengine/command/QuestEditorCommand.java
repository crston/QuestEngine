package com.gmail.bobason01.questengine.command;

import com.gmail.bobason01.questengine.QuestEnginePlugin;
import com.gmail.bobason01.questengine.gui.editor.QuestEditorMenu;
import com.gmail.bobason01.questengine.quest.QuestDef;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;

import java.io.File;
import java.util.*;

public final class QuestEditorCommand extends BaseCommand {

    private final QuestEditorMenu menu;
    private static final List<String> SUBS = Arrays.asList("create", "edit", "list", "delete");

    public QuestEditorCommand(QuestEnginePlugin plugin, QuestEditorMenu menu) {
        super(plugin);
        this.menu = menu;
        PluginCommand cmd = plugin.getCommand("questeditor");
        if (cmd != null) {
            cmd.setExecutor(this);
            cmd.setTabCompleter(this);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Player only");
            return true;
        }

        if (args.length == 0) {
            sendHelp(player);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);

        switch (sub) {
            case "create" -> {
                if (args.length < 2) {
                    player.sendMessage("§cUsage /questeditor create id");
                    return true;
                }
                String id = args[1].toLowerCase(Locale.ROOT);
                if (plugin.quests().get(id) != null) {
                    player.sendMessage("§cQuest already exists §f" + id);
                    return true;
                }
                menu.openNewWithId(player, id);
            }
            case "list" -> menu.openListSelection(player, 1);
            case "edit" -> {
                if (args.length < 2) {
                    player.sendMessage("§cUsage /questeditor edit id");
                    return true;
                }
                String id = args[1].toLowerCase(Locale.ROOT);
                QuestDef def = plugin.quests().get(id);
                if (def == null) {
                    player.sendMessage("§cQuest not found §f" + id);
                    return true;
                }
                menu.openEdit(player, def);
            }
            case "delete" -> handleDelete(player, args);
            default -> sendHelp(player);
        }
        return true;
    }

    private void handleDelete(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§cUsage /questeditor delete id");
            return;
        }

        String questId = args[1].toLowerCase(Locale.ROOT);
        QuestDef def = plugin.quests().get(questId);

        if (def == null) {
            player.sendMessage(plugin.msg().get(player, "admin.delete_fail_not_found").replace("%quest_name%", questId));
            return;
        }

        try {
            File folder = new File(plugin.getDataFolder(), plugin.getConfig().getString("quests.folder", "quests"));
            File file = new File(folder, def.id + ".yml");

            if (file.exists()) {
                if (file.delete()) {
                    plugin.quests().reload();
                    player.sendMessage(plugin.msg().get(player, "admin.delete_done").replace("%quest_name%", def.id));
                } else {
                    player.sendMessage(plugin.msg().get(player, "admin.delete_fail_error"));
                }
            } else {
                player.sendMessage(plugin.msg().get(player, "admin.delete_fail_not_found").replace("%quest_name%", questId));
            }
        } catch (Exception e) {
            player.sendMessage(plugin.msg().get(player, "admin.delete_fail_error"));
            e.printStackTrace();
        }
    }

    private void sendHelp(Player p) {
        p.sendMessage("§e/questeditor create id");
        p.sendMessage("§e/questeditor edit id");
        p.sendMessage("§e/questeditor list");
        p.sendMessage("§e/questeditor delete id");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return StringUtil.copyPartialMatches(args[0], SUBS, new ArrayList<>(SUBS.size()));
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("edit") || args[0].equalsIgnoreCase("delete"))) {
            Collection<String> ids = plugin.quests().ids();
            return StringUtil.copyPartialMatches(args[1], ids, new ArrayList<>(ids.size()));
        }
        return Collections.emptyList();
    }
}