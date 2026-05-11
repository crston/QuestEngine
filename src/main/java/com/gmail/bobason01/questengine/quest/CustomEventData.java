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
        if (sec == null) return null; // 섹션이 없으면 명확하게 null 반환

        // YAML 키 이름 읽기 (UpgradeResultEvent 예시의 키와 일치시킴)
        String eventClass = sec.getString("event", sec.getString("event_class", "")).trim();

        // 필수 값인 eventClass가 없으면 로드 실패로 간주하고 null 반환
        if (eventClass.isEmpty()) return null;

        String playerGetter = sec.getString("player_variable", sec.getString("player_getter", "getPlayer()")).trim();

        Map<String, String> map = new LinkedHashMap<>(8);

        // variables_to_capture와 captures 두 키 모두 지원
        List<?> raw = sec.getList("variables_to_capture");
        if (raw == null || raw.isEmpty()) raw = sec.getList("captures");

        if (raw != null && !raw.isEmpty()) {
            for (Object obj : raw) {
                if (!(obj instanceof String s)) continue;
                s = s.trim();
                if (s.isEmpty()) continue;

                int semi = s.indexOf(';');
                if (semi <= 0 || semi == s.length() - 1) continue;

                String key = s.substring(0, semi).trim();
                String chain = s.substring(semi + 1).trim();

                // % 기호 제거 (%upgrade_count% -> upgrade_count)
                if (key.startsWith("%") && key.endsWith("%") && key.length() > 2) {
                    key = key.substring(1, key.length() - 1);
                }

                // Chaining 로직을 위해 메서드 이름에서 ()와 ; 제거 (getUpgradeCount() -> getUpgradeCount)
                chain = chain.replace("(", "").replace(")", "").replace(";", "").trim();

                if (!key.isEmpty() && !chain.isEmpty()) {
                    map.put(key, chain);
                }
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
                lines.add("%" + e.getKey() + "%;" + e.getValue());
            }
            out.put("variables_to_capture", lines);
        }
        return out;
    }
}