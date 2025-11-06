package com.gmail.bobason01.questengine.util;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;

/**
 * FastYaml
 * - 빠른 로드/리로드용 YAML 래퍼
 * - BufferedReader 최소화 및 StringBuilder 재사용
 * - I/O 스트림 닫힘 자동화
 * - saveResource 누락 시 자동 복구
 */
public final class FastYaml {

    private final Plugin plugin;
    private final File file;
    private volatile YamlConfiguration cfg;

    public FastYaml(Plugin plugin, String name) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), name);
        reload();
    }

    public synchronized void reload() {
        ensureFileExists();
        var yml = new YamlConfiguration();
        try {
            yml.loadFromString(readAllFast(file));
            this.cfg = yml;
        } catch (IOException | InvalidConfigurationException ex) {
            plugin.getLogger().warning("[FastYaml] Failed to load " + file.getName() + ": " + ex.getMessage());
            this.cfg = new YamlConfiguration(); // fallback to empty
        }
    }

    public YamlConfiguration config() {
        return cfg;
    }

    private void ensureFileExists() {
        if (file.exists()) return;
        try {
            file.getParentFile().mkdirs();
            // saveResource는 리소스 안에 없을 수 있으므로 조용히 실패
            plugin.saveResource(file.getName(), false);
        } catch (IllegalArgumentException ex) {
            try {
                Files.writeString(file.toPath(), "# auto-generated " + file.getName() + "\n",
                        StandardCharsets.UTF_8, StandardOpenOption.CREATE);
            } catch (IOException ignored) {}
        }
    }

    /**
     * 고속 파일 읽기 (UTF-8)
     * FileInputStream -> BufferedReader -> StringBuilder
     */
    private static String readAllFast(File f) throws IOException {
        long len = f.length();
        int cap = len > 0 && len < Integer.MAX_VALUE ? (int) len + 64 : 8192;
        StringBuilder sb = new StringBuilder(cap);
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8), 8192)) {
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line).append('\n');
            }
        }
        return sb.toString();
    }
}
