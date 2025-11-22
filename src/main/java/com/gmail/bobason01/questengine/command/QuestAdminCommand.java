package com.gmail.bobason01.questengine.command;

import com.gmail.bobason01.questengine.QuestEnginePlugin;
import com.gmail.bobason01.questengine.quest.QuestDef;
import com.gmail.bobason01.questengine.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

public final class QuestAdminCommand extends BaseCommand implements TabCompleter {

    private static final String SUB_RELOAD = "reload";
    private static final String SUB_GIVE = "give";
    private static final String SUB_STOP = "stop";
    private static final String SUB_COMPLETE = "complete";
    private static final String SUB_RESET = "reset";
    private static final String SUB_LIST = "list";
    private static final String SUB_POINTS = "points";
    private static final String SUB_RANK = "rank";

    private static final List<String> SUBS = Arrays.asList(
            SUB_RELOAD,
            SUB_GIVE,
            SUB_STOP,
            SUB_COMPLETE,
            SUB_RESET,
            SUB_LIST,
            SUB_POINTS,
            SUB_RANK
    );

    private static final Set<String> SUBS_NEED_PLAYER = new HashSet<>(Arrays.asList(
            SUB_GIVE,
            SUB_STOP,
            SUB_COMPLETE,
            SUB_RESET,
            SUB_LIST,
            SUB_POINTS
    ));

    private static final Set<String> SUBS_NEED_QUEST = new HashSet<>(Arrays.asList(
            SUB_GIVE,
            SUB_STOP,
            SUB_COMPLETE,
            SUB_RESET
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
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(color(msg.get("admin.usage")));
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case SUB_RELOAD -> {
                handleReload(sender);
                return true;
            }
            case SUB_GIVE -> {
                handleGive(sender, args);
                return true;
            }
            case SUB_STOP -> {
                handleStop(sender, args);
                return true;
            }
            case SUB_COMPLETE -> {
                handleComplete(sender, args);
                return true;
            }
            case SUB_RESET -> {
                handleReset(sender, args);
                return true;
            }
            case SUB_LIST -> {
                handleList(sender, args);
                return true;
            }
            case SUB_POINTS -> {
                handlePoints(sender, args);
                return true;
            }
            case SUB_RANK -> {
                handleRank(sender);
                return true;
            }
            default -> {
                sender.sendMessage(color(msg.get("admin.usage")));
                return true;
            }
        }
    }

    private void handleReload(CommandSender sender) {
        try {
            plugin.msg().reload();
            plugin.engine().quests().reload();
            plugin.engine().quests().rebuildEventMap();
            plugin.engine().refreshEventCache();

            sender.sendMessage(color("&a[QuestEngine] Reload complete. Messages and quests reloaded."));
            plugin.getLogger().info("[QuestEngine] Reload complete. Messages and quests reloaded.");
        } catch (Throwable t) {
            sender.sendMessage(color("&c[QuestEngine] Reload failed: " + t.getMessage()));
            plugin.getLogger().severe("[QuestEngine] Reload failed: " + t.getMessage());
            t.printStackTrace();
        }
    }

    private Player findOnlinePlayer(String name) {
        if (name == null) return null;
        Player p = Bukkit.getPlayerExact(name);
        if (p != null) return p;
        return Bukkit.getPlayer(name);
    }

    private void handleGive(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(color(msg.get("admin.usage")));
            return;
        }

        Player target = findOnlinePlayer(args[1]);
        QuestDef def = plugin.engine().quests().get(args[2]);

        if (target == null || def == null) {
            sender.sendMessage(color(msg.get("admin.invalid_args")));
            return;
        }

        plugin.engine().startQuest(target, def);

        String m = msg.get("admin.started");
        if (m == null) m = "&aStarted quest %quest_name% for %player%";
        sender.sendMessage(color(
                m.replace("%quest_name%", def.display.title)
                        .replace("%player%", target.getName())
        ));
    }

    private void handleStop(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(color(msg.get("admin.usage")));
            return;
        }

        Player target = findOnlinePlayer(args[1]);
        QuestDef def = plugin.engine().quests().get(args[2]);

        if (target == null || def == null) {
            sender.sendMessage(color(msg.get("admin.invalid_args")));
            return;
        }

        plugin.engine().stopQuest(target, def);

        String m = msg.get("admin.stopped");
        if (m == null) m = "&cStopped quest %quest_name% for %player%";
        sender.sendMessage(color(
                m.replace("%quest_name%", def.display.title)
                        .replace("%player%", target.getName())
        ));
    }

    private void handleComplete(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(color(msg.get("admin.usage")));
            return;
        }

        Player target = findOnlinePlayer(args[1]);
        QuestDef def = plugin.engine().quests().get(args[2]);

        if (target == null || def == null) {
            sender.sendMessage(color(msg.get("admin.invalid_args")));
            return;
        }

        plugin.engine().forceComplete(target, def);

        String m = msg.get("admin.completed");
        if (m == null) m = "&bCompleted quest %quest_name% for %player%";
        sender.sendMessage(color(
                m.replace("%quest_name%", def.display.title)
                        .replace("%player%", target.getName())
        ));
    }

    private void handleReset(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(color(msg.get("admin.usage")));
            return;
        }

        Player target = findOnlinePlayer(args[1]);
        if (target == null) {
            sender.sendMessage(color(msg.get("admin.offline")));
            return;
        }

        String questId = args[2];

        // 1. Storage 삭제
        plugin.engine().progress().reset(target.getUniqueId(), target.getName(), questId);

        // 2. 메모리 캐시 초기화
        var pd = plugin.engine().progress().get(target.getUniqueId());
        pd.cancel(questId);
        pd.setRepeatCount(questId, 0);

        String m = msg.get("admin.reset_done");
        if (m == null) m = "&7Reset quest %quest_name% for %player%";
        sender.sendMessage(color(
                m.replace("%quest_name%", questId)
                        .replace("%player%", target.getName())
        ));
    }

    private void handleList(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(color(msg.get("admin.usage")));
            return;
        }

        Player target = findOnlinePlayer(args[1]);
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

        Player target = findOnlinePlayer(args[1]);
        if (target == null) {
            sender.sendMessage(color(msg.get("admin.offline")));
            return;
        }

        int pts = plugin.engine().progress().getPoints(target.getUniqueId());

        String m = msg.get("admin.points");
        if (m == null) m = "&e%player% Quest Points: %points%";
        sender.sendMessage(color(
                m.replace("%player%", target.getName())
                        .replace("%points%", Integer.toString(pts))
        ));
    }

    private void handleRank(CommandSender sender) {
        sender.sendMessage(color(msg.get("admin.rank_calc")));

        CompletableFuture.runAsync(() -> {
            Map<UUID, Integer> all = plugin.engine().progress().getAllPoints();
            if (all == null || all.isEmpty()) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    sender.sendMessage(color("&7No data"));
                });
                return;
            }

            int limit = 10;

            PriorityQueue<Map.Entry<UUID, Integer>> pq =
                    new PriorityQueue<>(limit, Comparator.comparingInt(Map.Entry::getValue));

            for (Map.Entry<UUID, Integer> e : all.entrySet()) {
                if (pq.size() < limit) {
                    pq.offer(e);
                } else if (e.getValue() > pq.peek().getValue()) {
                    pq.poll();
                    pq.offer(e);
                }
            }

            List<Map.Entry<UUID, Integer>> top = new ArrayList<>(pq);
            top.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));

            StringBuilder sb = new StringBuilder(128);
            sb.append("§a[Quest Points Ranking]\n");
            AtomicInteger rank = new AtomicInteger(1);

            for (Map.Entry<UUID, Integer> e : top) {
                UUID id = e.getKey();
                int points = e.getValue();

                String name;
                Player online = Bukkit.getPlayer(id);
                if (online != null) {
                    name = online.getName();
                } else {
                    OfflinePlayer off = Bukkit.getOfflinePlayer(id);
                    if (off != null && off.getName() != null) {
                        name = off.getName();
                    } else {
                        name = "Unknown";
                    }
                }

                sb.append("§7#")
                        .append(rank.getAndIncrement())
                        .append(" §f")
                        .append(name)
                        .append(" §8- §e")
                        .append(points)
                        .append("\n");
            }

            Bukkit.getScheduler().runTask(plugin, () -> sender.sendMessage(sb.toString()));
        }, plugin.engine().asyncPool());
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        int len = args.length;
        if (len == 0) return Collections.emptyList();

        if (len == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            List<String> out = new ArrayList<>();
            for (String sub : SUBS) {
                if (sub.startsWith(prefix)) {
                    out.add(sub);
                }
            }
            return out;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);

        if (len == 2 && SUBS_NEED_PLAYER.contains(sub)) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            List<String> out = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) {
                String name = p.getName();
                if (name.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                    out.add(name);
                }
            }
            return out;
        }

        if (len == 3 && SUBS_NEED_QUEST.contains(sub)) {
            String prefix = args[2].toLowerCase(Locale.ROOT);
            List<String> out = new ArrayList<>();
            for (String id : plugin.engine().quests().ids()) {
                if (id.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                    out.add(id);
                }
            }
            return out;
        }

        return Collections.emptyList();
    }

    private static String color(String s) {
        if (s == null) return "";
        return ChatColor.translateAlternateColorCodes('&', s);
    }
}
