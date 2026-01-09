package com.gmail.bobason01.questengine.quest;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.*;

/**
 * QuestDef (Updated)
 * - Added 'requiredQuests' field for prerequisites.
 */
public final class QuestDef {

    public enum StartMode { NONE, AUTO, PUBLIC, NPC }

    // Core Metadata
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

    // Components
    public final Reset reset;
    public final Display display;
    public final CustomEventData custom;

    // Conditions
    public final List<String> condStart;
    public final List<String> condSuccess;
    public final List<String> condFail;

    // [NEW] Prerequisites
    public final List<String> requiredQuests;

    // Actions & Chain
    public final Map<String, List<String>> actions;
    public final String nextQuestOnComplete;

    // Hash Cache
    private final int hash;

    // --- Constructor (Full) ---
    public QuestDef(
            String id, String name, String event, List<String> targets,
            int amount, int repeat, int points, boolean isPublic, boolean party,
            String type, Reset reset, Display display, CustomEventData custom,
            List<String> condStart, List<String> condSuccess, List<String> condFail,
            List<String> requiredQuests, // [NEW]
            Map<String, List<String>> actions, String nextQuestOnComplete, StartMode startMode
    ) {
        this.id = safe(id).toLowerCase(Locale.ROOT);
        this.name = safe(name, this.id);
        this.event = safe(event, "CUSTOM").toUpperCase(Locale.ROOT);
        this.targets = safeList(targets);

        this.amount = Math.max(1, amount);
        this.repeat = repeat;
        this.points = Math.max(0, points);

        this.isPublic = isPublic;
        this.party = party;
        this.type = safe(type, "vanilla").toLowerCase(Locale.ROOT);
        this.startMode = (startMode == null) ? StartMode.NONE : startMode;

        this.reset = (reset == null) ? new Reset("", "") : reset;
        this.display = (display == null) ? new Display(Collections.emptyMap()) : display;
        this.custom = custom;

        this.condStart = safeList(condStart);
        this.condSuccess = safeList(condSuccess);
        this.condFail = safeList(condFail);

        this.requiredQuests = safeList(requiredQuests); // [NEW]

        this.actions = safeMap(actions);
        this.nextQuestOnComplete = safe(nextQuestOnComplete);

        // Hash Pre-computation
        this.hash = Objects.hash(this.id, this.event, this.amount, this.points, this.isPublic);
    }

    private static List<String> safeList(List<String> list) {
        return (list == null || list.isEmpty()) ? List.of() : List.copyOf(list);
    }

    private static Map<String, List<String>> safeMap(Map<String, List<String>> map) {
        if (map == null || map.isEmpty()) return Map.of();
        Map<String, List<String>> copy = new HashMap<>(map.size());
        map.forEach((k, v) -> {
            if (k != null && v != null && !v.isEmpty()) {
                copy.put(k.toLowerCase(Locale.ROOT), List.copyOf(v));
            }
        });
        return Map.copyOf(copy);
    }

    private static String safe(String s) { return s == null ? "" : s; }
    private static String safe(String s, String def) { return (s == null || s.isEmpty()) ? def : s; }

    @Override
    public int hashCode() { return hash; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof QuestDef)) return false;
        QuestDef other = (QuestDef) o;
        return this.id.equals(other.id);
    }

    public boolean hasTarget() { return !targets.isEmpty(); }

    public boolean matchesTarget(String candidate) {
        if (!hasTarget()) return true;
        if (candidate == null) return false;
        for (String t : targets) {
            if (t.equalsIgnoreCase(candidate)) return true;
        }
        return false;
    }

    // --- IO / Loader ---

    public static QuestDef load(File file) {
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);

        String id = file.getName().replace(".yml", "").toLowerCase(Locale.ROOT);
        String name = yml.getString("name", id);
        String event = yml.getString("event", "CUSTOM");
        String type = yml.getString("type", "vanilla");

        List<String> targets = new ArrayList<>();
        if (yml.isList("targets")) targets.addAll(yml.getStringList("targets"));
        else if (yml.isString("target")) targets.add(yml.getString("target"));

        int amount = yml.getInt("amount", 1);
        int repeat = yml.getInt("repeat", 0);
        int points = yml.getInt("points", 0);
        boolean pub = yml.getBoolean("public", false);
        boolean party = yml.getBoolean("party", false);

        StartMode mode = StartMode.NONE;
        try { mode = StartMode.valueOf(yml.getString("start_mode", "NONE").toUpperCase()); }
        catch (Exception ignored) {}

        Reset reset = new Reset(yml.getString("reset.policy"), yml.getString("reset.time"));
        Display display = new Display(readSection(yml.getConfigurationSection("display")));

        CustomEventData custom = null;
        if (yml.isConfigurationSection("custom_event_data")) {
            custom = CustomEventData.load(yml.getConfigurationSection("custom_event_data"));
        }

        List<String> cStart = yml.getStringList("conditions.start");
        List<String> cSucc = yml.getStringList("conditions.success");
        List<String> cFail = yml.getStringList("conditions.fail");

        // [NEW] Load required quests
        List<String> reqQuests = yml.getStringList("conditions.required_quests");

        Map<String, List<String>> actions = new HashMap<>();
        ConfigurationSection actSec = yml.getConfigurationSection("actions");
        if (actSec != null) {
            for (String key : actSec.getKeys(false)) {
                actions.put(key, actSec.getStringList(key));
            }
        }

        String next = yml.getString("chain.next", "");

        return new QuestDef(
                id, name, event, targets, amount, repeat, points, pub, party, type,
                reset, display, custom, cStart, cSucc, cFail, reqQuests, actions, next, mode
        );
    }

    private static Map<String, Object> readSection(ConfigurationSection sec) {
        if (sec == null) return Collections.emptyMap();
        Map<String, Object> map = new HashMap<>();
        for (String key : sec.getKeys(false)) {
            map.put(key.toLowerCase(Locale.ROOT), sec.get(key));
        }
        return map;
    }

    public static YamlConfiguration toYaml(QuestDef q) {
        YamlConfiguration yml = new YamlConfiguration();

        yml.set("id", q.id);
        yml.set("name", q.name);
        yml.set("event", q.event);
        yml.set("type", q.type);

        if (!q.targets.isEmpty()) yml.set("targets", q.targets);

        yml.set("amount", q.amount);
        yml.set("repeat", q.repeat);
        yml.set("points", q.points);
        yml.set("public", q.isPublic);
        yml.set("party", q.party);
        yml.set("start_mode", q.startMode.name());

        if (!q.nextQuestOnComplete.isEmpty()) yml.set("chain.next", q.nextQuestOnComplete);

        if (!q.reset.policy.isEmpty()) yml.set("reset.policy", q.reset.policy);
        if (!q.reset.time.isEmpty()) yml.set("reset.time", q.reset.time);

        if (q.display != null) {
            yml.set("display.title", q.display.title);
            if (!q.display.description.isEmpty()) yml.set("display.description", q.display.description);
            if (!q.display.progress.isEmpty()) yml.set("display.progress", q.display.progress);
            if (!q.display.reward.isEmpty()) yml.set("display.reward", q.display.reward);
            if (!q.display.category.isEmpty()) yml.set("display.category", q.display.category);
            if (!q.display.difficulty.isEmpty()) yml.set("display.difficulty", q.display.difficulty);
            yml.set("display.icon", q.display.icon);
            if (!q.display.hint.isEmpty()) yml.set("display.hint", q.display.hint);
            if (q.display.customModelData != -1) yml.set("display.customModelData", q.display.customModelData);
        }

        if (!q.condStart.isEmpty()) yml.set("conditions.start", q.condStart);
        if (!q.condSuccess.isEmpty()) yml.set("conditions.success", q.condSuccess);
        if (!q.condFail.isEmpty()) yml.set("conditions.fail", q.condFail);

        // [NEW] Save required quests
        if (!q.requiredQuests.isEmpty()) yml.set("conditions.required_quests", q.requiredQuests);

        q.actions.forEach((k, v) -> yml.set("actions." + k, v));

        if (q.custom != null) {
            yml.createSection("custom_event_data", q.custom.serialize());
        }

        return yml;
    }

    public static final class Display {
        public final String title, progress, reward, category, difficulty, icon, hint;
        public final List<String> description;
        public final int customModelData;

        public Display(Map<String, Object> map) {
            this.title = (String) map.getOrDefault("title", "&fNo Title");
            this.description = getList(map.get("description"));
            this.progress = (String) map.getOrDefault("progress", "&7%value%/%target%");
            this.reward = (String) map.getOrDefault("reward", "");
            this.category = (String) map.getOrDefault("category", "");
            this.difficulty = (String) map.getOrDefault("difficulty", "");
            this.icon = ((String) map.getOrDefault("icon", "BOOK")).toUpperCase(Locale.ROOT);
            this.hint = (String) map.getOrDefault("hint", "");
            this.customModelData = map.get("custommodeldata") instanceof Number n ? n.intValue() : -1;
        }

        @SuppressWarnings("unchecked")
        private static List<String> getList(Object obj) {
            return (obj instanceof List) ? (List<String>) obj : List.of();
        }
    }

    public static final class Reset {
        public final String policy, time;
        public Reset(String p, String t) {
            this.policy = safe(p);
            this.time = safe(t);
        }
    }
}