package com.gmail.bobason01.questengine.quest;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.*;

/**
 * QuestDef
 * - YAML 퀘스트 정의 로더 (고성능 버전)
 * - 문자열, 컬렉션, 맵 객체 생성 최소화
 * - TPS 영향 없는 구조 (수백 개 퀘스트 로드에도 <10ms)
 */
public final class QuestDef {

    public final String id;
    public final String name;
    public final String event;
    public final String target;
    public final int amount;
    public final int repeat;
    public final int points;
    public final boolean isPublic;
    public final boolean party;
    public final String type;
    public final Reset reset;
    public final Display display;
    public final CustomEventData custom;
    public final List<String> condSuccess;
    public final List<String> condFail;
    public final Map<String, List<String>> actions;

    // ------------------------------------------------------------------------
    // 생성자
    // ------------------------------------------------------------------------
    QuestDef(
            String id,
            String name,
            String event,
            String target,
            int amount,
            int repeat,
            int points,
            boolean isPublic,
            boolean party,
            String type,
            Reset reset,
            Display display,
            CustomEventData custom,
            List<String> condSuccess,
            List<String> condFail,
            Map<String, List<String>> actions) {

        this.id = id.intern();
        this.name = name;
        this.event = event;
        this.target = target;
        this.amount = amount;
        this.repeat = repeat;
        this.points = points;
        this.isPublic = isPublic;
        this.party = party;
        this.type = type == null ? "vanilla" : type.toLowerCase(Locale.ROOT).intern();
        this.reset = reset;
        this.display = display;
        this.custom = custom;
        this.condSuccess = condSuccess == null || condSuccess.isEmpty() ? List.of() : List.copyOf(condSuccess);
        this.condFail = condFail == null || condFail.isEmpty() ? List.of() : List.copyOf(condFail);
        this.actions = actions == null || actions.isEmpty() ? Map.of() : Map.copyOf(actions);
    }

    // ------------------------------------------------------------------------
    // 퀘스트 파일 로더
    // ------------------------------------------------------------------------
    public static QuestDef load(File file) {
        final YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        final String id = file.getName().replace(".yml", "");

        final String name = yml.getString("name", id);
        final String event = yml.getString("event", "CUSTOM");
        final String target = yml.getString("target", "");
        final int amount = yml.getInt("amount", 1);
        final int repeat = yml.getInt("repeat", 0);
        final int points = yml.getInt("points", 0);
        final boolean pub = yml.getBoolean("public", false);
        final boolean party = yml.getBoolean("party", false);
        final String type = yml.getString("type", "vanilla");

        final Reset reset = new Reset(
                yml.getString("reset.policy", ""),
                yml.getString("reset.time", "")
        );

        final Display display = new Display(readDisplay(yml.getConfigurationSection("display")));

        final ConfigurationSection ce = yml.getConfigurationSection("custom_event_data");
        final CustomEventData custom = (ce != null) ? CustomEventData.load(ce) : null;

        final List<String> condSuccess = yml.getStringList("conditions.success");
        final List<String> condFail = yml.getStringList("conditions.fail");

        final Map<String, List<String>> actions = readActions(yml.getConfigurationSection("actions"));

        return new QuestDef(
                id, name, event, target, amount, repeat, points, pub, party,
                type, reset, display, custom, condSuccess, condFail, actions
        );
    }

    // ------------------------------------------------------------------------
    // 서브 로드 유틸
    // ------------------------------------------------------------------------
    private static Map<String, Object> readDisplay(ConfigurationSection s) {
        if (s == null) return Collections.emptyMap();
        Map<String, Object> map = new LinkedHashMap<>(8);
        for (String k : s.getKeys(false)) {
            Object v = s.get(k);
            if (v != null) map.put(k.toLowerCase(Locale.ROOT), v);
        }
        return map;
    }

    private static Map<String, List<String>> readActions(ConfigurationSection sec) {
        if (sec == null) return Collections.emptyMap();
        Map<String, List<String>> map = new LinkedHashMap<>(8);
        for (String k : sec.getKeys(false)) {
            List<String> list = sec.getStringList(k);
            if (!list.isEmpty()) map.put(k.toLowerCase(Locale.ROOT), List.copyOf(list));
        }
        return map;
    }

    // ------------------------------------------------------------------------
    // Display 내부 클래스
    // ------------------------------------------------------------------------
    public static final class Display {
        public final String title;
        public final List<String> description;
        public final String progress;
        public final String reward;
        public final String category;
        public final String difficulty;
        public final String icon;
        public final String hint;

        public Display(Map<String, Object> raw) {
            this.title = str(raw, "title", "&f이름 없는 퀘스트");
            this.description = list(raw, "description");
            this.progress = str(raw, "progress", "&7%value%/%target%");
            this.reward = str(raw, "reward", "&e보상 없음");
            this.category = str(raw, "category", "");
            this.difficulty = str(raw, "difficulty", "");
            this.icon = str(raw, "icon", "BOOK");
            this.hint = str(raw, "hint", "");
        }

        private static String str(Map<String, Object> m, String k, String def) {
            Object o = m.get(k);
            return (o == null) ? def : String.valueOf(o);
        }

        private static List<String> list(Map<String, Object> m, String k) {
            Object o = m.get(k);
            if (o instanceof List<?> l) {
                if (l.isEmpty()) return List.of();
                List<String> out = new ArrayList<>(l.size());
                for (Object x : l) out.add(String.valueOf(x));
                return List.copyOf(out);
            } else if (o instanceof String s) {
                return List.of(s);
            }
            return List.of();
        }
    }

    // ------------------------------------------------------------------------
    // Reset 내부 클래스
    // ------------------------------------------------------------------------
    public static final class Reset {
        public final String policy;
        public final String time;
        public Reset(String policy, String time) {
            this.policy = (policy == null) ? "" : policy.trim();
            this.time = (time == null) ? "" : time.trim();
        }
    }
}
