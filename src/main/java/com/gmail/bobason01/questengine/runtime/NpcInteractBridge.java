package com.gmail.bobason01.questengine.runtime;

import net.citizensnpcs.api.CitizensAPI;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;

import java.util.Locale;

final class NpcInteractBridge implements Listener {

    private final Engine engine;

    NpcInteractBridge(Engine engine) {
        this.engine = engine;
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEntityEvent e) {
        Player p = e.getPlayer();
        Entity clicked = e.getRightClicked();
        if (p == null || clicked == null) return;

        String key = resolveTargetKey(clicked);
        if (key == null || key.isEmpty()) return;

        // 전용 두-번-클릭 엔진 경로 호출
        engine.handleNpcInteract(p, key);
    }

    private String resolveTargetKey(Entity clicked) {
        // Citizens NPC 우선
        try {
            if (CitizensAPI.hasImplementation() && CitizensAPI.getNPCRegistry() != null) {
                var npc = CitizensAPI.getNPCRegistry().getNPC(clicked);
                if (npc != null) return "CITIZENS_" + npc.getId();
            }
        } catch (Throwable ignored) {}

        // 기본 엔티티 타입
        return clicked.getType().name().toUpperCase(Locale.ROOT);
    }
}
