package com.gmail.bobason01.questengine.runtime;

import com.gmail.bobason01.questengine.QuestEnginePlugin;
import com.gmail.bobason01.questengine.quest.QuestDef;
import com.gmail.bobason01.questengine.quest.QuestRepository;
import org.bukkit.Bukkit;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class DynamicEventListener {

    private final QuestEnginePlugin plugin;
    private final Engine engine;

    private final List<Listener> activeListeners = new ArrayList<>();

    private final Map<String, Class<? extends Event>> classCache = new ConcurrentHashMap<>();

    public DynamicEventListener(QuestEnginePlugin plugin, Engine engine, QuestRepository repo) {
        this.plugin = plugin;
        this.engine = engine;

        Bukkit.getScheduler().runTaskLater(plugin, () -> registerAll(repo), 20L);
    }

    public void unregisterAll() {
        for (Listener l : activeListeners) {
            HandlerList.unregisterAll(l);
        }
        activeListeners.clear();
        classCache.clear();
    }

    public void registerAll(QuestRepository repo) {
        if (!activeListeners.isEmpty()) {
            unregisterAll();
        }

        PluginManager pm = Bukkit.getPluginManager();
        Set<String> uniqueEvents = new HashSet<>();

        for (QuestDef def : repo.all()) {
            if (def == null) continue;

            if (def.event != null && !def.event.isEmpty()) {
                String evt = def.event.toUpperCase(Locale.ROOT);
                if (!evt.equals("CUSTOM") && !evt.equals("CUSTOM_EVENT") && !evt.equals("NONE")) {
                    uniqueEvents.add(evt);
                }
            }

            if (def.custom != null && def.custom.eventClass != null && !def.custom.eventClass.isEmpty()) {
                uniqueEvents.add(def.custom.eventClass.trim());
            }
        }

        if (uniqueEvents.isEmpty()) {
            plugin.getLogger().info("[QuestEngine] No dynamic events found to register.");
            return;
        }

        int hookedCount = 0;
        for (String evtName : uniqueEvents) {
            Class<? extends Event> eventClass = resolveEventClass(evtName);

            if (eventClass == null) {
                plugin.getLogger().warning("[QuestEngine] Cannot resolve event class: " + evtName);
                continue;
            }

            try {
                EventExecutor executor = (listener, event) -> {
                    if (eventClass.isInstance(event)) {
                        engine.handleDynamic(event);
                    }
                };

                Listener listener = new Listener() {};

                pm.registerEvent(eventClass, listener, EventPriority.MONITOR, executor, plugin, true);

                activeListeners.add(listener);
                hookedCount++;

                plugin.getLogger().info("[QuestEngine] Successfully hooked event: " + eventClass.getName());

            } catch (Throwable t) {
                plugin.getLogger().severe("[QuestEngine] Failed to hook event " + evtName + ": " + t.getMessage());
            }
        }

        plugin.getLogger().info("[QuestEngine] Dynamic registration finished. Hooked " + hookedCount + " events.");

        engine.rebuildCustomEventIndex();
    }

    @SuppressWarnings("unchecked")
    private Class<? extends Event> resolveEventClass(String name) {
        String key = name.toUpperCase(Locale.ROOT);
        if (classCache.containsKey(key)) return classCache.get(key);

        Class<? extends Event> found = null;

        switch (key) {
            // --- BLOCK EVENTS ---
            case "BLOCK_BREAK": found = org.bukkit.event.block.BlockBreakEvent.class; break;
            case "BLOCK_PLACE": found = org.bukkit.event.block.BlockPlaceEvent.class; break;
            case "BLOCK_BURN": found = org.bukkit.event.block.BlockBurnEvent.class; break;
            case "BLOCK_EXPLODE": found = org.bukkit.event.block.BlockExplodeEvent.class; break;
            case "BLOCK_FERTILIZING": found = org.bukkit.event.block.BlockFertilizeEvent.class; break;

            // --- ENTITY & MOB EVENTS ---
            case "ENTITY_DEATH":
            case "PLAYER_KILL":
            case "MOBKILLING": found = org.bukkit.event.entity.EntityDeathEvent.class; break;
            case "TAMING": found = org.bukkit.event.entity.EntityTameEvent.class; break;
            case "BREEDING":
            case "BREEDNG": found = org.bukkit.event.entity.EntityBreedEvent.class; break;
            case "CURING": found = org.bukkit.event.entity.EntityTransformEvent.class; break;
            case "ENTITY_SPAWN": found = org.bukkit.event.entity.EntitySpawnEvent.class; break;
            case "PLAYER_BARTERING": found = org.bukkit.event.entity.PiglinBarterEvent.class; break;
            case "DEAL_DAMAGE":
            case "PLAYER_ATTACK": found = org.bukkit.event.entity.EntityDamageByEntityEvent.class; break;

            // --- PLAYER STATUS & MOVEMENT ---
            case "PLAYER_CHAT": found = org.bukkit.event.player.AsyncPlayerChatEvent.class; break;
            case "PLAYER_COMMAND": found = org.bukkit.event.player.PlayerCommandPreprocessEvent.class; break;
            case "PLAYER_TELEPORT": found = org.bukkit.event.player.PlayerTeleportEvent.class; break;
            case "PLAYER_WALK":
            case "DISTANCE_FROM": found = org.bukkit.event.player.PlayerMoveEvent.class; break;
            case "PLAYER_PRE_JOIN": found = org.bukkit.event.player.PlayerJoinEvent.class; break;
            case "PLAYER_LEAVE": found = org.bukkit.event.player.PlayerQuitEvent.class; break;
            case "PLAYER_RESPAWN": found = org.bukkit.event.player.PlayerRespawnEvent.class; break;
            case "PLAYER_DEATH": found = org.bukkit.event.entity.PlayerDeathEvent.class; break;
            case "PLAYER_BED_ENTER": found = org.bukkit.event.player.PlayerBedEnterEvent.class; break;
            case "PLAYER_WORLD_CHANGE": found = org.bukkit.event.player.PlayerChangedWorldEvent.class; break;
            case "PLAYER_EXP_GAIN": found = org.bukkit.event.player.PlayerExpChangeEvent.class; break;
            case "PLAYER_LEVELUP": found = org.bukkit.event.player.PlayerLevelChangeEvent.class; break;
            case "PLAYER_SWAP_HAND": found = org.bukkit.event.player.PlayerSwapHandItemsEvent.class; break;
            case "FISHING": found = org.bukkit.event.player.PlayerFishEvent.class; break;
            case "MILKING":
            case "BUCKET_FILL": found = org.bukkit.event.player.PlayerBucketFillEvent.class; break;
            case "BUCKET_EMPTY": found = org.bukkit.event.player.PlayerBucketEmptyEvent.class; break;
            case "SHEARING":
            case "BLOCK_SHEARING": found = org.bukkit.event.player.PlayerShearEntityEvent.class; break;
            case "ITEM_INTERACT":
            case "FARMING": found = org.bukkit.event.player.PlayerInteractEvent.class; break;
            case "ENTITY_INTERACT": found = org.bukkit.event.player.PlayerInteractEntityEvent.class; break;

            // --- ITEM & INVENTORY EVENTS ---
            case "ITEM_CRAFT": found = org.bukkit.event.inventory.CraftItemEvent.class; break;
            case "CRAFTSLOT_CLICK":
            case "ITEM_MOVE":
            case "PLAYER_ARMOR": found = org.bukkit.event.inventory.InventoryClickEvent.class; break;
            case "ITEM_CONSUME": found = org.bukkit.event.player.PlayerItemConsumeEvent.class; break;
            case "ITEM_PICKUP": found = org.bukkit.event.entity.EntityPickupItemEvent.class; break;
            case "ITEM_DROP": found = org.bukkit.event.player.PlayerDropItemEvent.class; break;
            case "ITEM_ENCHANT":
            case "ENCHANTING": found = org.bukkit.event.enchantment.EnchantItemEvent.class; break;
            case "ITEM_BREAK": found = org.bukkit.event.player.PlayerItemBreakEvent.class; break;
            case "ITEM_DAMAGE": found = org.bukkit.event.player.PlayerItemDamageEvent.class; break;
            case "ITEM_MENDING": found = org.bukkit.event.player.PlayerItemMendEvent.class; break;
            case "SMITHING": found = org.bukkit.event.inventory.SmithItemEvent.class; break;
            case "BREWING": found = org.bukkit.event.inventory.BrewEvent.class; break;
            case "INVENTORY_OPEN": found = org.bukkit.event.inventory.InventoryOpenEvent.class; break;
            case "ITEM_REPAIR": found = org.bukkit.event.inventory.PrepareAnvilEvent.class; break;
            case "TRADING": found = org.bukkit.event.inventory.TradeSelectEvent.class; break;
            case "COMPOSTING": found = org.bukkit.event.inventory.InventoryMoveItemEvent.class; break;

            // --- WORLD & EXTERNAL ---
            case "WORLD_CHUNK_LOAD": found = org.bukkit.event.world.ChunkLoadEvent.class; break;
            case "MYTHICMOBS_ENTITY_KILL":
                try { found = (Class<? extends Event>) Class.forName("io.lumine.mythic.bukkit.events.MythicMobDeathEvent"); } catch (Exception ignored) {}
                break;
            case "MYTHICMOBS_ENTITY_SPAWN":
                try { found = (Class<? extends Event>) Class.forName("io.lumine.mythic.bukkit.events.MythicMobSpawnEvent"); } catch (Exception ignored) {}
                break;
        }

        if (found != null) {
            classCache.put(key, found);
            return found;
        }

        // 수동 매핑 실패 시 클래스 이름으로 직접 찾기
        try {
            Class<?> clz = Class.forName(name);
            if (Event.class.isAssignableFrom(clz)) {
                found = (Class<? extends Event>) clz;
            }
        } catch (ClassNotFoundException e) {
            for (Plugin p : Bukkit.getPluginManager().getPlugins()) {
                try {
                    Class<?> externalClz = Class.forName(name, false, p.getClass().getClassLoader());
                    if (Event.class.isAssignableFrom(externalClz)) {
                        found = (Class<? extends Event>) externalClz;
                        break;
                    }
                } catch (ClassNotFoundException ignored) {}
            }
        }

        if (found != null) {
            classCache.put(key, found);
        }

        return found;
    }
}