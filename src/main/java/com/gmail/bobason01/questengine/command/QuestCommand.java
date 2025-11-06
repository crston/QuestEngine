package com.gmail.bobason01.questengine.command;

import com.gmail.bobason01.questengine.QuestEnginePlugin;
import com.gmail.bobason01.questengine.quest.QuestDef;
import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.CompletableFuture;

public final class QuestCommand extends BaseCommand implements TabCompleter {

    private static final String SUB_START = "start";
    private static final String SUB_CANCEL = "cancel";
    private static final String SUB_LIST = "list";
    private static final String SUB_ABANDONALL = "abandonall";
    private static final String SUB_POINTS = "points";

    private static final List<String> SUBS = Arrays.asList(
            SUB_START, SUB_CANCEL, SUB_LIST, SUB_ABANDONALL, SUB_POINTS
    );

    public QuestCommand(QuestEnginePlugin plugin) {
        super(plugin);
        PluginCommand cmd = plugin.getCommand("quest");
        if (cmd != null) {
            cmd.setExecutor(this);
            cmd.setTabCompleter(this);
        } else {
            plugin.getLogger().warning("quest command not found in plugin.yml");
        }
    }

    @Override
    public boolean onCommand(CommandSender s, Command c, String l, String[] a) {
        if (!(s instanceof Player p)) {
            s.sendMessage("player only");
            return true;
        }

        if (a.length == 0) {
            plugin.engine().listActiveTo(p);
            return true;
        }

        String sub = a[0].toLowerCase(Locale.ROOT);
        if (SUB_START.equals(sub)) {
            if (a.length < 2) {
                p.sendMessage("/quest start <id>");
                return true;
            }
            QuestDef q = plugin.engine().quests().get(a[1]);
            if (q == null) {
                p.sendMessage("unknown quest");
                return true;
            }
            plugin.engine().startQuest(p, q);
            return true;
        }

        if (SUB_CANCEL.equals(sub)) {
            if (a.length < 2) {
                p.sendMessage("/quest cancel <id>");
                return true;
            }
            QuestDef q = plugin.engine().quests().get(a[1]);
            if (q == null) {
                p.sendMessage("unknown quest");
                return true;
            }
            plugin.engine().cancelQuest(p, q);
            return true;
        }

        if (SUB_LIST.equals(sub)) {
            plugin.engine().listActiveTo(p);
            return true;
        }

        if (SUB_ABANDONALL.equals(sub)) {
            plugin.engine().abandonAll(p);
            return true;
        }

        if (SUB_POINTS.equals(sub)) {
            showPoints(p);
            return true;
        }

        p.sendMessage("/quest <start|cancel|list|abandonall|points>");
        return true;
    }

    private void showPoints(Player p) {
        // 완전 비동기 캐싱 + 스케줄러 최소화
        CompletableFuture.supplyAsync(() -> plugin.engine().progress().getPoints(p.getUniqueId()), plugin.engine().asyncPool())
                .thenAcceptAsync(points -> {
                    StringBuilder sb = new StringBuilder(48);
                    sb.append("§e[Quest Points]§f 당신의 퀘스트 포인트: §a").append(points);
                    p.sendMessage(sb.toString());
                }, r -> Bukkit.getScheduler().runTask(plugin, r));
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String l, String[] a) {
        int len = a.length;
        if (len == 0) return Collections.emptyList();

        if (len == 1) {
            String prefix = a[0].toLowerCase(Locale.ROOT);
            List<String> out = new ArrayList<>(SUBS.size());
            for (int i = 0; i < SUBS.size(); i++) {
                String sub = SUBS.get(i);
                if (sub.startsWith(prefix)) out.add(sub);
            }
            return out;
        }

        if (len == 2 && (SUB_START.equalsIgnoreCase(a[0]) || SUB_CANCEL.equalsIgnoreCase(a[0]))) {
            String prefix = a[1].toLowerCase(Locale.ROOT);
            Collection<String> ids = plugin.engine().quests().ids();
            List<String> out = new ArrayList<>(Math.max(8, ids.size()));
            for (String id : ids) {
                if (id != null && id.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                    out.add(id);
                }
            }
            return out;
        }

        return Collections.emptyList();
    }
}
