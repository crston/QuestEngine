package com.gmail.bobason01.questengine.quest;

import org.bukkit.configuration.ConfigurationSection;

import java.util.*;

public final class CustomEventData {

    public final String eventClass;
    public final String playerGetter;
    public final Map<String, String> captures;

    private static final Map<String, String> EMPTY_MAP = Collections.emptyMap();

    public CustomEventData(String eventClass, String playerGetter, Map<String, String> captures) {
        this.eventClass = eventClass;
        this.playerGetter = playerGetter;
        this.captures = (captures == null || captures.isEmpty()) ? EMPTY_MAP : captures;
    }

    public static CustomEventData load(ConfigurationSection sec) {
        if (sec == null) return new CustomEventData("", "getPlayer()", EMPTY_MAP);

        String eventClass = sec.getString("event", "").trim();
        String playerGetter = sec.getString("player_variable", "getPlayer()").trim();

        Map<String, String> map = new LinkedHashMap<>(8);
        List<?> raw = sec.getList("variables_to_capture");
        if (raw != null && !raw.isEmpty()) {
            for (Object obj : raw) {
                if (!(obj instanceof String s)) continue;
                s = s.trim();
                if (s.isEmpty()) continue;

                int semi = s.indexOf(';');
                if (semi <= 0 || semi == s.length() - 1) continue;

                String key = s.substring(0, semi).trim();
                String chain = s.substring(semi + 1).trim();

                int len = key.length();
                if (len > 2 && key.charAt(0) == '%' && key.charAt(len - 1) == '%')
                    key = key.substring(1, len - 1);

                if (!key.isEmpty() && !chain.isEmpty())
                    map.put(key, chain);
            }
        }

        return new CustomEventData(eventClass, playerGetter, map);
    }

    @Override
    public String toString() {
        return "CustomEventData{" +
                "eventClass='" + eventClass + '\'' +
                ", playerGetter='" + playerGetter + '\'' +
                ", captures=" + captures.size() +
                '}';
    }

    public Map<String, Object> serialize() {
        Map<String, Object> out = new LinkedHashMap<>();

        out.put("event", eventClass);
        out.put("player_variable", playerGetter);

        if (captures != null && !captures.isEmpty()) {
            List<String> lines = new ArrayList<>(captures.size());
            for (Map.Entry<String, String> e : captures.entrySet()) {
                String key = e.getKey();
                if (key != null && !key.isEmpty()) {
                    lines.add("%" + key + "%;" + e.getValue());
                }
            }
            out.put("variables_to_capture", lines);
        }

        return out;
    }
}