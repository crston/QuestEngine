package com.gmail.bobason01.questengine.quest;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.*;

/**
 * QuestLoader
 * - QuestDef 로딩 전용 클래스
 * - targets 리스트 구조 대응
 * - 파일 접근 최소화 및 객체 생성 최적화
 */
public final class QuestLoader {

    private QuestLoader() {}

    public static QuestDef load(File file) {
        final YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);

        // 기본 정보
        final String id = stripExt(file.getName()).toLowerCase(Locale.ROOT);
        final String name = cfg.getString("name", id);
        final String event = cfg.getString("event", "none");

        // target / targets 처리
        final List<String> targets = new ArrayList<>();
        if (cfg.contains("targets")) {
            for (String s : cfg.getStringList("targets")) {
                if (s != null && !s.isBlank()) targets.add(s.trim());
            }
        } else if (cfg.contains("target")) {
            String single = cfg.getString("target", "").trim();
            if (!single.isEmpty()) targets.add(single);
        }

        final int amount = cfg.getInt("amount", 1);
        final int repeat = cfg.getInt("repeat", 1);
        final int points = cfg.getInt("points", 0);
        final boolean isPublic = cfg.getBoolean("public", true);
        final boolean party = cfg.getBoolean("party", false);
        final String type = cfg.getString("type", "vanilla");

        // Reset 섹션
        final QuestDef.Reset reset = new QuestDef.Reset(
                cfg.getString("reset.policy", "none"),
                cfg.getString("reset.time", "00:00")
        );

        // Display 섹션
        Map<String, Object> displayRaw = readDisplay(cfg.getConfigurationSection("display"));
        QuestDef.Display display = new QuestDef.Display(displayRaw);

        // Custom Event
        final ConfigurationSection ce = cfg.getConfigurationSection("custom_event_data");
        final CustomEventData custom = (ce != null) ? CustomEventData.load(ce) : null;

        // Conditions
        final List<String> condSuccess = safeList(cfg.getStringList("conditions.success"));
        final List<String> condFail = safeList(cfg.getStringList("conditions.fail"));

        // Actions
        final Map<String, List<String>> actions = readActions(cfg.getConfigurationSection("actions"));

        // cfg 참조 제거 > GC 조기 대상화
        cfg.options().copyDefaults(false);

        // target > targets 로 통합
        return new QuestDef(
                id, name, event, targets, amount, repeat, points,
                isPublic, party, type, reset, display, custom,
                condSuccess, condFail, actions
        );
    }

    // ------------------------------------------------------------------------
    // 내부 헬퍼
    // ------------------------------------------------------------------------
    private static Map<String, Object> readDisplay(ConfigurationSection s) {
        if (s == null) return Collections.emptyMap();
        Map<String, Object> m = new LinkedHashMap<>(8);
        for (String key : s.getKeys(false)) {
            Object v = s.get(key);
            if (v != null) m.put(key.toLowerCase(Locale.ROOT), v);
        }
        return m;
    }

    private static Map<String, List<String>> readActions(ConfigurationSection sec) {
        if (sec == null) return Collections.emptyMap();
        Map<String, List<String>> map = new LinkedHashMap<>(8);
        for (String key : sec.getKeys(false)) {
            List<String> list = sec.getStringList(key);
            if (!list.isEmpty()) map.put(key.toLowerCase(Locale.ROOT), List.copyOf(list));
        }
        return map;
    }

    private static List<String> safeList(List<String> src) {
        if (src == null || src.isEmpty()) return List.of();
        return List.copyOf(src);
    }

    private static String stripExt(String name) {
        int dot = name.lastIndexOf('.');
        return (dot > 0) ? name.substring(0, dot) : name;
    }
}
