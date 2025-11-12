package com.gmail.bobason01.questengine.runtime;

import net.citizensnpcs.api.CitizensAPI;
import org.bukkit.entity.Entity;
import org.bukkit.event.player.PlayerInteractEntityEvent;

import java.util.Locale;

public final class TargetMatchers {

    public static final Engine.TargetMatcher ENTITY_INTERACT_MATCHER = (p, e, t) -> {
        if (!(e instanceof PlayerInteractEntityEvent ie)) return false;
        Entity clicked = ie.getRightClicked();
        if (clicked == null) return false;

        // Citizens NPC 매칭
        try {
            if (CitizensAPI.hasImplementation() && CitizensAPI.getNPCRegistry() != null) {
                var npc = CitizensAPI.getNPCRegistry().getNPC(clicked);
                if (npc != null) {
                    String npcKey = "CITIZENS_" + npc.getId();
                    return npcKey.equalsIgnoreCase(t);
                }
            }
        } catch (Throwable ignored) {}

        // 기본 엔티티 타입 매칭 (예: VILLAGER, ZOMBIE 등)
        String entityName = clicked.getType().name();
        if (entityName.equalsIgnoreCase(t)) return true;

        // 혹시 모를 커스텀 이름 또는 클래스 이름 매칭
        if (clicked.getCustomName() != null && clicked.getCustomName().equalsIgnoreCase(t)) return true;
        String clazz = clicked.getClass().getSimpleName().toUpperCase(Locale.ROOT);
        return clazz.contains(t.toUpperCase(Locale.ROOT));
    };
}
