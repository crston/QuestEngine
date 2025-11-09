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

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ConditionEvaluator (개선 버전)
 * - 문자열, 숫자, 리플렉션(event.*) 접근, PAPI, ctx 모두 지원
 * - 자동 trim, 따옴표 제거, 공백 안전 처리
 * - Bukkit / Paper 완전 호환
 */
public final class ConditionEvaluator {

    private ConditionEvaluator() {}

    private static final boolean PAPI = Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null;
    private static final Map<String, Parsed> CACHE = new ConcurrentHashMap<>(512);

    private record Parsed(String left, String op, String right) {}

    private static final Set<String> OPS = Set.of("==", "!=", ">=", "<=", ">", "<");

    // ------------------------------------------------------------
    // 메인 평가
    // ------------------------------------------------------------
    public static boolean eval(Player p, Event e, Map<String, Object> ctx, String expr) {
        if (expr == null || expr.isEmpty()) return false;

        Parsed parsed = CACHE.computeIfAbsent(expr, ConditionEvaluator::parse);
        if (parsed == null) return false;

        String lv = resolve(p, e, ctx, parsed.left);
        String rv = stripQuotes(parsed.right);

        if (lv == null || rv == null) return false;
        lv = lv.trim();
        rv = rv.trim();

        Double ln = toNum(lv);
        Double rn = toNum(rv);
        if (ln != null && rn != null)
            return cmpNum(ln, rn, parsed.op);

        return cmpStr(lv, rv, parsed.op);
    }

    // ------------------------------------------------------------
    // 식 파서
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
        return null;
    }

    // ------------------------------------------------------------
    // 문자열 처리
    // ------------------------------------------------------------
    private static String stripQuotes(String s) {
        if (s == null) return "";
        s = s.trim();
        int len = s.length();
        if (len >= 2) {
            char first = s.charAt(0), last = s.charAt(len - 1);
            if ((first == '"' && last == '"') || (first == '\'' && last == '\''))
                return s.substring(1, len - 1);
        }
        return s;
    }

    private static Double toNum(String s) {
        try {
            return (s == null || s.isEmpty()) ? null : Double.parseDouble(s);
        } catch (Throwable ignored) {
            return null;
        }
    }

    // ------------------------------------------------------------
    // 비교 연산
    // ------------------------------------------------------------
    private static boolean cmpNum(double a, double b, String op) {
        return switch (op) {
            case "==" -> a == b;
            case "!=" -> a != b;
            case ">" -> a > b;
            case ">=" -> a >= b;
            case "<" -> a < b;
            case "<=" -> a <= b;
            default -> false;
        };
    }

    private static boolean cmpStr(String a, String b, String op) {
        if (a == null || b == null) return false;
        int cmp = a.compareToIgnoreCase(b);
        return switch (op) {
            case "==" -> cmp == 0;
            case "!=" -> cmp != 0;
            case ">" -> cmp > 0;
            case ">=" -> cmp >= 0;
            case "<" -> cmp < 0;
            case "<=" -> cmp <= 0;
            default -> false;
        };
    }

    // ------------------------------------------------------------
    // 값 해석
    // ------------------------------------------------------------
    private static String resolve(Player p, Event e, Map<String, Object> ctx, String token) {
        String t = token.trim();

        // 1. 점 표기법(event.xxx) 지원
        if (t.startsWith("event.")) {
            Object val = reflectChain(e, t.substring("event.".length()));
            return val == null ? "" : String.valueOf(val);
        }

        // 2. ctx 우선
        if (ctx != null && ctx.containsKey(t)) {
            Object v = ctx.get(t);
            if (v != null) return String.valueOf(v);
        }

        // 3. %placeholder% 형태
        if (t.startsWith("%") && t.endsWith("%")) {
            String key = t.substring(1, t.length() - 1);

            // ctx에서 찾기
            if (ctx != null) {
                Object v = ctx.get(key);
                if (v != null) return String.valueOf(v);
            }

            // 내장 변수
            String builtin = builtin(p, e, key);
            if (builtin != null) return builtin;

            // PlaceholderAPI
            if (PAPI)
                return PlaceholderAPI.setPlaceholders(p, "%" + key + "%");

            return "";
        }

        // 4. 기본 literal
        String builtin = builtin(p, e, t);
        if (builtin != null) return builtin;

        return t;
    }

    // ------------------------------------------------------------
    // event 필드 체인 접근기 (리플렉션)
    // ------------------------------------------------------------
    private static Object reflectChain(Object base, String chain) {
        if (base == null || chain == null || chain.isEmpty()) return null;
        Object cur = base;
        String[] parts = chain.split("\\.");
        try {
            for (String mName : parts) {
                String getter = "get" + Character.toUpperCase(mName.charAt(0)) + mName.substring(1);
                Method m = cur.getClass().getMethod(getter);
                cur = m.invoke(cur);
                if (cur == null) return null;
            }
            return cur;
        } catch (Throwable t) {
            return null;
        }
    }

    // ------------------------------------------------------------
    // 내장 변수
    // ------------------------------------------------------------
    private static String builtin(Player p, Event e, String key) {
        return switch (key) {
            case "player_name" -> p.getName();
            case "player_level" -> String.valueOf(p.getLevel());
            case "player_health" -> String.valueOf((int) p.getHealth());
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
