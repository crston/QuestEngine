package com.gmail.bobason01.questengine.util;

import com.gmail.bobason01.questengine.QuestEnginePlugin;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.*;

public final class Msg {

    private final QuestEnginePlugin plugin;
    private final Map<String, YamlConfiguration> langFiles = new HashMap<>();
    private final List<String> availableLanguages = new ArrayList<>();

    public Msg(QuestEnginePlugin plugin) {
        this.plugin = plugin;
        loadLanguages();
    }

    public void loadLanguages() {
        File folder = new File(plugin.getDataFolder(), "lang");
        if (!folder.exists()) {
            folder.mkdirs();
            plugin.saveResource("lang/en.yml", false);
            plugin.saveResource("lang/ko.yml", false);
        }

        langFiles.clear();
        availableLanguages.clear();
        File[] files = folder.listFiles((d, name) -> name.endsWith(".yml"));

        if (files != null) {
            for (File f : files) {
                String langCode = f.getName().replace(".yml", "");
                langFiles.put(langCode, YamlConfiguration.loadConfiguration(f));
                availableLanguages.add(langCode);
            }
        }
    }

    public String get(Player p, String path) {
        if (p == null) return get(path);
        String lang = plugin.progress().of(p.getUniqueId(), p.getName()).getLanguage();
        return getRaw(lang, path);
    }

    public String get(String path) {
        String defLang = plugin.getConfig().getString("default-language", "en");
        return getRaw(defLang, path);
    }

    public String getRaw(String langCode, String path) {
        YamlConfiguration config = langFiles.get(langCode);
        if (config == null) {
            String defLang = plugin.getConfig().getString("default-language", "en");
            config = langFiles.getOrDefault(defLang, langFiles.get("en"));
        }

        if (config == null) return color(path);

        String msg = config.getString(path);
        if (msg == null || msg.isEmpty()) return color(path);

        return color(msg);
    }

    // Prefix 제거 요청에 따라 pref 호출 시에도 일반 get과 동일하게 동작하도록 수정
    public String pref(Player p, String path) {
        return get(p, path);
    }

    public String pref(String path) {
        return get(path);
    }

    public List<String> list(Player p, String path) {
        String lang = plugin.progress().of(p.getUniqueId(), p.getName()).getLanguage();
        YamlConfiguration config = langFiles.getOrDefault(lang, langFiles.get("en"));
        if (config == null) return Collections.emptyList();

        List<String> raw = config.getStringList(path);
        List<String> colored = new ArrayList<>();
        for (String s : raw) colored.add(color(s));
        return colored;
    }

    public String color(String text) {
        if (text == null) return "";
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    public List<String> getAvailableLanguages() {
        return Collections.unmodifiableList(availableLanguages);
    }

    public void reload() {
        loadLanguages();
    }
}