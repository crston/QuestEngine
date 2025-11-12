package com.gmail.bobason01.questengine.runtime;

import io.lumine.mythic.bukkit.events.MythicMobDeathEvent;
import io.lumine.mythic.bukkit.events.MythicMobSpawnEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;

import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class EventContextMapper {

    private EventContextMapper() {}

    private static final Map<Class<?>, Method[]> METHOD_CACHE = new ConcurrentHashMap<>(128);
    private static final Map<Class<?>, Method> PLAYER_METHOD_CACHE = new ConcurrentHashMap<>(64);

    private static final ThreadLocal<Map<String, Object>> LOCAL_MAP =
            ThreadLocal.withInitial(() -> new HashMap<>(48));

    public static Map<String, Object> map(Event e) {
        if (e == null) return Collections.emptyMap();

        Map<String, Object> ctx = LOCAL_MAP.get();
        ctx.clear();

        Method[] methods = METHOD_CACHE.computeIfAbsent(e.getClass(), EventContextMapper::scanGetters);
        for (Method m : methods) {
            try {
                Object val = m.invoke(e);
                if (val == null) continue;
                String key = "event_" + m.getName().substring(3).toLowerCase(Locale.ROOT);
                ctx.put(key, val);
            } catch (Throwable ignored) {}
        }

        injectShortcuts(e, ctx);
        return new HashMap<>(ctx);
    }

    public static Player extractPlayer(Event e) {
        if (e == null) return null;
        Method m = PLAYER_METHOD_CACHE.computeIfAbsent(e.getClass(), EventContextMapper::findPlayerGetter);
        if (m == null) return null;
        try {
            Object v = m.invoke(e);
            return v instanceof Player ? (Player) v : null;
        } catch (Throwable ignored) {}
        return null;
    }

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

    private static Method findPlayerGetter(Class<?> clz) {
        for (Method m : clz.getMethods()) {
            if (m.getParameterCount() != 0) continue;
            String n = m.getName();
            if (n.equalsIgnoreCase("getPlayer")
                    || n.equalsIgnoreCase("getWhoClicked")
                    || n.equalsIgnoreCase("getEntity")) {
                return m;
            }
        }
        return null;
    }

    private static void injectShortcuts(Event e, Map<String, Object> ctx) {
        try {
            Player player = extractPlayer(e);
            if (player != null) {
                ctx.put("player_name", player.getName());
                if (player.getWorld() != null)
                    ctx.put("world_name", player.getWorld().getName());
            } else {
                ctx.putIfAbsent("player_name", "unknown");
            }

            if (e instanceof BlockBreakEvent be) {
                if (be.getBlock() != null)
                    ctx.put("block_type", be.getBlock().getType().name());
            } else if (e instanceof BlockPlaceEvent bp) {
                if (bp.getBlockPlaced() != null)
                    ctx.put("block_type", bp.getBlockPlaced().getType().name());
            } else if (e instanceof BlockEvent be2) {
                if (be2.getBlock() != null && !ctx.containsKey("block_type"))
                    ctx.put("block_type", be2.getBlock().getType().name());
            }

            if (e instanceof EntityEvent ee) {
                Entity ent = ee.getEntity();
                if (ent != null) {
                    ctx.put("entity_type", ent.getType().name());
                    if (ent.getWorld() != null)
                        ctx.put("world_name", ent.getWorld().getName());
                }
            }

            if (e instanceof EntityDeathEvent de) {
                if (de.getEntity() != null)
                    ctx.put("entity_type", de.getEntity().getType().name());
                if (de.getEntity().getKiller() != null)
                    ctx.put("killer_name", de.getEntity().getKiller().getName());
            }

            if (e instanceof EntityDamageByEntityEvent hit) {
                Entity damager = hit.getDamager();
                if (damager != null)
                    ctx.put("damager_type", damager.getType().name());
                Entity victim = hit.getEntity();
                if (victim != null)
                    ctx.put("victim_type", victim.getType().name());
            }

            ItemStack item = null;

            if (e instanceof PlayerInteractEvent ie) {
                item = ie.getItem();
                if (item == null && player != null)
                    item = player.getInventory().getItemInMainHand();
            } else if (e instanceof PlayerDropItemEvent dropE) {
                if (dropE.getItemDrop() != null)
                    item = dropE.getItemDrop().getItemStack();
            } else if (e instanceof CraftItemEvent craftE) {
                if (craftE.getRecipe() != null)
                    item = craftE.getRecipe().getResult();
            }

            if (item != null && item.getType() != null) {
                ctx.put("item_type", item.getType().name());
                if (item.hasItemMeta() && item.getItemMeta().hasDisplayName())
                    ctx.put("item_name", item.getItemMeta().getDisplayName());
            } else {
                ctx.putIfAbsent("item_type", "AIR");
            }

            if (e instanceof MythicMobDeathEvent mm) {
                if (mm.getMobType() != null)
                    ctx.put("mythicmob_type", mm.getMobType().getInternalName());
            } else if (e instanceof MythicMobSpawnEvent ms) {
                if (ms.getMobType() != null)
                    ctx.put("mythicmob_type", ms.getMobType().getInternalName());
            }

            ctx.putIfAbsent("world_name", "unknown_world");
            ctx.putIfAbsent("entity_type", "UNKNOWN");
            ctx.putIfAbsent("block_type", "AIR");
            ctx.putIfAbsent("item_name", "");
            ctx.putIfAbsent("item_type", "AIR");

        } catch (Throwable ex) {
            Bukkit.getLogger().warning("[QuestEngine] ContextMapper failed for " + e.getEventName() + ": " + ex.getMessage());
        }
    }
}
