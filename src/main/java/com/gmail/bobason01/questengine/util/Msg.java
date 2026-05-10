package com.gmail.bobason01.questengine.util;

import com.gmail.bobason01.questengine.QuestEnginePlugin;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
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

        // 1. 기본 제공 언어 리스트 정의
        String[] defaultLangs = {"en", "ko"};

        // 2. 기본 언어들에 대해 업데이트 체크 진행
        for (String lang : defaultLangs) {
            updateLangFile(lang);
        }

        // 3. 폴더 내의 모든 언어 파일 로드
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
     * 리소스 내의 원본 파일과 비교하여 누락된 키를 로컬 파일에 추가합니다.
     */
    private void updateLangFile(String langCode) {
        String fileName = "lang/" + langCode + ".yml";
        File file = new File(plugin.getDataFolder(), fileName);

        if (!file.exists()) return;

        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);

        // jar 파일 내부의 원본 리소스를 읽어옴
        InputStream is = plugin.getResource(fileName);
        if (is == null) return;

        YamlConfiguration internalConfig = YamlConfiguration.loadConfiguration(new InputStreamReader(is, StandardCharsets.UTF_8));

        boolean changed = false;
        // 내부 리소스의 모든 키를 순회하며 로컬 파일에 없는 것만 추가
        for (String key : internalConfig.getKeys(true)) {
            if (!config.contains(key)) {
                config.set(key, internalConfig.get(key));
                changed = true;
            }
        }

        // 변경 사항이 있다면 파일 저장
        if (changed) {
            try {
                config.save(file);
                plugin.getLogger().info("Updated missing language keys in " + fileName);
            } catch (Exception e) {
                e.printStackTrace();
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