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

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * QuestEnginePlugin
 * - High-performance asynchronous quest engine
 * - Fully compatible with Paper/Purpur/Folia
 * - Supports automatic quest extraction from JAR (resources/quests/*.yml)
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

        // ------------------------------------------------------------
        // Config + Messages
        // ------------------------------------------------------------
        saveDefaultConfig();
        msg = new Msg(this);

        // ------------------------------------------------------------
        // Repositories & folders
        // ------------------------------------------------------------
        File questDir = new File(getDataFolder(), getConfig().getString("quests.folder", "quests"));
        if (!questDir.exists() && !questDir.mkdirs()) {
            getLogger().warning("[QuestEngine] Failed to create quest folder: " + questDir.getAbsolutePath());
        }

        // 내부 JAR의 /quests/ 리소스를 외부로 복사
        try {
            copyDefaultQuests(questDir);
        } catch (Exception e) {
            getLogger().warning("[QuestEngine] Failed to extract default quests: " + e.getMessage());
        }

        quests = new QuestRepository(this, questDir);
        progress = new ProgressRepository(this);

        // ------------------------------------------------------------
        // Thread pool
        // ------------------------------------------------------------
        asyncPool = Executors.newFixedThreadPool(
                Math.max(2, Runtime.getRuntime().availableProcessors() / 2),
                r -> {
                    Thread t = new Thread(r, "QuestEngine-AsyncPool");
                    t.setDaemon(true);
                    return t;
                });

        // ------------------------------------------------------------
        // Engine + Actions
        // ------------------------------------------------------------
        actions = new ActionExecutor(this, msg);
        engine = new Engine(this, quests, progress, actions, msg, asyncPool);

        // ------------------------------------------------------------
        // Cache preload for online players
        // ------------------------------------------------------------
        for (Player p : Bukkit.getOnlinePlayers()) {
            try {
                progress.preload(p.getUniqueId());
                getLogger().info("[QuestEngine] Cached progress for " + p.getName());
            } catch (Throwable t) {
                getLogger().warning("[QuestEngine] Failed to preload " + p.getName() + ": " + t.getMessage());
            }
        }

        // ------------------------------------------------------------
        // Register event listeners
        // ------------------------------------------------------------
        Bukkit.getScheduler().runTask(this, () -> {
            try {
                new EventDispatcher(this, engine);
                new DynamicEventListener(this, engine, quests);
                getLogger().info("[QuestEngine] Event listeners registered (Paper-safe).");
            } catch (Throwable t) {
                getLogger().warning("[QuestEngine] Event registration failed: " + t.getMessage());
            }
        });

        // ------------------------------------------------------------
        // Party hooks
        // ------------------------------------------------------------
        PartyHook.init(this, getConfig());

        // ------------------------------------------------------------
        // PlaceholderAPI
        // ------------------------------------------------------------
        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            try {
                new QuestPapiExpansion(this).register();
                getLogger().info("[QuestEngine] PlaceholderAPI expansion registered.");
            } catch (Throwable t) {
                getLogger().warning("[QuestEngine] PlaceholderAPI registration failed: " + t.getMessage());
            }
        }

        // ------------------------------------------------------------
        // Commands
        // ------------------------------------------------------------
        new QuestCommand(this);
        new QuestAdminCommand(this);
        new QuestEngineCommand(this);

        // ------------------------------------------------------------
        // GUI
        // ------------------------------------------------------------
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
            getLogger().warning("[QuestEngine] Exception during shutdown: " + t.getMessage());
        }

        getLogger().info("[QuestEngine] Disabled safely.");
    }

    // ------------------------------------------------------------
    // Accessors
    // ------------------------------------------------------------
    public Engine engine() { return engine; }
    public QuestRepository quests() { return quests; }
    public ProgressRepository progress() { return progress; }
    public Msg msg() { return msg; }
    public QuestGuiManager gui() { return gui; }
    public ExecutorService asyncPool() { return asyncPool; }

    // ------------------------------------------------------------
    // Async helper
    // ------------------------------------------------------------
    public void runAsync(Runnable task) {
        if (asyncPool == null || asyncPool.isShutdown()) {
            getLogger().warning("[QuestEngine] Async pool not available, running sync.");
            task.run();
            return;
        }
        asyncPool.submit(task);
    }

    // ------------------------------------------------------------
    // Quest Extraction from JAR
    // ------------------------------------------------------------
    private void copyDefaultQuests(File questDir) throws Exception {
        File jarFile;
        try {
            jarFile = new File(getClass().getProtectionDomain().getCodeSource().getLocation().toURI());
        } catch (URISyntaxException e) {
            getLogger().warning("[QuestEngine] Could not locate plugin JAR.");
            return;
        }

        try (JarFile jar = new JarFile(jarFile)) {
            var entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();
                if (!name.startsWith("quests/") || !name.endsWith(".yml")) continue;

                String fileName = name.substring("quests/".length());
                File outFile = new File(questDir, fileName);

                if (outFile.exists()) continue; // 이미 존재 시 스킵

                try (InputStream in = jar.getInputStream(entry);
                     FileOutputStream out = new FileOutputStream(outFile)) {
                    byte[] buf = new byte[4096];
                    int len;
                    while ((len = in.read(buf)) > 0) out.write(buf, 0, len);
                    getLogger().info("[QuestEngine] Extracted default quest: " + fileName);
                } catch (Throwable t) {
                    getLogger().warning("[QuestEngine] Failed to copy quest " + fileName + ": " + t.getMessage());
                }
            }
        }
    }
}
