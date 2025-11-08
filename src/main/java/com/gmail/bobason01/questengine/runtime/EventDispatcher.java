package com.gmail.bobason01.questengine.runtime;

import io.lumine.mythic.bukkit.events.MythicMobDeathEvent;
import io.lumine.mythic.bukkit.events.MythicMobSpawnEvent;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.MerchantInventory;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.Objects;
import java.util.function.Consumer;

public final class EventDispatcher implements Listener {

    private final Plugin plugin;
    private final Engine engine;

    private static final String PLAYER_BARTERING = "PLAYER_BARTERING";
    private static final String BLOCK_BREAK = "BLOCK_BREAK";
    private static final String BLOCK_PLACE = "BLOCK_PLACE";
    private static final String BLOCK_FERTILIZING = "BLOCK_FERTILIZING";
    private static final String BLOCK_ITEM_DROPPING = "BLOCK_ITEM_DROPPING";
    private static final String BLOCK_SHEARING = "BLOCK_SHEARING";
    private static final String BREEDING = "BREEDING";
    private static final String BREEDNG = "BREEDNG";
    private static final String BREWING = "BREWING";
    private static final String BUCKET_EMPTY = "BUCKET_EMPTY";
    private static final String BUCKET_ENTITY = "BUCKET_ENTITY";
    private static final String BUCKET_FILL = "BUCKET_FILL";
    private static final String COMPOSTING = "COMPOSTING";
    private static final String CURING = "CURING";
    private static final String DEAL_DAMAGE = "DEAL_DAMAGE";
    private static final String DISTANCE_FROM = "DISTANCE_FROM";
    private static final String ENCHANTING = "ENCHANTING";
    private static final String FARMING = "FARMING";
    private static final String FISHING = "FISHING";
    private static final String HATCHING = "HATCHING";
    private static final String ITEM_BREAK = "ITEM_BREAK";
    private static final String ITEM_DAMAGE = "ITEM_DAMAGE";
    private static final String ITEM_MENDING = "ITEM_MENDING";
    private static final String MILKING = "MILKING";
    private static final String ENTITY_KILL = "ENTITY_KILL";
    private static final String PERMISSIONS_CHECK = "PERMISSIONS_CHECK";
    private static final String SHEARING = "SHEARING";
    private static final String SMELTING = "SMELTING";
    private static final String SMITHING = "SMITHING";
    private static final String TAMING = "TAMING";
    private static final String TRADING = "TRADING";
    private static final String PLAYER_WALK = "PLAYER_WALK";
    private static final String PLAYER_PRE_JOIN = "PLAYER_PRE_JOIN";
    private static final String PLAYER_LEAVE = "PLAYER_LEAVE";
    private static final String PLAYER_RESPAWN = "PLAYER_RESPAWN";
    private static final String PLAYER_DEATH = "PLAYER_DEATH";
    private static final String PLAYER_COMMAND = "PLAYER_COMMAND";
    private static final String PLAYER_CHAT = "PLAYER_CHAT";
    private static final String PLAYER_EXP_GAIN = "PLAYER_EXP_GAIN";
    private static final String PLAYER_LEVELUP = "PLAYER_LEVELUP";
    private static final String PLAYER_WORLD_CHANGE = "PLAYER_WORLD_CHANGE";
    private static final String PLAYER_ATTACK = "PLAYER_ATTACK";
    private static final String PLAYER_KILL = "PLAYER_KILL";
    private static final String PLAYER_ARMOR = "PLAYER_ARMOR";
    private static final String PLAYER_TELEPORT = "PLAYER_TELEPORT";
    private static final String PLAYER_BED_ENTER = "PLAYER_BED_ENTER";
    private static final String PLAYER_SWAP_HAND = "PLAYER_SWAP_HAND";
    private static final String ITEM_INTERACT = "ITEM_INTERACT";
    private static final String ITEM_CONSUME = "ITEM_CONSUME";
    private static final String ITEM_PICKUP = "ITEM_PICKUP";
    private static final String ITEM_MOVE = "ITEM_MOVE";
    private static final String ITEM_CRAFT = "ITEM_CRAFT";
    private static final String ITEM_DROP = "ITEM_DROP";
    private static final String ITEM_SELECT = "ITEM_SELECT";
    private static final String ITEM_ENCHANT = "ITEM_ENCHANT";
    private static final String ITEM_REPAIR = "ITEM_REPAIR";
    private static final String ENTITY_INTERACT = "ENTITY_INTERACT";
    private static final String ENTITY_SPAWN = "ENTITY_SPAWN";
    private static final String MYTHICMOBS_ENTITY_SPAWN = "MYTHICMOBS_ENTITY_SPAWN";
    private static final String MYTHICMOBS_KILL = "MYTHICMOBS_KILL";
    private static final String CHUNK_LOAD = "CHUNK_LOAD";

    private final double walkMinDistSq;

    private static Method mythicSpawnGetLocation;
    private static Method mythicDeathGetKiller;
    private static Method mythicDeathGetLocation;

    public EventDispatcher(Plugin plugin, Engine engine) {
        this.plugin = plugin;
        this.engine = engine;
        this.walkMinDistSq = Math.max(0.01, plugin.getConfig().getDouble("performance.player-walk-min-dist-sq", 0.64));
        Bukkit.getPluginManager().registerEvents(this, plugin);
        registerOptionalPaperEvents();
        registerOptionalExternalEvents();
        registerMythicEvents();
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent e) { engine.handle(e.getPlayer(), BLOCK_BREAK, e); }

    @EventHandler(ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent e) { engine.handle(e.getPlayer(), BLOCK_PLACE, e); }

    @EventHandler(ignoreCancelled = true)
    public void onBlockFertilize(BlockFertilizeEvent e) {
        if (e.getPlayer() != null) {
            engine.handle(e.getPlayer(), BLOCK_FERTILIZING, e);
            engine.handle(e.getPlayer(), FARMING, e);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent e) {
        Player killer = e.getEntity().getKiller();
        if (killer != null) {
            engine.handle(killer, ENTITY_KILL, e);
            engine.handle(killer, PLAYER_KILL, e);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onShear(PlayerShearEntityEvent e) { engine.handle(e.getPlayer(), SHEARING, e); }

    @EventHandler(ignoreCancelled = true)
    public void onBreed(EntityBreedEvent e) {
        if (e.getBreeder() instanceof Player p) {
            engine.handle(p, BREEDING, e);
            engine.handle(p, BREEDNG, e);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onFish(PlayerFishEvent e) {
        Player p = e.getPlayer();
        if (p != null) engine.handle(p, FISHING, e);
    }

    @EventHandler(ignoreCancelled = true)
    public void onCraft(CraftItemEvent e) {
        if (e.getWhoClicked() instanceof Player p) engine.handle(p, ITEM_CRAFT, e);
    }

    @EventHandler(ignoreCancelled = true)
    public void onSmelt(FurnaceSmeltEvent e) {
        var loc = e.getBlock().getLocation();
        World w = loc.getWorld();
        for (Player p : w.getPlayers()) {
            if (p.getWorld() != w) continue;
            if (p.getLocation().distanceSquared(loc) <= 36.0) {
                engine.handle(p, SMELTING, e);
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onSmithPrepare(PrepareSmithingEvent e) {
        if (e.getView() != null && e.getView().getPlayer() instanceof Player p) {
            engine.handle(p, SMITHING, e);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEnchant(EnchantItemEvent e) {
        engine.handle(e.getEnchanter(), ENCHANTING, e);
        engine.handle(e.getEnchanter(), ITEM_ENCHANT, e);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent e) { engine.handle(e.getPlayer(), BUCKET_FILL, e); }

    @EventHandler(ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent e) { engine.handle(e.getPlayer(), BUCKET_EMPTY, e); }

    @EventHandler(ignoreCancelled = true)
    public void onBucketEntity(PlayerBucketEntityEvent e) { engine.handle(e.getPlayer(), BUCKET_ENTITY, e); }

    @EventHandler(ignoreCancelled = true)
    public void onItemDrop(PlayerDropItemEvent e) {
        engine.handle(e.getPlayer(), ITEM_DROP, e);
        engine.handle(e.getPlayer(), BLOCK_ITEM_DROPPING, e);
    }

    @EventHandler(ignoreCancelled = true)
    public void onItemPickup(EntityPickupItemEvent e) {
        if (e.getEntity() instanceof Player p) engine.handle(p, ITEM_PICKUP, e);
    }

    @EventHandler(ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent e) { engine.handle(e.getPlayer(), ITEM_CONSUME, e); }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent e) { engine.handle(e.getPlayer(), ITEM_INTERACT, e); }

    @EventHandler(ignoreCancelled = true)
    public void onItemHeld(PlayerItemHeldEvent e) { engine.handle(e.getPlayer(), ITEM_SELECT, e); }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent e) {
        if (e.getWhoClicked() instanceof Player p) {
            Inventory inv = e.getInventory();
            if (inv instanceof MerchantInventory) {
                engine.handle(p, TRADING, e);
            }
            engine.handle(p, ITEM_MOVE, e);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPrepareAnvil(PrepareAnvilEvent e) {
        if (e.getView() != null && e.getView().getPlayer() instanceof Player p) {
            engine.handle(p, ITEM_REPAIR, e);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityInteract(PlayerInteractEntityEvent e) { engine.handle(e.getPlayer(), ENTITY_INTERACT, e); }

    @EventHandler(ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent e) {
        for (Player p : Objects.requireNonNull(e.getLocation().getWorld()).getPlayers()) {
            if (p.getWorld() == e.getLocation().getWorld() && p.getLocation().distanceSquared(e.getLocation()) <= 64.0) {
                engine.handle(p, ENTITY_SPAWN, e);
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerCommand(PlayerCommandPreprocessEvent e) { engine.handle(e.getPlayer(), PLAYER_COMMAND, e); }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerChat(AsyncPlayerChatEvent e) { engine.handle(e.getPlayer(), PLAYER_CHAT, e); }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerExp(PlayerExpChangeEvent e) { engine.handle(e.getPlayer(), PLAYER_EXP_GAIN, e); }

    @EventHandler(ignoreCancelled = true)
    public void onLevelChange(PlayerLevelChangeEvent e) { engine.handle(e.getPlayer(), PLAYER_LEVELUP, e); }

    @EventHandler public void onJoin(PlayerJoinEvent e) { engine.handle(e.getPlayer(), PLAYER_PRE_JOIN, e); }

    @EventHandler public void onQuit(PlayerQuitEvent e) { engine.handle(e.getPlayer(), PLAYER_LEAVE, e); }

    @EventHandler(ignoreCancelled = true)
    public void onRespawn(PlayerRespawnEvent e) { engine.handle(e.getPlayer(), PLAYER_RESPAWN, e); }

    @EventHandler(ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent e) { engine.handle(e.getEntity(), PLAYER_DEATH, e); }

    @EventHandler(ignoreCancelled = true)
    public void onWorldChange(PlayerChangedWorldEvent e) { engine.handle(e.getPlayer(), PLAYER_WORLD_CHANGE, e); }

    @EventHandler(ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent e) { engine.handle(e.getPlayer(), PLAYER_TELEPORT, e); }

    @EventHandler(ignoreCancelled = true)
    public void onBedEnter(PlayerBedEnterEvent e) { engine.handle(e.getPlayer(), PLAYER_BED_ENTER, e); }

    @EventHandler(ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent e) { engine.handle(e.getPlayer(), PLAYER_SWAP_HAND, e); }

    @EventHandler(ignoreCancelled = true)
    public void onTame(EntityTameEvent e) {
        if (e.getOwner() instanceof Player p) engine.handle(p, TAMING, e);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamageByEntity(EntityDamageByEntityEvent e) {
        if (e.getDamager() instanceof Player p) {
            engine.handle(p, DEAL_DAMAGE, e);
            engine.handle(p, PLAYER_ATTACK, e);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onChunkLoad(ChunkLoadEvent e) {
        for (Player p : e.getWorld().getPlayers()) engine.handle(p, CHUNK_LOAD, e);
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        if (e.getFrom().getWorld() != e.getTo().getWorld()) return;
        double dx = e.getTo().getX() - e.getFrom().getX();
        double dy = e.getTo().getY() - e.getFrom().getY();
        double dz = e.getTo().getZ() - e.getFrom().getZ();
        if ((dx * dx + dy * dy + dz * dz) >= walkMinDistSq) {
            engine.handle(e.getPlayer(), PLAYER_WALK, e);
            engine.handle(e.getPlayer(), DISTANCE_FROM, e);
        }
    }

    private void registerOptionalPaperEvents() {
        tryRegister("org.bukkit.event.player.PlayerItemDamageEvent", (cls) ->
                Bukkit.getPluginManager().registerEvent((Class<? extends Event>) cls, this, EventPriority.MONITOR,
                        (listener, event) -> {
                            try {
                                Method getPlayer = event.getClass().getMethod("getPlayer");
                                Player p = (Player) getPlayer.invoke(event);
                                engine.handle(p, ITEM_DAMAGE, (Event) event);
                            } catch (Throwable ignored) {}
                        }, plugin, true)
        );
    }

    private void registerOptionalExternalEvents() {
        tryRegister("org.bukkit.event.block.CompostItemEvent", (cls) ->
                Bukkit.getPluginManager().registerEvent((Class<? extends Event>) cls, this, EventPriority.MONITOR,
                        (listener, event) -> {
                            try {
                                Method getPlayer = event.getClass().getMethod("getPlayer");
                                Object maybe = getPlayer.invoke(event);
                                if (maybe instanceof Player p) engine.handle(p, COMPOSTING, (Event) event);
                            } catch (Throwable ignored) {}
                        }, plugin, true)
        );
    }

    private void registerMythicEvents() {
        try {
            mythicSpawnGetLocation = MythicMobSpawnEvent.class.getMethod("getLocation");
            Bukkit.getPluginManager().registerEvent(MythicMobSpawnEvent.class, this, EventPriority.MONITOR,
                    fastEvent(event -> {
                        try {
                            Object locObj = mythicSpawnGetLocation.invoke(event);
                            if (locObj instanceof org.bukkit.Location loc) {
                                World w = loc.getWorld();
                                if (w == null) return;
                                forNearbyPlayers(w, loc, 8, p -> engine.handle(p, MYTHICMOBS_ENTITY_SPAWN, (Event) event));
                            }
                        } catch (Throwable ignored) {}
                    }), plugin, true
            );
        } catch (Throwable ignored) {}

        try {
            mythicDeathGetKiller = MythicMobDeathEvent.class.getMethod("getKiller");
            mythicDeathGetLocation = MythicMobDeathEvent.class.getMethod("getLocation");
            Bukkit.getPluginManager().registerEvent(MythicMobDeathEvent.class, this, EventPriority.MONITOR,
                    fastEvent(event -> {
                        try {
                            Object killerObj = mythicDeathGetKiller.invoke(event);
                            if (killerObj instanceof Player p) {
                                engine.handle(p, MYTHICMOBS_KILL, (Event) event);
                                return;
                            }
                            Object locObj = mythicDeathGetLocation.invoke(event);
                            if (locObj instanceof org.bukkit.Location loc) {
                                World w = loc.getWorld();
                                if (w == null) return;
                                forNearbyPlayers(w, loc, 8, near -> engine.handle(near, MYTHICMOBS_KILL, (Event) event));
                            }
                        } catch (Throwable ignored) {}
                    }), plugin, true
            );
        } catch (Throwable ignored) {}
    }

    private static void forNearbyPlayers(World world, org.bukkit.Location loc, double radius, java.util.function.Consumer<Player> action) {
        try {
            Method m = World.class.getMethod("getNearbyPlayers", org.bukkit.Location.class, double.class);
            Object players = m.invoke(world, loc, radius);
            if (players instanceof Iterable<?> iterable) {
                for (Object obj : iterable) {
                    if (obj instanceof Player p) action.accept(p);
                }
                return;
            }
        } catch (Throwable ignored) {}
        double r2 = radius * radius;
        for (Player p : world.getPlayers()) {
            if (p.getWorld() == world && p.getLocation().distanceSquared(loc) <= r2) {
                action.accept(p);
            }
        }
    }

    private static EventExecutor fastEvent(Consumer<Event> handler) {
        return (listener, event) -> handler.accept(event);
    }

    private interface Registrar { void register(Class<?> eventClass) throws Exception; }

    private static void tryRegister(String className, Registrar task) {
        try {
            Class<?> cls = Class.forName(className);
            task.register(cls);
        } catch (Throwable ignored) {}
    }
}
