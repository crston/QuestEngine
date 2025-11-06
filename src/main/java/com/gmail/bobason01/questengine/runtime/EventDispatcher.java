package com.gmail.bobason01.questengine.runtime;

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
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.Objects;

/**
 * EventDispatcher
 * 단일 엔트리 포인트로 대부분의 게임 이벤트를 엔진에 전달
 * 존재하는 이벤트만 등록하고, 없는 이벤트는 자동으로 패스
 * 고빈도 이벤트는 최소 연산으로 필터링
 */
public final class EventDispatcher implements Listener {

    private final Plugin plugin;
    private final Engine engine;

    // 이벤트 키 상수
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
    private static final String MOBKILLING = "MOBKILLING";
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
    private static final String CHUNK_LOAD = "CHUNK_LOAD";

    // 플레이어 이동 트리거 최소 제곱거리(기본 0.64 = 0.8블록)
    private final double walkMinDistSq;

    public EventDispatcher(Plugin plugin, Engine engine) {
        this.plugin = plugin;
        this.engine = engine;
        this.walkMinDistSq = Math.max(0.01, plugin.getConfig().getDouble("performance.player-walk-min-dist-sq", 0.64));
        Bukkit.getPluginManager().registerEvents(this, plugin);
        registerOptionalPaperEvents();
        registerOptionalExternalEvents();
    }

    // 기본 Bukkit 계열 이벤트들

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent e) {
        engine.handle(e.getPlayer(), BLOCK_BREAK, e);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent e) {
        engine.handle(e.getPlayer(), BLOCK_PLACE, e);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockFertilize(BlockFertilizeEvent e) {
        if (e.getPlayer() != null) engine.handle(e.getPlayer(), BLOCK_FERTILIZING, e);
        engine.handle(e.getPlayer(), FARMING, e);
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent e) {
        Player killer = e.getEntity().getKiller();
        if (killer != null) {
            engine.handle(killer, MOBKILLING, e);
            engine.handle(killer, PLAYER_KILL, e);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onShear(PlayerShearEntityEvent e) {
        engine.handle(e.getPlayer(), SHEARING, e);
    }

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

    // 1.20.5+ 존재. 없으면 무시됨. 안전을 위해 try-catch 할 필요 없음. Spigot에도 있음.
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
    public void onInventoryMove(InventoryMoveItemEvent e) {
        // Hopper 등 블록 간 이동. 플레이어 주체 아님. 근처 플레이어에게 브로드캐스트할 필요 없음.
        // 엔진에서 컨텍스트로만 쓰고 싶다면 handleCustom로 바꿀 수 있음.
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent e) {
        if (e.getWhoClicked() instanceof Player p) {
            Inventory inv = e.getInventory();
            if (inv instanceof MerchantInventory) {
                engine.handle(p, TRADING, e);
            }
            engine.handle(p, ITEM_MOVE, e);
            // ITEM_REPAIR는 PrepareAnvilEvent에서 핸들링
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPrepareAnvil(PrepareAnvilEvent e) {
        if (e.getView() != null && e.getView().getPlayer() instanceof Player p) {
            engine.handle(p, ITEM_REPAIR, e);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityInteract(PlayerInteractEntityEvent e) {
        engine.handle(e.getPlayer(), ENTITY_INTERACT, e);
        // MILKING은 우유 통합 조건이 필요하면 별도 식별 로직 추가
    }

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

    // 채팅은 서버 버전에 따라 다른 이벤트가 있을 수 있음. 구형은 AsyncPlayerChatEvent, 신형은 Adventure 기반.
    @EventHandler(ignoreCancelled = true)
    public void onPlayerChat(AsyncPlayerChatEvent e) { engine.handle(e.getPlayer(), PLAYER_CHAT, e); }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerExp(PlayerExpChangeEvent e) { engine.handle(e.getPlayer(), PLAYER_EXP_GAIN, e); }

    @EventHandler(ignoreCancelled = true)
    public void onLevelChange(PlayerLevelChangeEvent e) { engine.handle(e.getPlayer(), PLAYER_LEVELUP, e); }

    @EventHandler public void onPreJoin(AsyncPlayerPreLoginEvent e) {
        // 이 이벤트는 플레이어 객체가 없음. 필요 시 이름과 UUID를 컨텍스트 캡쳐하는 확장 경로가 필요.
        // 여기서는 패스하거나 별도 엔진 API를 추가해서 처리.
    }

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

    // 걷기 트리거. 미세 이동 노이즈 억제를 위해 최소 제곱거리 기준 적용
    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        if (e.getFrom().getWorld() != e.getTo().getWorld()) return;
        double dx = e.getTo().getX() - e.getFrom().getX();
        double dy = e.getTo().getY() - e.getFrom().getY();
        double dz = e.getTo().getZ() - e.getFrom().getZ();
        if ((dx*dx + dy*dy + dz*dz) >= walkMinDistSq) {
            engine.handle(e.getPlayer(), PLAYER_WALK, e);
            engine.handle(e.getPlayer(), DISTANCE_FROM, e);
        }
    }

    // 선택 등록: Paper 전용 이벤트, 있으면 붙인다
    private void registerOptionalPaperEvents() {
        // PlayerItemBreakEvent -> ITEM_BREAK
        tryRegister("com.destroystokyo.paper.event.player.PlayerArmorChangeEvent", (cls) ->
                Bukkit.getPluginManager().registerEvent((Class<? extends Event>) cls, this, EventPriority.NORMAL,
                        (listener, event) -> {
                            try {
                                Method getPlayer = event.getClass().getMethod("getPlayer");
                                Player p = (Player) getPlayer.invoke(event);
                                engine.handle(p, PLAYER_ARMOR, (Event) event);
                            } catch (Throwable ignored) {}
                        }, plugin, true)
        );

        tryRegister("org.bukkit.event.player.PlayerItemBreakEvent", (cls) ->
                Bukkit.getPluginManager().registerEvent((Class<? extends Event>) cls, this, EventPriority.MONITOR,
                        (listener, event) -> {
                            try {
                                Method getPlayer = event.getClass().getMethod("getPlayer");
                                Player p = (Player) getPlayer.invoke(event);
                                engine.handle(p, ITEM_BREAK, (Event) event);
                            } catch (Throwable ignored) {}
                        }, plugin, true)
        );

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

        // PlayerItemMendEvent -> ITEM_MENDING (Paper)
        tryRegister("com.destroystokyo.paper.event.player.PlayerItemMendEvent", (cls) ->
                Bukkit.getPluginManager().registerEvent((Class<? extends Event>) cls, this, EventPriority.MONITOR,
                        (listener, event) -> {
                            try {
                                Method getPlayer = event.getClass().getMethod("getPlayer");
                                Player p = (Player) getPlayer.invoke(event);
                                engine.handle(p, ITEM_MENDING, (Event) event);
                            } catch (Throwable ignored) {}
                        }, plugin, true)
        );

        // TurtleEggHatchEvent -> HATCHING
        tryRegister("org.bukkit.event.entity.TurtleEggHatchEvent", (cls) ->
                Bukkit.getPluginManager().registerEvent((Class<? extends Event>) cls, this, EventPriority.MONITOR,
                        (listener, event) -> {
                            try {
                                // 근처 플레이어에게 브로드캐스트
                                Method getBlock = event.getClass().getMethod("getBlock");
                                var block = getBlock.invoke(event);
                                var loc = (org.bukkit.Location) block.getClass().getMethod("getLocation").invoke(block);
                                for (Player p : loc.getWorld().getPlayers()) {
                                    if (p.getLocation().distanceSquared(loc) <= 64.0) {
                                        engine.handle(p, HATCHING, (Event) event);
                                    }
                                }
                            } catch (Throwable ignored) {}
                        }, plugin, true)
        );

        // CompostItemEvent -> COMPOSTING
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

        // PiglinBarterEvent -> PLAYER_BARTERING
        tryRegister("org.bukkit.event.entity.PiglinBarterEvent", (cls) ->
                Bukkit.getPluginManager().registerEvent((Class<? extends Event>) cls, this, EventPriority.MONITOR,
                        (listener, event) -> {
                            try {
                                Method getEntity = event.getClass().getMethod("getEntity");
                                Object piglin = getEntity.invoke(event);
                                // 주변 플레이어 전달
                                Method getWorld = piglin.getClass().getMethod("getWorld");
                                World w = (World) getWorld.invoke(piglin);
                                Method getLocation = piglin.getClass().getMethod("getLocation");
                                org.bukkit.Location loc = (org.bukkit.Location) getLocation.invoke(piglin);
                                for (Player p : w.getPlayers()) {
                                    if (p.getLocation().distanceSquared(loc) <= 64.0) {
                                        engine.handle(p, PLAYER_BARTERING, (Event) event);
                                    }
                                }
                            } catch (Throwable ignored) {}
                        }, plugin, true)
        );

        // EntityTransformEvent with CURED -> CURING
        tryRegister("org.bukkit.event.entity.EntityTransformEvent", (cls) ->
                Bukkit.getPluginManager().registerEvent((Class<? extends Event>) cls, this, EventPriority.MONITOR,
                        (listener, event) -> {
                            try {
                                Method getTransformReason = event.getClass().getMethod("getTransformReason");
                                Object reason = getTransformReason.invoke(event);
                                if (reason != null && reason.toString().equalsIgnoreCase("CURED")) {
                                    Method getEntity = event.getClass().getMethod("getEntity");
                                    var ent = getEntity.invoke(event);
                                    var loc = (org.bukkit.Location) ent.getClass().getMethod("getLocation").invoke(ent);
                                    for (Player p : loc.getWorld().getPlayers()) {
                                        if (p.getLocation().distanceSquared(loc) <= 64.0) {
                                            engine.handle(p, CURING, (Event) event);
                                        }
                                    }
                                }
                            } catch (Throwable ignored) {}
                        }, plugin, true)
        );
    }

    // 외부 플러그인 이벤트 등록
    private void registerOptionalExternalEvents() {
        // MythicMobs Spawn
        tryRegister("io.lumine.mythic.api.events.MythicMobSpawnEvent", (cls) ->
                Bukkit.getPluginManager().registerEvent((Class<? extends Event>) cls, this, EventPriority.MONITOR,
                        (listener, event) -> {
                            try {
                                Method getLocation = event.getClass().getMethod("getLocation");
                                org.bukkit.Location loc = (org.bukkit.Location) getLocation.invoke(event);
                                for (Player p : loc.getWorld().getPlayers()) {
                                    if (p.getLocation().distanceSquared(loc) <= 64.0) {
                                        engine.handle(p, MYTHICMOBS_ENTITY_SPAWN, (Event) event);
                                    }
                                }
                            } catch (Throwable ignored) {}
                        }, plugin, true)
        );

        // Brewing 완료 이벤트는 서버마다 다름. BrewEvent는 기본 존재.
        // BrewEvent -> BREWING
        try {
            // 기존 BrewEvent를 직접 메서드로 이미 등록했어도 이중 등록되는 구조는 아님. 여기서는 보조로 남겨둠.
            // 의도적으로 별도 처리 없음.
        } catch (Throwable ignored) {}
    }

    // 리플렉션 기반 안전 등록
    private interface Registrar { void register(Class<?> eventClass) throws Exception; }

    private static void tryRegister(String className, Registrar task) {
        try {
            Class<?> cls = Class.forName(className);
            task.register(cls);
        } catch (Throwable ignored) {}
    }
}
