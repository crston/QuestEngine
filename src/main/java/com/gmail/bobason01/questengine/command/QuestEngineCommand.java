package com.gmail.bobason01.questengine.command;

import com.gmail.bobason01.questengine.QuestEnginePlugin;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;

/**
 * QuestEngineCommand
 * - 개발/진단용 명령어
 * - 즉시 응답 중심으로 설계되어 스케줄러, 스트림, 동기화 없음
 */
public final class QuestEngineCommand extends BaseCommand {

    private static final String PING = "ping";
    private static final String CACHE = "cache";
    private static final String PAPI = "papi";
    private static final String VERSION = "version";

    public QuestEngineCommand(QuestEnginePlugin plugin) {
        super(plugin);
        PluginCommand cmd = plugin.getCommand("questengine");
        if (cmd != null) cmd.setExecutor(this);
        else plugin.getLogger().warning("questengine command not found in plugin.yml");
    }

    @Override
    public boolean onCommand(CommandSender s, Command c, String l, String[] a) {
        if (a.length == 0) {
            s.sendMessage("/questengine ping|cache|papi|version");
            return true;
        }

        String sub = a[0].toLowerCase();
        // 분기 순서 최적화: 가장 자주 쓰일 가능성이 높은 ping부터
        if (PING.equals(sub)) {
            s.sendMessage("§aQuestEngine active");
            return true;
        }
        if (CACHE.equals(sub)) {
            s.sendMessage("§eCached players: §f" + plugin.engine().progress().cacheSize());
            return true;
        }
        if (PAPI.equals(sub)) {
            boolean has = Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null;
            s.sendMessage("§ePlaceholderAPI: §f" + has);
            return true;
        }
        if (VERSION.equals(sub)) {
            s.sendMessage("§eQuestEngine version §f" + plugin.getDescription().getVersion());
            return true;
        }

        s.sendMessage("/questengine ping|cache|papi|version");
        return true;
    }
}
