package com.gmail.bobason01.questengine.quest;

import org.bukkit.configuration.ConfigurationSection;

import java.util.*;

/**
 * CustomEventData
 * - YAML 로드 시 이벤트 클래스와 변수 캡처 규칙을 정의
 * - 런타임 리플렉션 기반 이벤트 감지용
 * - 서버 부팅 시 대량 로드 대비 GC-free 구조
 */
public final class CustomEventData {

    /** 이벤트 클래스의 FQCN (ex: org.bukkit.event.player.PlayerJoinEvent) */
    public final String eventClass;

    /** 플레이어 객체를 가져올 메서드 경로 (예: getPlayer()) */
    public final String playerGetter;

    /** %변수명% → 필드/메서드 체인 (ex: "%block_type%" -> "getBlock().getType().name()") */
    public final Map<String, String> captures;

    private static final Map<String, String> EMPTY_MAP = Collections.emptyMap();

    public CustomEventData(String eventClass, String playerGetter, Map<String, String> captures) {
        this.eventClass = eventClass;
        this.playerGetter = playerGetter;
        this.captures = (captures == null || captures.isEmpty()) ? EMPTY_MAP : captures;
    }

    /**
     * YAML 로부터 CustomEventData를 파싱한다.
     * 구조 예시:
     * event: org.bukkit.event.player.PlayerJoinEvent
     * player_variable: getPlayer()
     * variables_to_capture:
     *   - "%player_name%;getPlayer().getName()"
     *   - "%world%;getPlayer().getWorld().getName()"
     */
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

    // ------------------------------------------------------------
    // 직렬화 (QuestDef.toYaml 등에서 사용)
    // ------------------------------------------------------------
    public Map<String, Object> serialize() {
        Map<String, Object> out = new LinkedHashMap<>();

        out.put("event", eventClass);
        out.put("player_variable", playerGetter);

        if (captures != null && !captures.isEmpty()) {
            List<String> lines = new ArrayList<>(captures.size());
            for (Map.Entry<String, String> e : captures.entrySet()) {
                String key = e.getKey();
                if (key != null && !key.isEmpty()) {
                    // 저장 시 %변수명%;getSomething() 형태로 변환
                    lines.add("%" + key + "%;" + e.getValue());
                }
            }
            out.put("variables_to_capture", lines);
        }

        return out;
    }
}
