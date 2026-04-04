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
        // 권한 체크 추가 (관리자 명령어이므로 필수)
        if (!sender.hasPermission("questengine.admin")) {
            sender.sendMessage(ChatColor.RED + "권한이 없습니다.");
            return true;
        }

        if (args.length == 0) {
            sendUsage(sender);
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
            default -> sendUsage(sender);
        }
        return true;
    }

    private void sendUsage(CommandSender sender) {
        // CommandSender가 플레이어라면 플레이어 언어로, 아니면 시스템 기본 언어로 출력
        if (sender instanceof Player p) {
            sender.sendMessage(plugin.msg().get(p, "admin.usage"));
        } else {
            sender.sendMessage(plugin.msg().get("admin.usage"));
        }
    }

    private void handleReload(CommandSender sender) {
        try {
            plugin.reloadAll();

            String msgText = "&aReload complete!";
            if (sender instanceof Player p) {
                sender.sendMessage(plugin.msg().pref(p, msgText));
            } else {
                sender.sendMessage(plugin.msg().pref(msgText));
            }
        } catch (Throwable t) {
            sender.sendMessage(ChatColor.RED + "[QuestEngine] Reload failed: " + t.getMessage());
            t.printStackTrace();
        }
    }

    private void handleGive(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sendUsage(sender);
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        QuestDef def = plugin.quests().get(args[2]); // engine().quests() 대신 quests()로 직접 접근 가능

        if (target == null || def == null) {
            sender.sendMessage(getMessage(sender, "admin.invalid_args"));
            return;
        }
        plugin.engine().startQuest(target, def);

        String msgStr = getMessage(sender, "admin.started")
                .replace("%quest_name%", def.display.title != null ? def.display.title : def.id)
                .replace("%player%", target.getName());
        sender.sendMessage(msgStr);
    }

    private void handleStop(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sendUsage(sender);
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        QuestDef def = plugin.quests().get(args[2]);
        if (target == null || def == null) {
            sender.sendMessage(getMessage(sender, "admin.invalid_args"));
            return;
        }
        plugin.engine().stopQuest(target, def);
        sender.sendMessage(getMessage(sender, "admin.stopped")
                .replace("%quest_name%", def.display.title).replace("%player%", target.getName()));
    }

    private void handleComplete(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sendUsage(sender);
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        QuestDef def = plugin.quests().get(args[2]);
        if (target == null || def == null) {
            sender.sendMessage(getMessage(sender, "admin.invalid_args"));
            return;
        }
        plugin.engine().forceComplete(target, def);
        sender.sendMessage(getMessage(sender, "admin.completed")
                .replace("%quest_name%", def.display.title).replace("%player%", target.getName()));
    }

    private void handleReset(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sendUsage(sender);
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(getMessage(sender, "admin.offline"));
            return;
        }
        String questId = args[2];
        plugin.progress().reset(target.getUniqueId(), target.getName(), questId);

        var pd = plugin.progress().get(target.getUniqueId());
        if (pd != null) {
            pd.cancel(questId);
            pd.setRepeatCount(questId, 0);
        }
        sender.sendMessage(getMessage(sender, "admin.reset_done")
                .replace("%quest_name%", questId).replace("%player%", target.getName()));
    }

    private void handleList(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sendUsage(sender);
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(getMessage(sender, "admin.offline"));
            return;
        }
        plugin.engine().listActiveTo(target);
    }

    private void handlePoints(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sendUsage(sender);
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(getMessage(sender, "admin.offline"));
            return;
        }
        int pts = plugin.progress().getPoints(target.getUniqueId());
        sender.sendMessage(getMessage(sender, "admin.points")
                .replace("%player%", target.getName()).replace("%points%", String.valueOf(pts)));
    }

    private void handleRank(CommandSender sender) {
        sender.sendMessage(getMessage(sender, "admin.rank_calc"));
        CompletableFuture.runAsync(() -> {
            Map<UUID, Integer> all = plugin.progress().getAllPoints();
            if (all == null || all.isEmpty()) {
                sender.sendMessage(ChatColor.GRAY + "데이터가 없습니다.");
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
            sb.append("§a[Quest Points Ranking]\n");
            int rankNum = 1;
            for (Map.Entry<UUID, Integer> e : top) {
                String name = "Unknown";
                OfflinePlayer off = Bukkit.getOfflinePlayer(e.getKey());
                if (off.getName() != null) name = off.getName();
                sb.append("§7#").append(rankNum++).append(" §f").append(name).append(" §8- §e").append(e.getValue()).append("\n");
            }
            String result = sb.toString();
            Bukkit.getScheduler().runTask(plugin, () -> sender.sendMessage(result));
        }, plugin.asyncPool());
    }

    /**
     * 상황에 맞는 메시지를 가져오는 헬퍼 메서드
     */
    private String getMessage(CommandSender sender, String path) {
        if (sender instanceof Player p) {
            return plugin.msg().get(p, path);
        }
        return plugin.msg().get(path);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            return StringUtil.copyPartialMatches(args[0], SUBS, new ArrayList<>());
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2 && SUBS_NEED_PLAYER.contains(sub)) {
            return null; // 플레이어 목록 자동 완성
        }
        if (args.length == 3 && SUBS_NEED_QUEST.contains(sub)) {
            return StringUtil.copyPartialMatches(args[2], plugin.quests().ids(), new ArrayList<>());
        }
        return Collections.emptyList();
    }
}