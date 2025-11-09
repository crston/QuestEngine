package com.gmail.bobason01.questengine.quest;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.*;

/**
 * QuestDef
 * - 퀘스트 정의 데이터 클래스 (불변)
 * - 모든 필드는 로드 시 확정되어 이후 변경 불가
 * - extreme performance 기반 구조 (copy-once immutable)
 */
public final class QuestDef {

    public final String id;
    public final String name;
    public final String event;
    public final List<String> targets;
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
    public final int hash;

    public QuestDef(
            String id,
            String name,
            String event,
            List<String> targets,
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
            Map<String, List<String>> actions
    ) {
        this.id = id.intern();
        this.name = name;
        this.event = event.toUpperCase(Locale.ROOT).intern();
        this.targets = (targets == null || targets.isEmpty()) ? List.of() : List.copyOf(targets);
        this.amount = amount;
        this.repeat = repeat;
        this.points = points;
        this.isPublic = isPublic;
        this.party = party;
        this.type = (type == null ? "vanilla" : type.toLowerCase(Locale.ROOT).intern());
        this.reset = reset;
        this.display = display;
        this.custom = custom;
        this.condSuccess = (condSuccess == null || condSuccess.isEmpty()) ? List.of() : List.copyOf(condSuccess);
        this.condFail = (condFail == null || condFail.isEmpty()) ? List.of() : List.copyOf(condFail);
        this.actions = (actions == null || actions.isEmpty()) ? Map.of() : Map.copyOf(actions);

        this.hash = computeHash();
    }

    private int computeHash() {
        int h = id.hashCode();
        h = 31 * h + event.hashCode();
        for (String t : targets) {
            h = 31 * h + t.hashCode();
        }
        h = 31 * h + amount;
        h = 31 * h + points;
        return h;
    }

    public boolean hasTarget() {
        return !targets.isEmpty();
    }

    public boolean matchesTarget(String candidate) {
        if (!hasTarget()) return true;
        if (candidate == null) return false;
        for (String t : targets) {
            if (t.equalsIgnoreCase(candidate)) return true;
        }
        return false;
    }

    // ============================================================
    // YAML 로드
    // ============================================================
    public static QuestDef load(File file) {
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        String id = file.getName().replace(".yml", "");
        String name = yml.getString("name", id);
        String event = yml.getString("event", "CUSTOM");
        int amount = yml.getInt("amount", 1);
        int repeat = yml.getInt("repeat", 0);
        int points = yml.getInt("points", 0);
        boolean pub = yml.getBoolean("public", false);
        boolean party = yml.getBoolean("party", false);
        String type = yml.getString("type", "vanilla");

        Reset reset = new Reset(
                yml.getString("reset.policy", ""),
                yml.getString("reset.time", "")
        );

        Display display = new Display(readDisplay(yml.getConfigurationSection("display")));

        CustomEventData custom = null;
        ConfigurationSection ce = yml.getConfigurationSection("custom_event_data");
        if (ce != null) custom = CustomEventData.load(ce);

        List<String> condSuccess = yml.getStringList("conditions.success");
        List<String> condFail = yml.getStringList("conditions.fail");

        List<String> targets = new ArrayList<>();
        if (yml.contains("targets")) {
            for (String s : yml.getStringList("targets")) {
                if (s != null && !s.isBlank()) targets.add(s.trim());
            }
        } else if (yml.contains("target")) {
            String single = yml.getString("target", "").trim();
            if (!single.isEmpty()) targets.add(single);
        }

        Map<String, List<String>> actions = readActions(yml.getConfigurationSection("actions"));

        return new QuestDef(
                id,
                name,
                event,
                targets,
                amount,
                repeat,
                points,
                pub,
                party,
                type,
                reset,
                display,
                custom,
                condSuccess,
                condFail,
                actions
        );
    }

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

    // ============================================================
    // 게터 (GUI 및 외부 참조용)
    // ============================================================
    public boolean isPublic() {
        return isPublic;
    }

    public Display display() {
        return display;
    }

    // ============================================================
    // 내부 클래스
    // ============================================================
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
            this.title = str(raw, "title", "&fNo Name Quest");
            this.description = list(raw, "description");
            this.progress = str(raw, "progress", "&7%value%/%target%");
            this.reward = str(raw, "reward", "&eNo Rewards");
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
            if (o instanceof List<?>) {
                List<?> l = (List<?>) o;
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

    public static final class Reset {
        public final String policy;
        public final String time;

        public Reset(String policy, String time) {
            this.policy = (policy == null) ? "" : policy.trim();
            this.time = (time == null) ? "" : time.trim();
        }
    }
}
