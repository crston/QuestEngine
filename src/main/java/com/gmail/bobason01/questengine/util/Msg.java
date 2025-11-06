package com.gmail.bobason01.questengine.util;

import com.gmail.bobason01.questengine.QuestEnginePlugin;
import org.bukkit.ChatColor;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Msg
 * - 고속 메시지 매니저 (다국어 지원 기반)
 * - I/O 최소화 및 캐싱 구조
 * - 실시간 리로드 안정
 */
public final class Msg {

    private final QuestEnginePlugin plugin;
    private final File file;
    private volatile YamlConfiguration cfg;
    private volatile String prefix;

    // 변환된 메시지 캐시 (Color + prefix 포함)
    private final Map<String, String> cache = new ConcurrentHashMap<>();

    public Msg(QuestEnginePlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "messages.yml");
        if (!file.exists()) {
            file.getParentFile().mkdirs();
            try {
                plugin.saveResource("messages.yml", false);
            } catch (IllegalArgumentException ignored) {
                try (Writer w = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
                    w.write("prefix: '&7[&aQuestEngine&7]'\n");
                } catch (IOException ignored2) {}
            }
        }
        reload();
    }

    /**
     * 메시지 파일 다시 로드 (핫리로드 안전)
     */
    public synchronized void reload() {
        YamlConfiguration yml = new YamlConfiguration();
        try (InputStreamReader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
            yml.load(reader);
        } catch (IOException | InvalidConfigurationException ex) {
            plugin.getLogger().warning("[Msg] Failed to load messages.yml: " + ex.getMessage());
        }
        this.cfg = yml;
        this.prefix = ChatColor.translateAlternateColorCodes('&',
                yml.getString("prefix", "&7[&aQuestEngine&7] "));
        cache.clear();
    }

    /**
     * prefix + 메시지 (기본)
     */
    public String get(String key) {
        return cache.computeIfAbsent(key, k ->
                prefix + color(cfg.getString(k, k))
        );
    }

    /**
     * prefix 포함 버전 (get()과 동일, 호환용)
     */
    public String pref(String key) {
        return get(key);
    }

    /**
     * prefix 없는 원문
     */
    public String raw(String key) {
        return cache.computeIfAbsent("raw:" + key, k ->
                color(cfg.getString(key, key))
        );
    }

    private static String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s == null ? "" : s);
    }
}
