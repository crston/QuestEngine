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
     * [1] 특정 플레이어의 언어 설정에 맞는 메시지를 가져옵니다. (가장 일반적인 사용)
     */
    public String get(Player p, String path) {
        String lang = plugin.progress().of(p.getUniqueId(), p.getName()).getLanguage();
        return getRaw(lang, path);
    }

    /**
     * [2] 인자가 1개일 때 호출됨 (QuestEditorMenu 등에서 사용)
     * 시스템 기본 언어를 기준으로 메시지를 반환합니다.
     */
    public String get(String path) {
        String defLang = plugin.getConfig().getString("default-language", "en");
        return getRaw(defLang, path);
    }

    /**
     * [3] 인자가 2개이고 모두 문자열일 때 호출됨 (QuestEditorMenu 헬퍼 대응)
     * 키가 없을 경우 지정된 기본값(def)을 반환합니다.
     */
    public String get(String path, String def) {
        String defLang = plugin.getConfig().getString("default-language", "en");
        YamlConfiguration config = langFiles.getOrDefault(defLang, langFiles.get("en"));

        if (config == null || !config.contains(path)) {
            return ChatColor.translateAlternateColorCodes('&', def);
        }

        return ChatColor.translateAlternateColorCodes('&', config.getString(path, def));
    }

    /**
     * 특정 언어 코드와 경로로 메시지를 가져오는 핵심 로직입니다.
     */
    public String getRaw(String langCode, String path) {
        String defLang = plugin.getConfig().getString("default-language", "en");
        YamlConfiguration config = langFiles.getOrDefault(langCode, langFiles.get(defLang));

        if (config == null) config = langFiles.get("en");
        if (config == null) return path; // 최후의 보루: 키 이름 그대로 반환

        String msg = config.getString(path, path);
        return ChatColor.translateAlternateColorCodes('&', msg);
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

        return ChatColor.translateAlternateColorCodes('&', prefix + getRaw(lang, path));
    }

    /**
     * 시스템 기본 언어 기준 접두사 포함 메시지를 가져옵니다.
     */
    public String pref(String path) {
        String defLang = plugin.getConfig().getString("default-language", "en");
        String prefix = getRaw(defLang, "prefix");
        if (prefix.equalsIgnoreCase("prefix")) prefix = "&7[&aQuestEngine&7] ";

        return ChatColor.translateAlternateColorCodes('&', prefix + getRaw(defLang, path));
    }

    /**
     * 플레이어 언어 설정에 따른 문자열 리스트(Lore)를 가져옵니다.
     */
    public List<String> list(Player p, String path) {
        String lang = plugin.progress().of(p.getUniqueId(), p.getName()).getLanguage();
        return listRaw(lang, path);
    }

    /**
     * 플레이어 인자 없이 리스트를 가져올 때 사용 (기본 언어 기준)
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
            colored.add(ChatColor.translateAlternateColorCodes('&', s));
        }
        return colored;
    }

    /**
     * 문자열에 컬러 코드를 입힙니다.
     */
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