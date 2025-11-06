package com.gmail.bobason01.questengine;

import com.gmail.bobason01.questengine.action.ActionExecutor;
import com.gmail.bobason01.questengine.command.QuestAdminCommand;
import com.gmail.bobason01.questengine.command.QuestCommand;
import com.gmail.bobason01.questengine.command.QuestEngineCommand;
import com.gmail.bobason01.questengine.progress.ProgressRepository;
import com.gmail.bobason01.questengine.quest.QuestRepository;
import com.gmail.bobason01.questengine.runtime.DynamicEventListener;
import com.gmail.bobason01.questengine.runtime.Engine;
import com.gmail.bobason01.questengine.runtime.EventDispatcher;
import com.gmail.bobason01.questengine.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * QuestEnginePlugin
 * - 극한 성능 중심 구조
 * - 안전한 로드/언로드 시퀀스
 * - async warm-up + weak reload-safe pattern
 */
public final class QuestEnginePlugin extends JavaPlugin {

    private static QuestEnginePlugin inst;

    private volatile Msg msg;
    private volatile QuestRepository quests;
    private volatile ProgressRepository progress;
    private volatile Engine engine;
    private volatile ActionExecutor actions;

    @Override
    public void onLoad() {
        inst = this;
    }

    @Override
    public void onEnable() {
        long start = System.nanoTime();

        saveDefaultConfig();
        saveResourceIfAbsent("messages.yml");
        createFolderIfAbsent(getConfig().getString("quests.folder", "quests"));
        createFolderIfAbsent(getConfig().getString("storage.folder", "playerdata"));

        msg = new Msg(this);
        actions = new ActionExecutor(this, msg);
        progress = new ProgressRepository(this);
        quests = new QuestRepository(this, getConfig().getString("quests.folder", "quests"));
        engine = new Engine(this, quests, progress, actions, msg);

        PluginManager pm = Bukkit.getPluginManager();
        pm.registerEvents(new EventDispatcher(this, engine), this);
        new DynamicEventListener(this, engine, quests);

        // 명령어 등록
        new QuestCommand(this);
        new QuestAdminCommand(this);
        new QuestEngineCommand(this);

        // 비동기 예열: 캐시 빌드 + 디스크 접근 warm-up
        CompletableFuture.runAsync(() -> {
            try {
                engine.refreshEventCache();
                progress.preloadAll(); // optional, 캐시 예열 메서드 있으면 추가
            } catch (Throwable t) {
                getLogger().warning("[QuestEngine] async warmup failed: " + t.getMessage());
            }
        });

        long took = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
        getLogger().info("[QuestEngine] Enabled in " + took + " ms (" + Bukkit.getOnlinePlayers().size() + " online)");
    }

    @Override
    public void onDisable() {
        try {
            if (engine != null) engine.shutdown();
        } catch (Throwable t) {
            getLogger().warning("[QuestEngine] engine shutdown: " + t.getMessage());
        }
        try {
            if (progress != null) progress.close();
        } catch (Throwable t) {
            getLogger().warning("[QuestEngine] progress close: " + t.getMessage());
        }
        inst = null;
    }

    public static QuestEnginePlugin inst() { return inst; }
    public Engine engine() { return engine; }
    public Msg msg() { return msg; }

    private void saveResourceIfAbsent(String path) {
        File f = new File(getDataFolder(), path);
        if (!f.exists()) {
            try {
                saveResource(path, false);
            } catch (IllegalArgumentException ignored) {}
        }
    }

    private void createFolderIfAbsent(String name) {
        File dir = new File(getDataFolder(), name);
        if (!dir.exists() && !dir.mkdirs()) {
            getLogger().warning("[QuestEngine] Failed to create folder: " + name);
        }
    }
}
