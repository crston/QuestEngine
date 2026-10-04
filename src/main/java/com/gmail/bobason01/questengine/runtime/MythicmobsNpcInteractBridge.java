package com.gmail.bobason01.questengine.runtime;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Soft-depend MythicMobs interact bridge (no compile-time Mythic dependency).
 */
public final class MythicmobsNpcInteractBridge implements Listener {

    private final Engine engine;
    private final Map<String, String> keyCache = new HashMap<>();

    private final Object mobManager;
    private final Method getActiveMob;

    public MythicmobsNpcInteractBridge(Engine engine) {
        this.engine = engine;
        try {
            Class<?> mythicBukkit = Class.forName("io.lumine.mythic.bukkit.MythicBukkit");
            Method inst = mythicBukkit.getMethod("inst");
            Object plugin = inst.invoke(null);
            Method getMobManager = mythicBukkit.getMethod("getMobManager");
            this.mobManager = getMobManager.invoke(plugin);
            this.getActiveMob = mobManager.getClass().getMethod("getActiveMob", UUID.class);
        } catch (Throwable t) {
            throw new IllegalStateException("MythicMobs API unavailable", t);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEntityEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;

        Entity clicked = e.getRightClicked();
        if (clicked == null || mobManager == null) return;

        try {
            Object opt = getActiveMob.invoke(mobManager, clicked.getUniqueId());
            if (!(opt instanceof Optional<?> optional) || optional.isEmpty()) return;

            Object activeMob = optional.get();
            Method getType = activeMob.getClass().getMethod("getType");
            Object mythicType = getType.invoke(activeMob);
            if (mythicType == null) return;

            Method getInternalName = mythicType.getClass().getMethod("getInternalName");
            String internalName = String.valueOf(getInternalName.invoke(mythicType));
            if (internalName.isEmpty() || "null".equals(internalName)) return;

            engine.handleNpcInteract(e.getPlayer(), getKey(internalName));
        } catch (Throwable ignored) {
        }
    }

    private String getKey(String internalName) {
        return keyCache.computeIfAbsent(internalName, name ->
                "MYTHICMOBS:" + name.toUpperCase(Locale.ROOT));
    }

    public static boolean isAvailable() {
        return Bukkit.getPluginManager().isPluginEnabled("MythicMobs");
    }
}
