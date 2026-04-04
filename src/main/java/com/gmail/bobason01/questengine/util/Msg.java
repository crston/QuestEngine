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

    /**
     * 언어 폴더에서 모든 .yml 파일을 읽어옵니다.
     */
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

    /**
     * 특정 플레이어의 언어 설정에 맞는 메시지를 가져옵니다.
     */
    public String get(Player p, String path) {
        String lang = plugin.progress().of(p.getUniqueId(), p.getName()).getLanguage();
        return getRaw(lang, path);
    }

    /**
     * 시스템 기본 언어를 기준으로 메시지를 반환합니다. (에디터 등에서 사용)
     */
    public String get(String path) {
        String defLang = plugin.getConfig().getString("default-language", "en");
        return getRaw(defLang, path);
    }

    /**
     * 키가 없을 경우 지정된 기본값(def)을 반환하며 색상을 입힙니다.
     */
    public String get(String path, String def) {
        String defLang = plugin.getConfig().getString("default-language", "en");
        YamlConfiguration config = langFiles.getOrDefault(defLang, langFiles.get("en"));

        if (config == null || !config.contains(path)) {
            return color(def);
        }

        return color(config.getString(path, def));
    }

    /**
     * 특정 언어 코드와 경로로 메시지를 가져오는 핵심 로직입니다.
     * [보완] 최후의 보루인 path 반환 시에도 color()를 입혔습니다.
     */
    public String getRaw(String langCode, String path) {
        String defLang = plugin.getConfig().getString("default-language", "en");
        YamlConfiguration config = langFiles.getOrDefault(langCode, langFiles.get(defLang));

        if (config == null) config = langFiles.get("en");

        // 파일 자체가 없거나 읽지 못했다면 path 자체에 색상을 입혀 반환
        if (config == null) return color(path);

        // 파일에 키가 없으면 path(기본값)를 가져오고 색상을 입힘
        String msg = config.getString(path, path);
        return color(msg);
    }

    /**
     * 플레이어의 언어 설정에 맞는 접두사(Prefix) 포함 메시지를 가져옵니다.
     */
    public String pref(Player p, String path) {
        String lang = plugin.progress().of(p.getUniqueId(), p.getName()).getLanguage();
        String prefix = getRaw(lang, "prefix");

        if (prefix.equalsIgnoreCase("prefix") || prefix.isEmpty()) {
            prefix = "&7[&aQuestEngine&7] ";
        }

        return color(prefix + getRaw(lang, path));
    }

    /**
     * 시스템 기본 언어 기준 접두사 포함 메시지를 가져옵니다.
     */
    public String pref(String path) {
        String defLang = plugin.getConfig().getString("default-language", "en");
        String prefix = getRaw(defLang, "prefix");
        if (prefix.equalsIgnoreCase("prefix")) prefix = "&7[&aQuestEngine&7] ";

        return color(prefix + getRaw(defLang, path));
    }

    /**
     * 플레이어 언어 설정에 따른 문자열 리스트(Lore)를 가져옵니다.
     */
    public List<String> list(Player p, String path) {
        String lang = plugin.progress().of(p.getUniqueId(), p.getName()).getLanguage();
        return listRaw(lang, path);
    }

    /**
     * 플레이어 인자 없이 리스트를 가져올 때 사용합니다.
     */
    public List<String> list(String path) {
        String defLang = plugin.getConfig().getString("default-language", "en");
        return listRaw(defLang, path);
    }

    private List<String> listRaw(String langCode, String path) {
        YamlConfiguration config = langFiles.getOrDefault(langCode, langFiles.get("en"));
        if (config == null) return Collections.emptyList();

        List<String> raw = config.getStringList(path);
        List<String> colored = new ArrayList<>();
        for (String s : raw) {
            colored.add(color(s));
        }
        return colored;
    }

    /**
     * 문자열에 컬러 코드를 입힙니다. (& -> ChatColor)
     */
    public String color(String text) {
        if (text == null) return "";
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    /**
     * PAPI 변수를 치환하고 컬러 코드를 입혀서 반환합니다.
     */
    public String parse(Player p, String text) {
        if (text == null) return "";
        String result = color(text.replace("%player%", p.getName()));

        if (org.bukkit.Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            return me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(p, result);
        }
        return result;
    }

    public List<String> getAvailableLanguages() {
        return Collections.unmodifiableList(availableLanguages);
    }

    public void reload() {
        loadLanguages();
    }
}