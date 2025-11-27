package com.gmail.bobason01.questengine.command;

import com.gmail.bobason01.questengine.QuestEnginePlugin;
import com.gmail.bobason01.questengine.util.Msg;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;

import java.util.Collections;
import java.util.List;

/**
 * BaseCommand
 * - TabExecutor 구현으로 통일
 * - 공통 필드 최적화
 */
public abstract class BaseCommand implements TabExecutor {

    protected final QuestEnginePlugin plugin;
    protected final Msg msg;

    public BaseCommand(QuestEnginePlugin plugin) {
        this.plugin = plugin;
        this.msg = plugin.msg();
    }

    @Override
    public abstract boolean onCommand(CommandSender sender, Command command, String label, String[] args);

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        return Collections.emptyList();
    }
}