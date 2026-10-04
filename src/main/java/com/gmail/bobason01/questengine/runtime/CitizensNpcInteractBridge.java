package com.gmail.bobason01.questengine.runtime;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Soft-depend Citizens bridge (no compile-time Citizens dependency).
 */
public final class CitizensNpcInteractBridge implements Listener {

    private final Engine engine;
    private final Map<Integer, String> keyCache = new ConcurrentHashMap<>();

    private final Object registry;
    private final Method getNpc;

    public CitizensNpcInteractBridge(Engine engine) {
        this.engine = engine;
        try {
            Class<?> api = Class.forName("net.citizensnpcs.api.CitizensAPI");
            Method getRegistry = api.getMethod("getNPCRegistry");
            this.registry = getRegistry.invoke(null);
            this.getNpc = registry.getClass().getMethod("getNPC", Entity.class);
        } catch (Throwable t) {
            throw new IllegalStateException("Citizens API unavailable", t);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEntityEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;

        Entity clicked = e.getRightClicked();
        if (clicked == null || !clicked.hasMetadata("NPC")) return;

        try {
            Object npc = getNpc.invoke(registry, clicked);
            if (npc == null) return;
            Method getId = npc.getClass().getMethod("getId");
            int id = ((Number) getId.invoke(npc)).intValue();
            engine.handleNpcInteract(e.getPlayer(), keyCache.computeIfAbsent(id, i -> "CITIZENS:" + i));
        } catch (Throwable ignored) {
            // NPC lookup race / unload — ignore
        }
    }

    public static boolean isAvailable() {
        return Bukkit.getPluginManager().isPluginEnabled("Citizens");
    }
}
