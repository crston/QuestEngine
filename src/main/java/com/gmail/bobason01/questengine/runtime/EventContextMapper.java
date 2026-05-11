package com.gmail.bobason01.questengine.runtime;

import io.lumine.mythic.bukkit.events.MythicMobDeathEvent;
import io.lumine.mythic.bukkit.events.MythicMobSpawnEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class EventContextMapper {

    private EventContextMapper() {}

    private static final Map<Class<?>, MethodHandle> PLAYER_GETTER_CACHE = new ConcurrentHashMap<>();

    private static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();
    private static final MethodHandle NULL_HANDLE;

    private static final boolean HAS_MYTHIC;

    static {
        try {
            NULL_HANDLE = LOOKUP.findStatic(EventContextMapper.class, "returnNull", java.lang.invoke.MethodType.methodType(Player.class, Event.class));
            HAS_MYTHIC = Bukkit.getPluginManager().getPlugin("MythicMobs") != null;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static Player returnNull(Event e) { return null; }

    public static Map<String, Object> map(Event e) {
        if (e == null) return Collections.emptyMap();

        Map<String, Object> ctx = new HashMap<>(8);

        Player player = extractPlayer(e);
        if (player != null) {
            ctx.put("player_name", player.getName());
            ctx.put("world_name", player.getWorld().getName());
        } else {
            ctx.put("player_name", "unknown");
            ctx.put("world_name", "unknown");
        }

        populateShortcuts(e, ctx, player);

        return ctx;
    }

    public static Player extractPlayer(Event e) {
        if (e == null) return null;
        Class<?> clz = e.getClass();

        if (e instanceof PlayerEvent pe) {
            return pe.getPlayer();
        }

        MethodHandle mh = PLAYER_GETTER_CACHE.get(clz);
        if (mh != null) {
            try {
                return (Player) mh.invoke(e);
            } catch (Throwable ignored) {
                return null;
            }
        }

        return findAndCachePlayerGetter(clz, e);
    }

    private static Player findAndCachePlayerGetter(Class<?> clz, Event e) {
        MethodHandle target = NULL_HANDLE;
        try {
            try {
                Method m = clz.getMethod("getPlayer");
                if (Player.class.isAssignableFrom(m.getReturnType())) {
                    target = LOOKUP.unreflect(m);
                }
            } catch (NoSuchMethodException ignored) {}

            if (target == NULL_HANDLE) {
                try {
                    Method m = clz.getMethod("getWhoClicked");
                    if (HumanEntity.class.isAssignableFrom(m.getReturnType())) {
                        target = LOOKUP.unreflect(m);
                    }
                } catch (NoSuchMethodException ignored) {}
            }

            if (target == NULL_HANDLE) {
                try {
                    Method m = clz.getMethod("getEntity");
                    if (Player.class.isAssignableFrom(m.getReturnType())) {
                        target = LOOKUP.unreflect(m);
                    }
                } catch (NoSuchMethodException ignored) {}
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }

        PLAYER_GETTER_CACHE.put(clz, target);
        try {
            return (Player) target.invoke(e);
        } catch (Throwable t) {
            return null;
        }
    }

    private static void populateShortcuts(Event e, Map<String, Object> ctx, Player p) {
        if (e instanceof BlockBreakEvent be) {
            ctx.put("block_type", be.getBlock().getType().name());
        } else if (e instanceof BlockPlaceEvent bp) {
            ctx.put("block_type", bp.getBlockPlaced().getType().name());
        }

        if (e instanceof EntityEvent ee) {
            Entity ent = ee.getEntity();
            ctx.put("entity_type", ent.getType().name());
            if (ent instanceof Player && p == null) {
                ctx.put("player_name", ent.getName());
            }
        }

        if (e instanceof EntityDeathEvent de) {
            if (de.getEntity().getKiller() != null) {
                ctx.put("killer_name", de.getEntity().getKiller().getName());
            }
        }

        if (e instanceof EntityDamageByEntityEvent hit) {
            Entity damager = hit.getDamager();
            ctx.put("damager_type", damager.getType().name());
            if (damager instanceof Player dp) ctx.put("damager_name", dp.getName());

            Entity victim = hit.getEntity();
            ctx.put("victim_type", victim.getType().name());
            if (victim instanceof Player vp) ctx.put("victim_name", vp.getName());
        }

        ItemStack item = null;
        if (e instanceof PlayerInteractEvent ie) {
            item = ie.getItem();
            if (item == null && p != null) item = p.getInventory().getItemInMainHand();
        } else if (e instanceof PlayerDropItemEvent de) {
            item = de.getItemDrop().getItemStack();
        } else if (e instanceof CraftItemEvent ce) {
            item = ce.getRecipe().getResult();
        }

        if (item != null && item.getType() != org.bukkit.Material.AIR) {
            ctx.put("item_type", item.getType().name());
            if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
                ctx.put("item_name", item.getItemMeta().getDisplayName());
            }
        } else {
            ctx.put("item_type", "AIR");
        }

        if (HAS_MYTHIC) {
            if (e instanceof MythicMobDeathEvent md) {
                if (md.getMobType() != null) ctx.put("mythicmob_type", md.getMobType().getInternalName());
            } else if (e instanceof MythicMobSpawnEvent ms) {
                if (ms.getMobType() != null) ctx.put("mythicmob_type", ms.getMobType().getInternalName());
            }
        }
    }
}