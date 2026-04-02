package com.gmail.bobason01.questengine.runtime;

import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.player.PlayerFishEvent;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class ConditionEvaluator {

    private ConditionEvaluator() {}

    private static final boolean PAPI = Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI");

    // 식 파싱 캐시
    private static final Map<String, Parsed> EXPR_CACHE = new ConcurrentHashMap<>(512);

    // 리플렉션 핸들 캐시 (핵심 최적화)
    private static final Map<String, MethodHandle[]> REFLECTION_CACHE = new ConcurrentHashMap<>();
    private static final MethodHandles.Lookup LOOKUP = MethodHandles.publicLookup();

    private record Parsed(String left, String op, String right) {}

    // 연산자 길이가 긴 것부터 검사해야 함 (ex: >= 가 > 보다 먼저)
    private static final List<String> OPS = List.of("==", "!=", ">=", "<=", ">", "<");

    // ------------------------------------------------------------
    // 메인 평가
    // ------------------------------------------------------------
    public static boolean eval(Player p, Event e, Map<String, Object> ctx, String expr) {
        if (expr == null || expr.isEmpty()) return false;

        Parsed parsed = EXPR_CACHE.computeIfAbsent(expr, ConditionEvaluator::parse);
        if (parsed == null) return false;

        String lv = resolve(p, e, ctx, parsed.left);
        String rv = stripQuotes(parsed.right); // 우측 값은 보통 고정값이므로 단순 처리

        // 둘 중 하나라도 없으면 false (null safe)
        if (lv == null || rv == null) return false;

        // 숫자 비교 시도
        Double ln = toNum(lv);
        Double rn = toNum(rv);
        if (ln != null && rn != null) {
            return cmpNum(ln, rn, parsed.op);
        }

        // 문자열 비교
        return cmpStr(lv, rv, parsed.op);
    }

    // ------------------------------------------------------------
    // 파서 (1회만 실행되고 캐싱됨)
    // ------------------------------------------------------------
    private static Parsed parse(String s) {
        for (String op : OPS) {
            int idx = s.indexOf(op);
            if (idx >= 0) {
                String left = s.substring(0, idx).trim();
                String right = s.substring(idx + op.length()).trim();
                return new Parsed(left, op, right);
            }
        }
        return null; // 연산자가 없는 경우 (단순 boolean 체크 등은 여기서 확장 가능)
    }

    // ------------------------------------------------------------
    // 유틸리티
    // ------------------------------------------------------------
    private static String stripQuotes(String s) {
        if (s == null) return "";
        s = s.trim();
        int len = s.length();
        if (len >= 2) {
            char first = s.charAt(0);
            char last = s.charAt(len - 1);
            if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                return s.substring(1, len - 1);
            }
        }
        return s;
    }

    private static Double toNum(String s) {
        if (s == null || s.isEmpty()) return null;
        try {
            // 빠른 검사: 첫 글자가 숫자나 -, . 인지 확인
            char c = s.charAt(0);
            if (c != '-' && c != '.' && (c < '0' || c > '9')) return null;
            return Double.parseDouble(s);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    // ------------------------------------------------------------
    // 비교 로직
    // ------------------------------------------------------------
    private static boolean cmpNum(double a, double b, String op) {
        return switch (op) {
            case "==" -> Math.abs(a - b) < 0.000001; // 부동소수점 오차 보정
            case "!=" -> Math.abs(a - b) > 0.000001;
            case ">" -> a > b;
            case ">=" -> a >= b;
            case "<" -> a < b;
            case "<=" -> a <= b;
            default -> false;
        };
    }

    private static boolean cmpStr(String a, String b, String op) {
        int cmp = a.compareToIgnoreCase(b);
        return switch (op) {
            case "==" -> cmp == 0;
            case "!=" -> cmp != 0;
            case ">" -> cmp > 0; // 사전순 비교
            case ">=" -> cmp >= 0;
            case "<" -> cmp < 0;
            case "<=" -> cmp <= 0;
            default -> false;
        };
    }

    // ------------------------------------------------------------
    // 값 해석 (Resolver)
    // ------------------------------------------------------------
    private static String resolve(Player p, Event e, Map<String, Object> ctx, String token) {
        // 1. Context (가장 빠름)
        if (ctx != null) {
            Object v = ctx.get(token);
            if (v != null) return String.valueOf(v);
        }

        // 2. Event Reflection (Optimized)
        if (token.startsWith("event.")) {
            // "event." (6글자) 제외하고 넘김
            Object val = evalEventChain(e, token.substring(6));
            return val == null ? "null" : String.valueOf(val);
        }

        // 3. Placeholder (%...%)
        if (token.length() > 2 && token.charAt(0) == '%' && token.charAt(token.length() - 1) == '%') {
            String key = token.substring(1, token.length() - 1);

            // Context 재확인 (placeholder 내부 키)
            if (ctx != null) {
                Object v = ctx.get(key);
                if (v != null) return String.valueOf(v);
            }

            // Built-in
            String bi = builtin(p, e, key);
            if (bi != null) return bi;

            // PAPI
            if (PAPI && p != null) {
                return PlaceholderAPI.setPlaceholders(p, token);
            }
            return "";
        }

        // 4. Built-in direct check
        String bi = builtin(p, e, token);
        if (bi != null) return bi;

        return token;
    }

    // ------------------------------------------------------------
    // MethodHandle 기반 고성능 리플렉션
    // ------------------------------------------------------------
    private static Object evalEventChain(Object root, String chain) {
        if (root == null || chain == null || chain.isEmpty()) return null;

        // 캐시 키: "클래스명#체인" (ex: "org.bukkit.event.player.PlayerMoveEvent#player.location.x")
        String cacheKey = root.getClass().getName() + "#" + chain;
        MethodHandle[] handles = REFLECTION_CACHE.get(cacheKey);

        try {
            if (handles == null) {
                // 캐시 미스: 핸들 생성 (무거운 작업, 최초 1회만 실행)
                String[] parts = chain.split("\\."); // 여기서는 split 써도 됨 (캐싱되므로)
                List<MethodHandle> list = new ArrayList<>();
                Class<?> current = root.getClass();

                for (String part : parts) {
                    String name = part.trim();
                    if (name.isEmpty()) return null;

                    // 메소드 찾기 시도: getXxx -> isXxx -> xxx 순서
                    Method m = findMethodSmart(current, name);
                    if (m == null) return null;

                    m.setAccessible(true);
                    list.add(LOOKUP.unreflect(m));
                    current = m.getReturnType();
                }
                handles = list.toArray(new MethodHandle[0]);
                REFLECTION_CACHE.put(cacheKey, handles);
            }

            // 핸들 실행 (매우 빠름)
            Object current = root;
            for (MethodHandle mh : handles) {
                if (current == null) return null;
                current = mh.invoke(current);
            }
            return current;

        } catch (Throwable t) {
            return null;
        }
    }

    private static Method findMethodSmart(Class<?> clazz, String name) {
        // 1. getXxx()
        String getter = "get" + Character.toUpperCase(name.charAt(0)) + name.substring(1);
        try { return clazz.getMethod(getter); } catch (NoSuchMethodException ignored) {}

        // 2. isXxx() (boolean)
        String isser = "is" + Character.toUpperCase(name.charAt(0)) + name.substring(1);
        try { return clazz.getMethod(isser); } catch (NoSuchMethodException ignored) {}

        // 3. xxx() (record style or direct)
        try { return clazz.getMethod(name); } catch (NoSuchMethodException ignored) {}

        return null;
    }

    // ------------------------------------------------------------
    // 내장 변수 (확장)
    // ------------------------------------------------------------
    private static String builtin(Player p, Event e, String key) {
        if (p == null) return null;
        return switch (key) {
            case "player_name" -> p.getName();
            case "player_uuid" -> p.getUniqueId().toString();
            case "player_level" -> String.valueOf(p.getLevel());
            case "player_xp" -> String.valueOf(p.getTotalExperience());
            case "player_health" -> String.valueOf((int) p.getHealth());
            case "player_food" -> String.valueOf(p.getFoodLevel());
            case "player_world" -> p.getWorld().getName();
            case "player_x" -> String.valueOf(p.getLocation().getBlockX());
            case "player_y" -> String.valueOf(p.getLocation().getBlockY());
            case "player_z" -> String.valueOf(p.getLocation().getBlockZ());

            case "block_type" -> {
                if (e instanceof BlockBreakEvent b) yield b.getBlock().getType().name();
                if (e instanceof BlockPlaceEvent b) yield b.getBlock().getType().name();
                yield null;
            }
            case "entity_type" -> {
                if (e instanceof EntityDeathEvent d) yield d.getEntity().getType().name();
                if (e instanceof PlayerFishEvent f && f.getCaught() != null)
                    yield f.getCaught().getType().name();
                yield null;
            }
            case "item_type" -> {
                if (e instanceof CraftItemEvent ci)
                    yield ci.getRecipe().getResult().getType().name();
                yield null;
            }
            default -> null;
        };
    }
}