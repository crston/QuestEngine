package com.gmail.bobason01.questengine.runtime;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
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
    private static final Class<?> MYTHIC_DEATH_CLASS;
    private static final Class<?> MYTHIC_SPAWN_CLASS;
    private static final MethodHandle MYTHIC_DEATH_KILLER;
    private static final MethodHandle MYTHIC_DEATH_MOB_TYPE;
    private static final MethodHandle MYTHIC_SPAWN_MOB_TYPE;
    private static final MethodHandle MYTHIC_INTERNAL_NAME;

    static {
        try {
            NULL_HANDLE = LOOKUP.findStatic(
                    EventContextMapper.class,
                    "returnNull",
                    java.lang.invoke.MethodType.methodType(Player.class, Event.class)
            );
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        boolean mythic = false;
        Class<?> deathClz = null;
        Class<?> spawnClz = null;
        MethodHandle deathKiller = null;
        MethodHandle deathMobType = null;
        MethodHandle spawnMobType = null;
        MethodHandle internalName = null;

        try {
            if (Bukkit.getPluginManager().getPlugin("MythicMobs") != null) {
                deathClz = Class.forName("io.lumine.mythic.bukkit.events.MythicMobDeathEvent");
                spawnClz = Class.forName("io.lumine.mythic.bukkit.events.MythicMobSpawnEvent");
                deathKiller = LOOKUP.unreflect(deathClz.getMethod("getKiller"));
                deathMobType = LOOKUP.unreflect(deathClz.getMethod("getMobType"));
                spawnMobType = LOOKUP.unreflect(spawnClz.getMethod("getMobType"));
                Class<?> mythicMob = Class.forName("io.lumine.mythic.api.mobs.MythicMob");
                internalName = LOOKUP.unreflect(mythicMob.getMethod("getInternalName"));
                mythic = true;
            }
        } catch (Throwable ignored) {
            mythic = false;
            deathClz = null;
            spawnClz = null;
            deathKiller = null;
            deathMobType = null;
            spawnMobType = null;
            internalName = null;
        }

        HAS_MYTHIC = mythic;
        MYTHIC_DEATH_CLASS = deathClz;
        MYTHIC_SPAWN_CLASS = spawnClz;
        MYTHIC_DEATH_KILLER = deathKiller;
        MYTHIC_DEATH_MOB_TYPE = deathMobType;
        MYTHIC_SPAWN_MOB_TYPE = spawnMobType;
        MYTHIC_INTERNAL_NAME = internalName;
    }

    public static Player returnNull(Event e) { return null; }

    public static boolean hasMythic() { return HAS_MYTHIC; }

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

        if (e instanceof PlayerEvent pe) {
            return pe.getPlayer();
        }

        if (e instanceof EntityDeathEvent de) {
            return de.getEntity().getKiller();
        }

        if (HAS_MYTHIC && MYTHIC_DEATH_CLASS != null && MYTHIC_DEATH_CLASS.isInstance(e)) {
            Player mythicKiller = extractMythicKiller(e);
            if (mythicKiller != null) return mythicKiller;
        }

        Class<?> clz = e.getClass();
        MethodHandle mh = PLAYER_GETTER_CACHE.get(clz);
        if (mh != null) {
            try {
                Object result = mh.invoke(e);
                return asPlayer(result);
            } catch (Throwable ignored) {
                return null;
            }
        }

        return findAndCachePlayerGetter(clz, e);
    }

    public static Player extractMythicKiller(Event e) {
        if (!HAS_MYTHIC || MYTHIC_DEATH_KILLER == null || e == null) return null;
        try {
            Object killer = MYTHIC_DEATH_KILLER.invoke(e);
            return asPlayer(killer);
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static String extractMythicInternalName(Event e) {
        if (!HAS_MYTHIC || e == null || MYTHIC_INTERNAL_NAME == null) return null;
        try {
            Object mobType = null;
            if (MYTHIC_DEATH_CLASS != null && MYTHIC_DEATH_CLASS.isInstance(e) && MYTHIC_DEATH_MOB_TYPE != null) {
                mobType = MYTHIC_DEATH_MOB_TYPE.invoke(e);
            } else if (MYTHIC_SPAWN_CLASS != null && MYTHIC_SPAWN_CLASS.isInstance(e) && MYTHIC_SPAWN_MOB_TYPE != null) {
                mobType = MYTHIC_SPAWN_MOB_TYPE.invoke(e);
            }
            if (mobType == null) return null;
            Object name = MYTHIC_INTERNAL_NAME.invoke(mobType);
            return name == null ? null : String.valueOf(name);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Player asPlayer(Object o) {
        if (o instanceof Player p) return p;
        if (o instanceof LivingEntity le) {
            // Projectile/pet edge: LivingEntity.getKiller() is last damaging player
            if (le instanceof Player p2) return p2;
        }
        return null;
    }

    private static Player findAndCachePlayerGetter(Class<?> clz, Event e) {
        MethodHandle target = NULL_HANDLE;
        try {
            target = tryGetter(clz, "getPlayer", Player.class);
            if (target == NULL_HANDLE) {
                target = tryGetter(clz, "getWhoClicked", HumanEntity.class);
            }
            if (target == NULL_HANDLE) {
                // MythicMobDeathEvent.getKiller(): LivingEntity (often Player)
                target = tryGetter(clz, "getKiller", LivingEntity.class);
            }
            if (target == NULL_HANDLE) {
                target = tryGetter(clz, "getEntity", Player.class);
            }
        } catch (Throwable ignored) {
            target = NULL_HANDLE;
        }

        PLAYER_GETTER_CACHE.put(clz, target);
        try {
            return asPlayer(target.invoke(e));
        } catch (Throwable t) {
            return null;
        }
    }

    private static MethodHandle tryGetter(Class<?> clz, String name, Class<?> assignableFrom) throws IllegalAccessException {
        try {
            Method m = clz.getMethod(name);
            if (assignableFrom.isAssignableFrom(m.getReturnType())
                    || LivingEntity.class.isAssignableFrom(m.getReturnType())
                    || Entity.class.isAssignableFrom(m.getReturnType())) {
                return LOOKUP.unreflect(m);
            }
        } catch (NoSuchMethodException ignored) {}
        return NULL_HANDLE;
    }

    private static void populateShortcuts(Event e, Map<String, Object> ctx, Player p) {
        if (e instanceof BlockBreakEvent be) {
            ctx.put("block_type", be.getBlock().getType().name());
        } else if (e instanceof BlockPlaceEvent bp) {
            ctx.put("block_type", bp.getBlockPlaced().getType().name());
        } else if (e instanceof ProjectileLaunchEvent pe) {
            ctx.put("projectile_type", pe.getEntity().getType().name());
        }

        String className = e.getClass().getName();
        if (className.equals("com.gmail.bobason01.event.GUIOpenEvent")) {
            try {
                Method m = e.getClass().getMethod("getGuiId");
                ctx.put("gui_id", m.invoke(e));
            } catch (Throwable ignored) {}
        } else if (className.equals("com.gmail.bobason01.api.event.HotkeyInputEvent")) {
            try {
                Method m = e.getClass().getMethod("getKeyName");
                ctx.put("hotkey_name", m.invoke(e));
            } catch (Throwable ignored) {}
        }

        if (e instanceof EntityEvent ee) {
            Entity ent = ee.getEntity();
            ctx.put("entity_type", ent.getType().name());
            if (ent instanceof Player && p == null) {
                ctx.put("player_name", ent.getName());
            }
        }

        if (e instanceof EntityDeathEvent de) {
            Player killer = de.getEntity().getKiller();
            if (killer != null) ctx.put("killer_name", killer.getName());
        }

        if (HAS_MYTHIC && MYTHIC_DEATH_CLASS != null && MYTHIC_DEATH_CLASS.isInstance(e)) {
            Player killer = extractMythicKiller(e);
            if (killer != null) ctx.put("killer_name", killer.getName());
            String type = extractMythicInternalName(e);
            if (type != null) ctx.put("mythicmob_type", type);
        } else if (HAS_MYTHIC && MYTHIC_SPAWN_CLASS != null && MYTHIC_SPAWN_CLASS.isInstance(e)) {
            String type = extractMythicInternalName(e);
            if (type != null) ctx.put("mythicmob_type", type);
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
    }
}
