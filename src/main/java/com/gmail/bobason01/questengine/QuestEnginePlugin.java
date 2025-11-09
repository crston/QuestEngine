package com.gmail.bobason01.questengine;

import com.gmail.bobason01.questengine.action.ActionExecutor;
import com.gmail.bobason01.questengine.command.QuestAdminCommand;
import com.gmail.bobason01.questengine.command.QuestCommand;
import com.gmail.bobason01.questengine.command.QuestEngineCommand;
import com.gmail.bobason01.questengine.gui.QuestGuiManager;
import com.gmail.bobason01.questengine.party.PartyHook;
import com.gmail.bobason01.questengine.papi.QuestPapiExpansion;
import com.gmail.bobason01.questengine.progress.ProgressRepository;
import com.gmail.bobason01.questengine.quest.QuestRepository;
import com.gmail.bobason01.questengine.runtime.DynamicEventListener;
import com.gmail.bobason01.questengine.runtime.Engine;
import com.gmail.bobason01.questengine.runtime.EventDispatcher;
import com.gmail.bobason01.questengine.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * QuestEnginePlugin
 * - 메인 플러그인 엔트리
 * - 고성능 비동기 퀘스트 엔진
 * - Paper/Purpur 완전 대응
 * - GUI / 이벤트 / 명령 / 연동 통합
 */
public final class QuestEnginePlugin extends JavaPlugin {

    private Engine engine;
    private QuestRepository quests;
    private ProgressRepository progress;
    private ActionExecutor actions;
    private ExecutorService asyncPool;
    private Msg msg;
    private QuestGuiManager gui;

    @Override
    public void onEnable() {
        long start = System.currentTimeMillis();
        getLogger().info("[QuestEngine] Initializing...");

        saveDefaultConfig();

        // =============================================================
        // 메시지 시스템
        // =============================================================
        msg = new Msg(this);

        // =============================================================
        // 리포지토리 및 스레드풀 초기화
        // =============================================================
        quests = new QuestRepository(this, getConfig().getString("quests.folder", "quests"));
        progress = new ProgressRepository(this);

        asyncPool = Executors.newFixedThreadPool(
                Math.max(2, Runtime.getRuntime().availableProcessors() / 2),
                r -> {
                    Thread t = new Thread(r, "QuestEngine-AsyncPool");
                    t.setDaemon(true);
                    return t;
                });

        // =============================================================
        // 액션 실행기 및 엔진 생성
        // =============================================================
        actions = new ActionExecutor(this, msg);
        engine = new Engine(this, quests, progress, actions, msg, asyncPool);

        // =============================================================
        // 온라인 플레이어 캐시 프리로드
        // =============================================================
        for (Player p : Bukkit.getOnlinePlayers()) {
            try {
                progress.preload(p.getUniqueId());
                getLogger().info("[QuestEngine] Cached progress for " + p.getName());
            } catch (Throwable t) {
                getLogger().warning("[QuestEngine] Failed to preload " + p.getName() + ": " + t.getMessage());
            }
        }

        // =============================================================
        // 이벤트 등록 (Paper-safe)
        // =============================================================
        Bukkit.getScheduler().runTask(this, () -> {
            try {
                new EventDispatcher(this, engine);
                new DynamicEventListener(this, engine, quests);
                getLogger().info("[QuestEngine] Event listeners registered (Paper-safe).");
            } catch (Throwable t) {
                getLogger().warning("[QuestEngine] Event registration failed: " + t.getMessage());
            }
        });

        // =============================================================
        // 파티 플러그인 연동
        // =============================================================
        PartyHook.init(this, getConfig());

        // =============================================================
        // PlaceholderAPI 확장
        // =============================================================
        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            try {
                new QuestPapiExpansion(this).register();
                getLogger().info("[QuestEngine] PlaceholderAPI expansion registered.");
            } catch (Throwable t) {
                t.printStackTrace();
                getLogger().warning("[QuestEngine] PlaceholderAPI registration failed: " + t.getMessage());
            }
        }

        // =============================================================
        // 명령어 등록
        // =============================================================
        new QuestCommand(this);
        new QuestAdminCommand(this);
        new QuestEngineCommand(this);

        // =============================================================
        // GUI 매니저 초기화
        // =============================================================
        gui = new QuestGuiManager(this);

        long took = System.currentTimeMillis() - start;
        getLogger().info("[QuestEngine] Enabled successfully in " + took + "ms");
    }

    @Override
    public void onDisable() {
        getLogger().info("[QuestEngine] Shutting down...");

        try {
            HandlerList.unregisterAll(this);
            if (engine != null) engine.shutdown();
            if (progress != null) progress.close();
            if (asyncPool != null && !asyncPool.isShutdown()) asyncPool.shutdownNow();
        } catch (Throwable t) {
            t.printStackTrace();
            getLogger().warning("[QuestEngine] Exception during shutdown: " + t.getMessage());
        }

        getLogger().info("[QuestEngine] Disabled safely.");
    }

    // =============================================================
    // 접근자
    // =============================================================
    public Engine engine() { return engine; }
    public QuestRepository quests() { return quests; }
    public ProgressRepository progress() { return progress; }
    public Msg msg() { return msg; }
    public QuestGuiManager gui() { return gui; }
    public ExecutorService asyncPool() { return asyncPool; }

    // =============================================================
    // 간편 비동기 실행 유틸
    // =============================================================
    public void runAsync(Runnable task) {
        if (asyncPool == null || asyncPool.isShutdown()) {
            getLogger().warning("[QuestEngine] Async pool not available, running sync.");
            task.run();
            return;
        }
        asyncPool.submit(task);
    }
}
