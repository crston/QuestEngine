package com.gmail.bobason01.questengine.runtime;

import net.citizensnpcs.api.CitizensAPI;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.player.PlayerInteractEntityEvent;

import java.util.Locale;

/**
 * TargetMatchers
 * 이벤트별 타깃 비교 로직
 * - ENTITY_INTERACT: Citizens NPC / 일반 엔티티 식별 지원
 * - BLOCK_BREAK 등은 Engine 쪽에서 등록
 */
public final class TargetMatchers {

    private TargetMatchers() {}

    /**
     * ENTITY_INTERACT 매칭기
     * targets:
     *   - CITIZENS_1 → Citizens NPC ID 1번 클릭 시 매칭
     *   - ZOMBIE → 일반 좀비 클릭
     *   - VILLAGER → 주민 클릭
     */
    public static final Engine.TargetMatcher ENTITY_INTERACT_MATCHER = (p, e, t) -> {
        if (!(e instanceof PlayerInteractEntityEvent ie)) return false;
        Entity clicked = ie.getRightClicked();
        if (clicked == null) return false;

        // Citizens NPC 매칭 (모든 registry 순회)
        try {
            for (var reg : CitizensAPI.getNPCRegistries()) {
                var npc = reg.getNPC(clicked);
                if (npc != null) {
                    String npcKey = "CITIZENS_" + npc.getId();
                    if (npcKey.equalsIgnoreCase(t)) {
                        return true;
                    }
                }
            }
        } catch (Throwable ignored) {
            // Citizens가 설치되지 않은 경우에도 안전하게 무시
        }

        // 일반 엔티티 타입 매칭 (예: VILLAGER, ZOMBIE 등)
        return clicked.getType().name().equalsIgnoreCase(t);
    };

    /**
     * ENTITY_INTERACT 타겟 ID를 문자열로 추출
     * (디버그 / 로깅용)
     */
    public static String extractTargetId(Event e) {
        if (!(e instanceof PlayerInteractEntityEvent ie)) return null;
        Entity clicked = ie.getRightClicked();
        if (clicked == null) return null;

        // Citizens NPC
        try {
            for (var reg : CitizensAPI.getNPCRegistries()) {
                var npc = reg.getNPC(clicked);
                if (npc != null) {
                    return "CITIZENS_" + npc.getId();
                }
            }
        } catch (Throwable ignored) {}

        // 일반 엔티티 타입
        return clicked.getType().name().toUpperCase(Locale.ROOT);
    }
}
