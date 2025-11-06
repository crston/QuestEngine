package com.gmail.bobason01.questengine.runtime;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * EventContextMapper
 * - Bukkit 이벤트 → 조건 평가용 컨텍스트 맵 자동 변환기
 * - 리플렉션 캐시 기반 극한의 성능 중심 버전
 */
public final class EventContextMapper {

    private EventContextMapper() {}

    // 클래스별 get 메서드 캐시
    private static final Map<Class<?>, Method[]> METHOD_CACHE = new ConcurrentHashMap<>(128);

    // 플레이어 추출 캐시
    private static final Map<Class<?>, Method> PLAYER_METHOD_CACHE = new ConcurrentHashMap<>(64);

    // ThreadLocal 재사용 맵 (GC 최소화)
    private static final ThreadLocal<Map<String, Object>> LOCAL_MAP =
            ThreadLocal.withInitial(() -> new HashMap<>(32));

    /**
     * 이벤트에서 자동 컨텍스트 추출
     * key: "event_fieldname" 형태
     */
    public static Map<String, Object> map(Event e) {
        if (e == null) return Collections.emptyMap();

        Map<String, Object> ctx = LOCAL_MAP.get();
        ctx.clear();

        Class<?> clz = e.getClass();
        Method[] methods = METHOD_CACHE.computeIfAbsent(clz, EventContextMapper::scanGetters);

        for (Method m : methods) {
            try {
                Object val = m.invoke(e);
                if (val == null) continue;
                String key = "event_" + m.getName().substring(3).toLowerCase(Locale.ROOT);
                ctx.put(key, val);
            } catch (Throwable ignored) {}
        }

        return new HashMap<>(ctx); // 반환 시 복사 (ThreadLocal 재사용)
    }

    /**
     * 플레이어 객체를 이벤트에서 자동 추출
     * getPlayer / getWhoClicked / getEntity 등 탐색
     */
    public static Player extractPlayer(Event e) {
        if (e == null) return null;
        Class<?> clz = e.getClass();

        Method m = PLAYER_METHOD_CACHE.computeIfAbsent(clz, k -> {
            for (Method md : k.getMethods()) {
                if (md.getParameterCount() != 0) continue;
                String n = md.getName();
                if (n.equalsIgnoreCase("getPlayer")
                        || n.equalsIgnoreCase("getWhoClicked")
                        || n.equalsIgnoreCase("getEntity")) {
                    return md;
                }
            }
            return null;
        });

        if (m != null) {
            try {
                Object v = m.invoke(e);
                if (v instanceof Player) return (Player) v;
            } catch (Throwable ignored) {}
        }
        return null;
    }

    /**
     * 클래스에서 유효한 getter 목록 스캔
     */
    private static Method[] scanGetters(Class<?> clz) {
        List<Method> list = new ArrayList<>(16);
        for (Method m : clz.getDeclaredMethods()) {
            if (!m.getName().startsWith("get")) continue;
            if (m.getParameterCount() != 0) continue;
            Class<?> rt = m.getReturnType();
            if (rt == Void.TYPE || rt == Method.class || rt == Class.class) continue;
            try {
                m.setAccessible(true);
                list.add(m);
            } catch (Throwable ignored) {}
        }
        return list.toArray(new Method[0]);
    }
}
