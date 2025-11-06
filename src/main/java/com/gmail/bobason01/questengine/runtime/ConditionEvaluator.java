package com.gmail.bobason01.questengine.runtime;

import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.player.PlayerFishEvent;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ConditionEvaluator
 * - 퀘스트 조건문 평가기
 * - PAPI, 내부 변수, 이벤트 기반 변수 모두 지원
 * - 캐시 기반 파싱 및 분기 최적화
 */
public final class ConditionEvaluator {

    private ConditionEvaluator() {}

    // ------------------------------------------------------------
    // 캐시 및 상수
    // ------------------------------------------------------------
    private static final boolean PAPI = Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null;
    private static final Map<String, Parsed> CACHE = new ConcurrentHashMap<>(512);

    private record Parsed(String left, String op, String right) {}

    private static final Set<String> OPS = Set.of("==", "!=", ">=", "<=", ">", "<");

    // ------------------------------------------------------------
    // 메인 평가 진입점
    // ------------------------------------------------------------
    public static boolean eval(Player p, Event e, Map<String, Object> ctx, String expr) {
        if (expr == null || expr.isEmpty()) return false;

        Parsed parsed = CACHE.computeIfAbsent(expr, ConditionEvaluator::parse);
        if (parsed == null) return false;

        String lv = resolve(p, e, ctx, parsed.left);
        String rv = stripQuotes(parsed.right);

        Double ln = toNum(lv);
        Double rn = toNum(rv);
        if (ln != null && rn != null)
            return cmpNum(ln, rn, parsed.op);

        return cmpStr(lv, rv, parsed.op);
    }

    // ------------------------------------------------------------
    // 파싱 캐시
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
    // 값 해석기 (context / builtin / papi)
    // ------------------------------------------------------------
    private static String resolve(Player p, Event e, Map<String, Object> ctx, String token) {
        String t = token.trim();

        if (!t.startsWith("%") || !t.endsWith("%"))
            return t; // 리터럴 값

        String key = t.substring(1, t.length() - 1);

        // context 우선
        if (ctx != null) {
            Object v = ctx.get(key);
            if (v != null) return String.valueOf(v);
        }

        // built-in fast path
        String builtin = builtin(p, e, key);
        if (builtin != null) return builtin;

        // PlaceholderAPI fallback
        if (PAPI)
            return PlaceholderAPI.setPlaceholders(p, "%" + key + "%");

        return "";
    }

    // ------------------------------------------------------------
    // 내장 변수 해석 (성능 위주, if-chain 제거)
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
