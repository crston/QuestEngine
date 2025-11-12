package com.gmail.bobason01.questengine.util;

import com.gmail.bobason01.questengine.QuestEnginePlugin;
import org.bukkit.ChatColor;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Msg
 * - 색코드(&) 지원 + prefix 선택적
 * - 캐싱 및 실시간 리로드 안정
 */
public final class Msg {

    private final QuestEnginePlugin plugin;
    private final File file;
    private volatile YamlConfiguration cfg;
    private volatile String prefix;

    private final Map<String, String> cache = new ConcurrentHashMap<>();
    private final Map<String, List<String>> listCache = new ConcurrentHashMap<>();

    public Msg(QuestEnginePlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "messages.yml");
        if (!file.exists()) {
            file.getParentFile().mkdirs();
            try {
                plugin.saveResource("messages.yml", false);
            } catch (IllegalArgumentException ignored) {
                try (Writer w = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
                    w.write("prefix: '&7[&aQuestEngine&7] '\n");
                } catch (IOException ignored2) {}
            }
        }
        reload();
    }

    /** 메시지 파일 다시 로드 */
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
        listCache.clear();
    }

    /** 리스트 메시지 (GUI용) */
    public List<String> list(String path) {
        return listCache.computeIfAbsent(path, p -> {
            List<String> list = cfg.getStringList(p);
            if (list != null && !list.isEmpty()) {
                List<String> colored = new ArrayList<>(list.size());
                for (String s : list)
                    colored.add(applyColor(s));
                return Collections.unmodifiableList(colored);
            }
            String single = cfg.getString(p);
            if (single != null && !single.isEmpty()) {
                return List.of(applyColor(single));
            }
            return List.of("§c<missing-list:" + p + ">");
        });
    }

    /** prefix 없이 색 적용 */
    public String get(String key) {
        return cache.computeIfAbsent("get:" + key, k ->
                applyColor(cfg.getString(key, key))
        );
    }

    public String get(String key, String def) {
        String val = get(key);
        return (val == null || val.isEmpty()) ? def : val;
    }

    /** prefix 포함 */
    public String pref(String key) {
        return cache.computeIfAbsent("pref:" + key, k ->
                prefix + applyColor(cfg.getString(key, key))
        );
    }

    /** 실제 색코드 변환 */
    private static String applyColor(String s) {
        return ChatColor.translateAlternateColorCodes('&', s == null ? "" : s);
    }
}
