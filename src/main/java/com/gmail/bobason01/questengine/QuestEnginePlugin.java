package com.gmail.bobason01.questengine;

import com.gmail.bobason01.questengine.action.ActionExecutor;
import com.gmail.bobason01.questengine.command.QuestAdminCommand;
import com.gmail.bobason01.questengine.command.QuestCommand;
import com.gmail.bobason01.questengine.command.QuestEditorCommand;
import com.gmail.bobason01.questengine.command.QuestEngineCommand;
import com.gmail.bobason01.questengine.gui.QuestGuiManager;
import com.gmail.bobason01.questengine.gui.editor.QuestEditorMenu;
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
 * - ProgressRepository API 변경 대응 of 사용
 * - EventDispatcher 통합으로 인한 중복 리스너 등록 제거
 * - 스레드 풀 및 리소스 정리 최적화
 * - 퀘스트 폴더 존재 여부에 따른 초기화 로직 변경
 */
public final class QuestEnginePlugin extends JavaPlugin {

    private Engine engine;
    private QuestRepository quests;
    private ProgressRepository progress;
    private ActionExecutor actions;
    private ExecutorService asyncPool;
    private Msg msg;
    private QuestGuiManager gui;
    private QuestEditorMenu editorMenu;

    @Override
    public void onEnable() {
        long start = System.currentTimeMillis();
        getLogger().info("[QuestEngine] Initializing...");

        // 1. Config & Message
        saveDefaultConfig();
        msg = new Msg(this);

        // 2. Quest Folder Setup
        // 폴더가 존재하지 않는 경우에만 생성하고 기본 퀘스트 파일을 추출합니다
        // 이미 폴더가 존재하면 아무 작업도 하지 않습니다
        File questDir = new File(getDataFolder(), getConfig().getString("quests.folder", "quests"));

        if (!questDir.exists()) {
            if (questDir.mkdirs()) {
                getLogger().info("[QuestEngine] Quests folder created. Extracting defaults...");
                try {
                    copyDefaultQuests(questDir);
                } catch (Exception e) {
                    getLogger().warning("[QuestEngine] Failed to extract default quests: " + e.getMessage());
                }
            } else {
                getLogger().warning("[QuestEngine] Failed to create quest folder: " + questDir.getAbsolutePath());
            }
        } else {
            getLogger().info("[QuestEngine] Quests folder found. Skipping default generation.");
        }

        // 3. Core Components Init
        quests = new QuestRepository(this, questDir);
        progress = new ProgressRepository(this);

        // 4. Thread Pool CPU Core 기반 최적화
        asyncPool = Executors.newFixedThreadPool(
                Math.max(2, Runtime.getRuntime().availableProcessors()),
                r -> {
                    Thread t = new Thread(r, "QuestEngine-AsyncPool");
                    t.setDaemon(true);
                    return t;
                });

        actions = new ActionExecutor(this, msg);
        engine = new Engine(this, quests, progress, actions, msg, asyncPool);

        // 5. Preload Online Players
        for (Player p : Bukkit.getOnlinePlayers()) {
            try {
                // preload 대신 of 메서드 사용 데이터 로드 및 캐싱
                progress.of(p.getUniqueId(), p.getName());
                getLogger().info("[QuestEngine] Cached progress for " + p.getName());
            } catch (Throwable t) {
                getLogger().warning("[QuestEngine] Failed to load data for " + p.getName() + ": " + t.getMessage());
            }
        }

        // 6. Event Listeners 지연 등록
        // 별도의 Bridge 클래스 등록 제거 EventDispatcher 내부 처리
        Bukkit.getScheduler().runTask(this, () -> {
            try {
                new EventDispatcher(this, engine);
                new DynamicEventListener(this, engine, quests);
                getLogger().info("[QuestEngine] Event listeners registered.");
            } catch (Throwable t) {
                getLogger().warning("[QuestEngine] Event registration failed: " + t.getMessage());
            }
        });

        // 7. Hooks
        PartyHook.init(this, getConfig());

        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            try {
                new QuestPapiExpansion(this).register();
                getLogger().info("[QuestEngine] PlaceholderAPI expansion registered.");
            } catch (Throwable t) {
                getLogger().warning("[QuestEngine] PlaceholderAPI registration failed: " + t.getMessage());
            }
        }

        // 8. GUI & Commands
        gui = new QuestGuiManager(this);
        editorMenu = new QuestEditorMenu(this);

        new QuestCommand(this);
        new QuestAdminCommand(this);
        new QuestEngineCommand(this);
        new QuestEditorCommand(this, editorMenu);

        long took = System.currentTimeMillis() - start;
        getLogger().info("[QuestEngine] Enabled successfully in " + took + "ms");
    }

    @Override
    public void onDisable() {
        getLogger().info("[QuestEngine] Shutting down...");

        try {
            // 이벤트 리스너 해제
            HandlerList.unregisterAll(this);

            // 엔진 종료
            if (engine != null) engine.shutdown();

            // 데이터 저장 및 DB 연결 종료
            if (progress != null) progress.close();

            // 스레드 풀 강제 종료
            if (asyncPool != null && !asyncPool.isShutdown()) {
                asyncPool.shutdownNow();
            }

        } catch (Throwable t) {
            getLogger().warning("[QuestEngine] Exception during shutdown: " + t.getMessage());
        }

        getLogger().info("[QuestEngine] Disabled safely.");
    }

    // --- Accessors ---

    public Engine engine() { return engine; }
    public QuestRepository quests() { return quests; }
    public ProgressRepository progress() { return progress; }
    public Msg msg() { return msg; }
    public QuestGuiManager gui() { return gui; }
    public ExecutorService asyncPool() { return asyncPool; }
    public QuestEditorMenu editorMenu() { return editorMenu; }

    // 비동기 작업 헬퍼
    public void runAsync(Runnable task) {
        if (asyncPool == null || asyncPool.isShutdown()) {
            getLogger().warning("[QuestEngine] Async pool not available, running sync.");
            task.run();
            return;
        }
        asyncPool.submit(task);
    }

    // --- Internal Helpers ---

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
                if (fileName.isEmpty()) continue; // 폴더 자체인 경우 스킵

                File outFile = new File(questDir, fileName);
                // 이미 존재하면 덮어쓰지 않지만 상위 로직에서 폴더가 없을 때만 호출하므로
                // 사실상 새 폴더에 파일들을 생성하는 역할만 수행하게 됩니다
                if (outFile.exists()) continue;

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