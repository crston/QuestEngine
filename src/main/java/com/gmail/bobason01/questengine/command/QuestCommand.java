package com.gmail.bobason01.questengine.command;

import com.gmail.bobason01.questengine.QuestEnginePlugin;
import com.gmail.bobason01.questengine.quest.QuestDef;
import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * QuestCommand
 * - /quest 명령어로 GUI 바로 열기
 * - list/public/top GUI 포함
 * - 검색어 초기화 기능 추가
 */
public final class QuestCommand extends BaseCommand implements TabCompleter {

    private static final String SUB_START = "start";
    private static final String SUB_CANCEL = "cancel";
    private static final String SUB_LIST = "list";
    private static final String SUB_PUBLIC = "public";
    private static final String SUB_TOP = "top";
    private static final String SUB_ABANDONALL = "abandonall";
    private static final String SUB_POINTS = "points";

    private static final List<String> SUBS = Arrays.asList(
            SUB_START, SUB_CANCEL, SUB_LIST, SUB_PUBLIC, SUB_TOP, SUB_ABANDONALL, SUB_POINTS
    );

    public QuestCommand(QuestEnginePlugin plugin) {
        super(plugin);
        PluginCommand cmd = plugin.getCommand("quest");
        if (cmd != null) {
            cmd.setExecutor(this);
            cmd.setTabCompleter(this);
        } else {
            plugin.getLogger().warning("[QuestCommand] 'quest' command not found in plugin.yml");
        }
    }

    @Override
    public boolean onCommand(CommandSender s, Command c, String l, String[] a) {
        if (!(s instanceof Player p)) {
            s.sendMessage(plugin.msg().get("player_only"));
            return true;
        }

        // =============================================================
        // 기본: /quest > GUI 바로 열기 (검색어 초기화)
        // =============================================================
        if (a.length == 0) {
            plugin.gui().putSession(p, "list_search", ""); // 검색 초기화
            plugin.gui().openList(p);
            return true;
        }

        String sub = a[0].toLowerCase(Locale.ROOT);

        switch (sub) {
            case SUB_START -> {
                if (a.length < 2) {
                    p.sendMessage(plugin.msg().get("invalid_args"));
                    return true;
                }
                QuestDef q = plugin.engine().quests().get(a[1]);
                if (q == null) {
                    p.sendMessage(plugin.msg().get("list_empty"));
                    return true;
                }
                plugin.engine().startQuest(p, q);
                return true;
            }

            case SUB_CANCEL -> {
                if (a.length < 2) {
                    p.sendMessage(plugin.msg().get("invalid_args"));
                    return true;
                }
                QuestDef q = plugin.engine().quests().get(a[1]);
                if (q == null) {
                    p.sendMessage(plugin.msg().get("list_empty"));
                    return true;
                }
                plugin.engine().cancelQuest(p, q);
                return true;
            }

            // =============================================================
            // /quest list > 목록 GUI (검색 초기화)
            // =============================================================
            case SUB_LIST -> {
                plugin.gui().putSession(p, "list_search", ""); // 검색 초기화
                plugin.gui().openList(p);
                return true;
            }

            // =============================================================
            // /quest public > 공개 퀘스트 GUI
            // =============================================================
            case SUB_PUBLIC -> {
                plugin.gui().openPublic(p);
                return true;
            }

            // =============================================================
            // /quest top > 리더보드 GUI
            // =============================================================
            case SUB_TOP -> {
                plugin.gui().openLeaderboard(p);
                return true;
            }

            case SUB_ABANDONALL -> {
                plugin.engine().abandonAll(p);
                p.sendMessage(plugin.msg().get("abandon_all_done"));
                return true;
            }

            case SUB_POINTS -> {
                showPoints(p);
                return true;
            }

            default -> {
                p.sendMessage(plugin.msg().get("invalid_args"));
                return true;
            }
        }
    }

    private void showPoints(Player p) {
        CompletableFuture
                .supplyAsync(() -> plugin.engine().progress().getPoints(p.getUniqueId()), plugin.engine().asyncPool())
                .thenAccept(points -> Bukkit.getScheduler().runTask(plugin, () -> {
                    String msg = plugin.msg().get("list_header") + "§f "
                            + plugin.msg().get("list.points").replace("%points%", String.valueOf(points));
                    p.sendMessage(msg);
                }));
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String l, String[] a) {
        int len = a.length;
        if (len == 0) return Collections.emptyList();

        if (len == 1) {
            String prefix = a[0].toLowerCase(Locale.ROOT);
            List<String> out = new ArrayList<>(SUBS.size());
            for (String sub : SUBS) {
                if (sub.startsWith(prefix)) out.add(sub);
            }
            return out;
        }

        if (len == 2 && (SUB_START.equalsIgnoreCase(a[0]) || SUB_CANCEL.equalsIgnoreCase(a[0]))) {
            String prefix = a[1].toLowerCase(Locale.ROOT);
            Collection<String> ids = plugin.engine().quests().ids();
            List<String> out = new ArrayList<>(Math.max(8, ids.size()));
            for (String id : ids) {
                if (id != null && id.toLowerCase(Locale.ROOT).startsWith(prefix)) out.add(id);
            }
            return out;
        }

        return Collections.emptyList();
    }
}
