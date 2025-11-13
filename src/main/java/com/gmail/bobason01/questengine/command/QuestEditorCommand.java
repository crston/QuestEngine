package com.gmail.bobason01.questengine.command;

import com.gmail.bobason01.questengine.QuestEnginePlugin;
import com.gmail.bobason01.questengine.gui.editor.QuestEditorMenu;
import com.gmail.bobason01.questengine.quest.QuestDef;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class QuestEditorCommand extends BaseCommand implements TabCompleter {

    private final QuestEditorMenu menu;
    private final QuestEnginePlugin plugin;

    public QuestEditorCommand(QuestEnginePlugin plugin, QuestEditorMenu menu) {
        super(plugin);
        this.plugin = plugin;
        this.menu = menu;

        PluginCommand cmd = plugin.getCommand("questeditor");
        if (cmd != null) {
            cmd.setExecutor(this);
            cmd.setTabCompleter(this);
        } else {
            plugin.getLogger().warning("[QuestEditor] questeditor command not found in plugin.yml");
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (!(sender instanceof Player player)) {
            sender.sendMessage("Player only");
            return true;
        }

        // ---------- 기본 사용법 출력 ----------
        if (args.length == 0) {
            player.sendMessage("§e/questeditor create <id>");
            player.sendMessage("§e/questeditor edit <id>");
            player.sendMessage("§e/questeditor list");
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);

        // ---------- CREATE ----------
        if (sub.equals("create")) {
            if (args.length < 2) {
                player.sendMessage("§cUsage: /questeditor create <id>");
                return true;
            }

            String id = args[1].toLowerCase(Locale.ROOT);

            if (plugin.quests().get(id) != null) {
                player.sendMessage("§cQuest already exists: §f" + id);
                return true;
            }

            menu.openNewWithId(player, id);
            return true;
        }

        // ---------- LIST ----------
        if (sub.equals("list")) {
            menu.openListSelection(player, 1);  // 페이지 1부터
            return true;
        }

        // ---------- EDIT ----------
        if (sub.equals("edit")) {
            if (args.length < 2) {
                player.sendMessage("§cUsage: /questeditor edit <id>");
                return true;
            }

            String id = args[1].toLowerCase(Locale.ROOT);
            QuestDef def = plugin.quests().get(id);

            if (def == null) {
                player.sendMessage("§cQuest not found: §f" + id);
                return true;
            }

            menu.openEdit(player, def);
            return true;
        }

        // ---------- 잘못된 입력 ----------
        player.sendMessage("§e/questeditor create <id>");
        player.sendMessage("§e/questeditor edit <id>");
        player.sendMessage("§e/questeditor list");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {

        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            List<String> out = new ArrayList<>();
            if ("create".startsWith(prefix)) out.add("create");
            if ("edit".startsWith(prefix)) out.add("edit");
            if ("list".startsWith(prefix)) out.add("list");
            return out;
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("edit")) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            List<String> out = new ArrayList<>();
            for (String id : plugin.quests().ids()) {
                if (id.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                    out.add(id);
                }
            }
            return out;
        }

        return Collections.emptyList();
    }
}
