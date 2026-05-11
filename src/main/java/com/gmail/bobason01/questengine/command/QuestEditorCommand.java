package com.gmail.bobason01.questengine.command;

import com.gmail.bobason01.questengine.QuestEnginePlugin;
import com.gmail.bobason01.questengine.gui.editor.QuestEditorMenu;
import com.gmail.bobason01.questengine.quest.QuestDef;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;

import java.io.File;
import java.lang.reflect.Method;
import java.util.*;

public final class QuestEditorCommand extends BaseCommand {

    private final QuestEditorMenu menu;
    private static final List<String> SUBS = Arrays.asList("create", "edit", "list", "delete", "debug");

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
            case "debug" -> handleDebug(player, args);
            default -> sendHelp(player);
        }
        return true;
    }

    private void handleDebug(Player sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§cUsage /questeditor debug id [player]");
            return;
        }

        String questId = args[1].toLowerCase(Locale.ROOT);
        QuestDef def = plugin.quests().get(questId);

        if (def == null) {
            sender.sendMessage("§cQuest not found §f" + questId);
            return;
        }

        if (def.custom == null || def.custom.captures == null || def.custom.captures.isEmpty()) {
            sender.sendMessage("§cThis quest does not have variables_to_capture configured");
            return;
        }

        Player target = sender;
        if (args.length >= 3) {
            Player p = Bukkit.getPlayer(args[2]);
            if (p != null && p.isOnline()) {
                target = p;
            } else {
                sender.sendMessage("§cTarget player is not online");
                return;
            }
        }

        sender.sendMessage("§e=== Variables Debug for " + def.name + " §e===");
        sender.sendMessage("§7Target Player : §f" + target.getName());

        for (Map.Entry<String, String> entry : def.custom.captures.entrySet()) {
            String key = entry.getKey();
            String path = entry.getValue();

            Object result = evalChain(target, path);
            String resultStr = (result != null) ? "§a" + result.toString() : "§cnull §7(Event path or invalid)";

            sender.sendMessage("§f- §b%" + key + "% §7(Path: " + path + ") -> " + resultStr);
        }
    }

    private Object evalChain(Object root, String chain) {
        if (root == null || chain == null || chain.isEmpty()) return null;
        Object current = root;
        try {
            for (String part : chain.split("\\.")) {
                if (current == null) return null;
                String name = part.trim().replace("()", "");
                Method m = findNoArgMethod(current.getClass(), name);
                if (m == null) return null;
                m.setAccessible(true);
                current = m.invoke(current);
            }
            return current;
        } catch (Throwable t) {
            return null;
        }
    }

    private Method searchMethod(Class<?> type, String name) {
        for (Method m : type.getMethods()) {
            if (m.getName().equals(name) && m.getParameterCount() == 0) return m;
        }
        Class<?> curr = type;
        while (curr != null && curr != Object.class) {
            for (Method m : curr.getDeclaredMethods()) {
                if (m.getName().equals(name) && m.getParameterCount() == 0) return m;
            }
            curr = curr.getSuperclass();
        }
        return null;
    }

    private Method findNoArgMethod(Class<?> type, String name) {
        Method m = searchMethod(type, name);
        if (m != null) return m;

        String cap = Character.toUpperCase(name.charAt(0)) + name.substring(1);
        m = searchMethod(type, "get" + cap);
        if (m != null) return m;

        m = searchMethod(type, "is" + cap);
        return m;
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
        p.sendMessage("§e/questeditor debug id [player]");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return StringUtil.copyPartialMatches(args[0], SUBS, new ArrayList<>(SUBS.size()));
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("edit") || args[0].equalsIgnoreCase("delete") || args[0].equalsIgnoreCase("debug"))) {
            Collection<String> ids = plugin.quests().ids();
            return StringUtil.copyPartialMatches(args[1], ids, new ArrayList<>(ids.size()));
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("debug")) {
            return null;
        }
        return Collections.emptyList();
    }
}