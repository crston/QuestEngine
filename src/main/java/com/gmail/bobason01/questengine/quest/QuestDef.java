package com.gmail.bobason01.questengine.quest;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.*;

/**
 * QuestDef
 * - 완전 불변 데이터 모델
 * - Engine과 완전 호환 (start_mode, reset, display, conditions, actions 포함)
 * - Display에 customModelData 지원
 * - 19-인자 최신 생성자 및 구버전 호환 생성자 제공
 */
public final class QuestDef {

    // ------------------------------------------------------------
    // Enum
    // ------------------------------------------------------------
    public enum StartMode {
        NONE, AUTO, PUBLIC, NPC
    }

    // ------------------------------------------------------------
    // 기본 메타
    // ------------------------------------------------------------
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
    public final StartMode startMode;

    // 리셋/표시/커스텀
    public final Reset reset;
    public final Display display;
    public final CustomEventData custom; // 외부 클래스(이미 프로젝트에 존재)

    // 컨디션
    public final List<String> condStart;
    public final List<String> condSuccess;
    public final List<String> condFail;

    // 액션
    public final Map<String, List<String>> actions;

    // 체인
    public final String nextQuestOnComplete;

    // 해시 캐시
    public final int hash;

    // ------------------------------------------------------------
    // 19-인자 최신 생성자
    // ------------------------------------------------------------
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
            List<String> condStart,
            List<String> condSuccess,
            List<String> condFail,
            Map<String, List<String>> actions,
            String nextQuestOnComplete,
            StartMode startMode
    ) {
        this.id = nonNullLower(id);
        this.name = safe(name, this.id);
        this.event = safe(event, "CUSTOM").toUpperCase(Locale.ROOT).intern();
        this.targets = (targets == null || targets.isEmpty()) ? List.of() : List.copyOf(targets);
        this.amount = Math.max(1, amount);
        this.repeat = repeat;
        this.points = Math.max(0, points);
        this.isPublic = isPublic;
        this.party = party;
        this.type = safe(type, "vanilla").toLowerCase(Locale.ROOT).intern();
        this.reset = (reset == null) ? new Reset("", "") : reset;
        this.display = (display == null) ? new Display(Map.of()) : display;
        this.custom = custom;
        this.startMode = (startMode == null) ? StartMode.NONE : startMode;

        this.condStart = (condStart == null || condStart.isEmpty()) ? List.of() : List.copyOf(condStart);
        this.condSuccess = (condSuccess == null || condSuccess.isEmpty()) ? List.of() : List.copyOf(condSuccess);
        this.condFail = (condFail == null || condFail.isEmpty()) ? List.of() : List.copyOf(condFail);

        if (actions == null || actions.isEmpty()) {
            this.actions = Map.of();
        } else {
            Map<String, List<String>> tmp = new LinkedHashMap<>(actions.size());
            for (Map.Entry<String, List<String>> e : actions.entrySet()) {
                String k = (e.getKey() == null) ? "" : e.getKey().toLowerCase(Locale.ROOT);
                List<String> v = (e.getValue() == null || e.getValue().isEmpty()) ? List.of() : List.copyOf(e.getValue());
                if (!k.isEmpty() && !v.isEmpty()) tmp.put(k, v);
            }
            this.actions = Map.copyOf(tmp);
        }

        this.nextQuestOnComplete = safe(nextQuestOnComplete);
        this.hash = computeHash();
    }

    // ------------------------------------------------------------
    // 구버전(16인자) 호환 생성자
    // ------------------------------------------------------------
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
        this(
                id, name, event, targets, amount, repeat, points,
                isPublic, party, type, reset, display, custom,
                List.of(), // condStart 기본값
                condSuccess, condFail,
                actions,
                "", // nextQuestOnComplete
                StartMode.NONE // 기본
        );
    }

    // ------------------------------------------------------------
    // 해시 계산
    // ------------------------------------------------------------
    private int computeHash() {
        int h = id.hashCode();
        h = 31 * h + event.hashCode();
        for (String t : targets) h = 31 * h + t.hashCode();
        h = 31 * h + amount;
        h = 31 * h + points;
        h = 31 * h + (isPublic ? 1 : 0);
        h = 31 * h + (party ? 1 : 0);
        return h;
    }

    // ------------------------------------------------------------
    // 헬퍼
    // ------------------------------------------------------------
    public boolean hasTarget() { return !targets.isEmpty(); }

    public boolean matchesTarget(String candidate) {
        if (!hasTarget()) return true;
        if (candidate == null) return false;
        for (String t : targets) if (t.equalsIgnoreCase(candidate)) return true;
        return false;
    }

    // ------------------------------------------------------------
    // 정적 로더 (Engine 호환)
    // ------------------------------------------------------------
    public static QuestDef loadFromFile(File file) {
        return load(file);
    }

    public static QuestDef load(File file) {
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        String id = stripExt(file.getName()).toLowerCase(Locale.ROOT);
        String name = yml.getString("name", id);
        String event = yml.getString("event", "CUSTOM");

        List<String> targets = new ArrayList<>();
        if (yml.contains("targets")) {
            for (String s : yml.getStringList("targets"))
                if (s != null && !s.isBlank()) targets.add(s.trim());
        } else if (yml.contains("target")) {
            String single = yml.getString("target", "").trim();
            if (!single.isEmpty()) targets.add(single);
        }

        int amount = yml.getInt("amount", 1);
        int repeat = yml.getInt("repeat", 0);
        int points = yml.getInt("points", 0);
        boolean pub = yml.getBoolean("public", false);
        boolean party = yml.getBoolean("party", false);
        String type = yml.getString("type", "vanilla");

        // start_mode
        String startModeStr = yml.getString("start_mode", "NONE").toUpperCase(Locale.ROOT);
        StartMode mode;
        try {
            mode = StartMode.valueOf(startModeStr);
        } catch (IllegalArgumentException ex) {
            mode = StartMode.NONE;
        }

        Reset reset = new Reset(
                yml.getString("reset.policy", ""),
                yml.getString("reset.time", "")
        );

        Display display = new Display(readDisplay(yml.getConfigurationSection("display")));

        CustomEventData custom = null;
        ConfigurationSection ce = yml.getConfigurationSection("custom_event_data");
        if (ce != null) custom = CustomEventData.load(ce);

        List<String> condStart = yml.getStringList("conditions.start");
        List<String> condSuccess = yml.getStringList("conditions.success");
        List<String> condFail = yml.getStringList("conditions.fail");

        Map<String, List<String>> actions = readActions(yml.getConfigurationSection("actions"));
        String next = yml.getString("chain.next", "");

        return new QuestDef(
                id, name, event, targets, amount, repeat, points,
                pub, party, type, reset, display, custom,
                condStart, condSuccess, condFail, actions, next, mode
        );
    }

    // ------------------------------------------------------------
    // 내부 헬퍼
    // ------------------------------------------------------------
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

        for (String key : sec.getKeys(false)) {
            Object raw = sec.get(key);
            List<String> list = new ArrayList<>();

            if (raw instanceof List<?>) {
                for (Object o : (List<?>) raw) {
                    if (o != null) list.add(String.valueOf(o));
                }
            } else if (raw instanceof String s) {
                list.add(s);
            } else if (raw != null) {
                list.add(String.valueOf(raw));
            }

            if (!list.isEmpty()) {
                map.put(key.toLowerCase(Locale.ROOT), List.copyOf(list));
            }
        }
        return map;
    }

    private static String stripExt(String name) {
        int dot = (name == null) ? -1 : name.lastIndexOf('.');
        return (dot > 0) ? name.substring(0, dot) : safe(name);
    }

    private static String nonNullLower(String s) {
        return safe(s).toLowerCase(Locale.ROOT);
    }

    private static String safe(String s) {
        return (s == null) ? "" : s;
    }

    private static String safe(String s, String def) {
        String x = safe(s);
        return x.isEmpty() ? def : x;
    }

    // ------------------------------------------------------------
    // 게터 보조
    // ------------------------------------------------------------
    public boolean isPublic() { return isPublic; }
    public Display display() { return display; }

    // ------------------------------------------------------------
    // Display 중첩 클래스
    // ------------------------------------------------------------
    public static final class Display {
        public final String title;
        public final List<String> description;
        public final String progress;
        public final String reward;
        public final String category;
        public final String difficulty;
        public final String icon;
        public final String hint;
        public final int customModelData;

        public Display(Map<String, Object> raw) {
            String title0 = str(raw, "title", "&fNo Name Quest");
            this.title = title0;
            this.description = list(raw, "description");
            this.progress = str(raw, "progress", "&7%value%/%target%");
            this.reward = str(raw, "reward", "");
            this.category = str(raw, "category", "");
            this.difficulty = str(raw, "difficulty", "");
            this.icon = str(raw, "icon", "BOOK").toUpperCase(Locale.ROOT);
            this.hint = str(raw, "hint", "");
            this.customModelData = intOr(raw, "custommodeldata", -1);
        }

        private static String str(Map<String, Object> m, String k, String def) {
            if (m == null) return def;
            Object o = m.get(k);
            return (o == null) ? def : String.valueOf(o);
        }

        private static List<String> list(Map<String, Object> m, String k) {
            if (m == null) return List.of();
            Object o = m.get(k);
            if (o instanceof List<?>) {
                List<?> l = (List<?>) o;
                if (l.isEmpty()) return List.of();
                List<String> out = new ArrayList<>(l.size());
                for (Object x : l) out.add(String.valueOf(x));
                return List.copyOf(out);
            } else if (o instanceof String s) {
                return s.isBlank() ? List.of() : List.of(s);
            }
            return List.of();
        }

        private static int intOr(Map<String, Object> m, String k, int def) {
            if (m == null) return def;
            Object o = m.get(k);
            if (o instanceof Number n) return n.intValue();
            if (o instanceof String s) {
                try { return Integer.parseInt(s.trim()); } catch (Exception ignored) {}
            }
            return def;
        }
    }

    // ------------------------------------------------------------
    // Reset 중첩 클래스
    // ------------------------------------------------------------
    public static final class Reset {
        public final String policy;
        public final String time;

        public Reset(String policy, String time) {
            this.policy = (policy == null) ? "" : policy.trim();
            this.time = (time == null) ? "" : time.trim();
        }
    }
    public static org.bukkit.configuration.file.YamlConfiguration toYaml(QuestDef q) {
        org.bukkit.configuration.file.YamlConfiguration yml = new org.bukkit.configuration.file.YamlConfiguration();

        yml.set("id", q.id);
        yml.set("name", q.name);
        yml.set("event", q.event);
        yml.set("type", q.type);
        yml.set("targets", q.targets);
        yml.set("amount", q.amount);
        yml.set("repeat", q.repeat);
        yml.set("points", q.points);
        yml.set("public", q.isPublic);
        yml.set("party", q.party);
        yml.set("start_mode", q.startMode.name());
        if (q.nextQuestOnComplete != null && !q.nextQuestOnComplete.isEmpty())
            yml.set("chain.next", q.nextQuestOnComplete);

        // reset
        if (q.reset != null) {
            yml.set("reset.policy", q.reset.policy);
            yml.set("reset.time", q.reset.time);
        }

        // display
        if (q.display != null) {
            yml.set("display.title", q.display.title);
            if (!q.display.description.isEmpty()) yml.set("display.description", q.display.description);
            if (q.display.progress != null && !q.display.progress.isEmpty()) yml.set("display.progress", q.display.progress);
            if (q.display.reward != null && !q.display.reward.isEmpty()) yml.set("display.reward", q.display.reward);
            if (q.display.category != null && !q.display.category.isEmpty()) yml.set("display.category", q.display.category);
            if (q.display.difficulty != null && !q.display.difficulty.isEmpty()) yml.set("display.difficulty", q.display.difficulty);
            if (q.display.icon != null && !q.display.icon.isEmpty()) yml.set("display.icon", q.display.icon);
            if (q.display.hint != null && !q.display.hint.isEmpty()) yml.set("display.hint", q.display.hint);
            if (q.display.customModelData != -1) yml.set("display.customModelData", q.display.customModelData);
        }

        // conditions
        if (!q.condStart.isEmpty()) yml.set("conditions.start", q.condStart);
        if (!q.condSuccess.isEmpty()) yml.set("conditions.success", q.condSuccess);
        if (!q.condFail.isEmpty()) yml.set("conditions.fail", q.condFail);

        // actions
        if (q.actions != null && !q.actions.isEmpty()) {
            for (Map.Entry<String, List<String>> e : q.actions.entrySet()) {
                yml.set("actions." + e.getKey(), e.getValue());
            }
        }

        // custom_event_data
        if (q.custom != null) {
            try {
                Map<String, Object> serialized = q.custom.serialize();
                if (!serialized.isEmpty()) yml.createSection("custom_event_data", serialized);
            } catch (Throwable ignored) {}
        }

        return yml;
    }
}
