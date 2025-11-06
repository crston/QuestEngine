package com.gmail.bobason01.questengine.command;

import com.gmail.bobason01.questengine.QuestEnginePlugin;
import com.gmail.bobason01.questengine.util.Msg;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

/**
 * BaseCommand
 * 모든 커맨드 클래스의 공통 부모
 * - msg, plugin 참조를 final로 유지
 * - 생성자 시점에 캐시 확정
 * - reflection, lookup, instanceof 검사 최소화
 */
public abstract class BaseCommand implements CommandExecutor {

    protected final QuestEnginePlugin plugin;
    protected final Msg msg;

    public BaseCommand(QuestEnginePlugin plugin) {
        this.plugin = plugin;
        // Engine 접근 비용 최소화: 한번만 참조
        Msg m;
        try {
            m = plugin.engine().msg();
        } catch (Throwable t) {
            // engine 초기화 시점 문제를 방지
            m = new Msg(plugin);
        }
        this.msg = m;
    }

    /**
     * 모든 하위 명령어 클래스는 onCommand 구현 필수
     * 성능을 위해 명시적으로 final override를 사용하지 않음
     */
    @Override
    public abstract boolean onCommand(CommandSender sender, Command command, String label, String[] args);
}
