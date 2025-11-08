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

import java.io.*;
import java.nio.file.Files;
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

        // 폴더 준비
        createFolderIfAbsent(getConfig().getString("quests.folder", "quests"));
        createFolderIfAbsent(getConfig().getString("storage.folder", "playerdata"));

        // quests 폴더 내 예시 자동 복사
        copyExampleQuests();

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

        // 비동기 예열
        CompletableFuture.runAsync(() -> {
            try {
                engine.refreshEventCache();
                progress.preloadAll();
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

    // ---------------------------------------------------------
    // 유틸리티
    // ---------------------------------------------------------

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

    /**
     * quests/example.yml 자동 생성
     */
    private void copyExampleQuests() {
        File questsDir = new File(getDataFolder(), "quests");

        // quests 폴더가 없으면 새로 만들고 내부 리소스 전체 복사
        if (!questsDir.exists()) {
            if (questsDir.mkdirs()) {
                getLogger().info("[QuestEngine] Created quests directory. Copying default quests...");
            } else {
                getLogger().warning("[QuestEngine] Failed to create quests directory.");
                return;
            }

            try {
                // JAR 내부의 quests 폴더에 포함된 리소스 목록을 가져옴
                // (리소스 파일이 JAR에 패키징되어 있어야 함)
                String[] defaults = new String[] {
                        "quests/example1.yml",
                        "quests/example1_en.yml",
                        "quests/custom_event.yml"
                };

                for (String resourcePath : defaults) {
                    try (InputStream in = getResource(resourcePath)) {
                        if (in == null) {
                            getLogger().warning("[QuestEngine] Missing resource: " + resourcePath);
                            continue;
                        }

                        File target = new File(questsDir, new File(resourcePath).getName());
                        Files.copy(in, target.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                        getLogger().info("[QuestEngine] Copied default quest: " + target.getName());
                    }
                }
            } catch (Exception e) {
                getLogger().warning("[QuestEngine] Failed to copy default quests: " + e.getMessage());
            }
        } else {
            getLogger().info("[QuestEngine] Quests folder already exists, skipping default copy.");
        }
    }
}
