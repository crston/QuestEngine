package com.gmail.bobason01.questengine.runtime;

import io.lumine.mythic.bukkit.events.MythicMobDeathEvent;
import io.lumine.mythic.bukkit.events.MythicMobSpawnEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.plugin.Plugin;

/**
 * EventDispatcher
 * - QuestEngine 전용 글로벌 이벤트 브로드캐스터
 * - 모든 주요 Bukkit + MythicMobs 이벤트 감지
 * - Paper/Purpur 완전 호환
 * - 고성능 구조, 중복 감지 없음
 */
public final class EventDispatcher implements Listener {

    private final Plugin plugin;
    private final Engine engine;

    public EventDispatcher(Plugin plugin, Engine engine) {
        this.plugin = plugin;
        this.engine = engine;
        Bukkit.getPluginManager().registerEvents(this, plugin);
        plugin.getLogger().info("[QuestEngine] EventDispatcher fully registered");
    }

    private void handle(Player player, String key, Event event) {
        if (player == null) return;
        engine.handle(player, key, event);
    }

    // ------------------------------------------------------------------------
    // BLOCK EVENTS
    // ------------------------------------------------------------------------
    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent e) { handle(e.getPlayer(), "BLOCK_BREAK", e); }

    @EventHandler(ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent e) { handle(e.getPlayer(), "BLOCK_PLACE", e); }

    @EventHandler(ignoreCancelled = true)
    public void onBlockFertilize(BlockFertilizeEvent e) { handle(e.getPlayer(), "BLOCK_FERTILIZING", e); }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBurn(BlockBurnEvent e) {
        for (Player p : e.getBlock().getWorld().getPlayers()) handle(p, "BLOCK_BURN", e);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent e) {
        for (Player p : e.getBlock().getWorld().getPlayers()) handle(p, "BLOCK_EXPLODE", e);
    }

    // ------------------------------------------------------------------------
    // ITEM / INVENTORY EVENTS
    // ------------------------------------------------------------------------
    @EventHandler(ignoreCancelled = true)
    public void onItemConsume(PlayerItemConsumeEvent e) { handle(e.getPlayer(), "ITEM_CONSUME", e); }

    @EventHandler(ignoreCancelled = true)
    public void onItemBreak(PlayerItemBreakEvent e) { handle(e.getPlayer(), "ITEM_BREAK", e); }

    @EventHandler(ignoreCancelled = true)
    public void onItemDamage(PlayerItemDamageEvent e) { handle(e.getPlayer(), "ITEM_DAMAGE", e); }

    @EventHandler(ignoreCancelled = true)
    public void onItemMend(PlayerItemMendEvent e) { handle(e.getPlayer(), "ITEM_MENDING", e); }

    @EventHandler(ignoreCancelled = true)
    public void onItemPickup(EntityPickupItemEvent e) {
        if (e.getEntity() instanceof Player p) handle(p, "ITEM_PICKUP", e);
    }

    @EventHandler(ignoreCancelled = true)
    public void onItemDrop(PlayerDropItemEvent e) { handle(e.getPlayer(), "ITEM_DROP", e); }

    @EventHandler(ignoreCancelled = true)
    public void onCraft(CraftItemEvent e) { handle((Player) e.getWhoClicked(), "ITEM_CRAFT", e); }

    @EventHandler(ignoreCancelled = true)
    public void onEnchant(EnchantItemEvent e) { handle(e.getEnchanter(), "ITEM_ENCHANT", e); }

    @EventHandler(ignoreCancelled = true)
    public void onAnvilRepair(PrepareAnvilEvent e) {
        if (e.getView().getPlayer() instanceof Player p) handle(p, "ITEM_REPAIR", e);
    }

    @EventHandler(ignoreCancelled = true)
    public void onSmithing(PrepareSmithingEvent e) {
        if (e.getView().getPlayer() instanceof Player p) handle(p, "SMITHING", e);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBrew(BrewEvent e) {
        for (Player p : e.getBlock().getWorld().getPlayers()) handle(p, "BREWING", e);
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent e) {
        if (e.getWhoClicked() instanceof Player p) handle(p, "ITEM_MOVE", e);
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent e) {
        if (e.getPlayer() instanceof Player p) handle(p, "INVENTORY_OPEN", e);
    }

    // ------------------------------------------------------------------------
    // PLAYER CORE EVENTS
    // ------------------------------------------------------------------------
    @EventHandler public void onJoin(PlayerJoinEvent e) { handle(e.getPlayer(), "PLAYER_PRE_JOIN", e); }

    @EventHandler public void onQuit(PlayerQuitEvent e) { handle(e.getPlayer(), "PLAYER_LEAVE", e); }

    @EventHandler public void onRespawn(PlayerRespawnEvent e) { handle(e.getPlayer(), "PLAYER_RESPAWN", e); }

    @EventHandler(ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent e) { handle(e.getPlayer(), "PLAYER_CHAT", e); }

    @EventHandler(ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent e) { handle(e.getPlayer(), "PLAYER_COMMAND", e); }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) { handle(e.getPlayer(), "PLAYER_WALK", e); }

    @EventHandler(ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent e) { handle(e.getPlayer(), "PLAYER_TELEPORT", e); }

    @EventHandler(ignoreCancelled = true)
    public void onBedEnter(PlayerBedEnterEvent e) { handle(e.getPlayer(), "PLAYER_BED_ENTER", e); }

    @EventHandler(ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent e) { handle(e.getPlayer(), "PLAYER_SWAP_HAND", e); }

    @EventHandler(ignoreCancelled = true)
    public void onExpChange(PlayerExpChangeEvent e) { handle(e.getPlayer(), "PLAYER_EXP_GAIN", e); }

    @EventHandler(ignoreCancelled = true)
    public void onLevelChange(PlayerLevelChangeEvent e) { handle(e.getPlayer(), "PLAYER_LEVELUP", e); }

    @EventHandler(ignoreCancelled = true)
    public void onArmor(PlayerItemHeldEvent e) { handle(e.getPlayer(), "PLAYER_ARMOR", e); }

    // ------------------------------------------------------------------------
    // ENTITY EVENTS
    // ------------------------------------------------------------------------
    @EventHandler(ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent e) {
        if (e.getEntity().getKiller() != null)
            handle(e.getEntity().getKiller(), "MOBKILLING", e);
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityTame(EntityTameEvent e) { handle((Player) e.getOwner(), "TAMING", e); }

    @EventHandler(ignoreCancelled = true)
    public void onEntityBreed(EntityBreedEvent e) {
        if (e.getBreeder() instanceof Player p) handle(p, "BREEDING", e);
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent e) {
        if (e.getDamager() instanceof Player p) handle(p, "DEAL_DAMAGE", e);
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityInteract(PlayerInteractEntityEvent e) { handle(e.getPlayer(), "ENTITY_INTERACT", e); }

    @EventHandler(ignoreCancelled = true)
    public void onFish(PlayerFishEvent e) { handle(e.getPlayer(), "FISHING", e); }

    // ------------------------------------------------------------------------
    // WORLD EVENTS
    // ------------------------------------------------------------------------
    @EventHandler public void onChunkLoad(ChunkLoadEvent e) {
        for (Player p : e.getWorld().getPlayers()) handle(p, "WORLD_CHUNK_LOAD", e);
    }

    // ------------------------------------------------------------------------
    // MYTHICMOBS EVENTS
    // ------------------------------------------------------------------------
    @EventHandler(ignoreCancelled = true)
    public void onMythicSpawn(MythicMobSpawnEvent e) {
        if (e.getEntity() != null && e.getEntity().getWorld() != null)
            for (Player p : e.getEntity().getWorld().getPlayers())
                handle(p, "MYTHICMOBS_ENTITY_SPAWN", e);
    }

    @EventHandler(ignoreCancelled = true)
    public void onMythicDeath(MythicMobDeathEvent e) {
        if (e.getKiller() instanceof Player p) handle(p, "MYTHICMOBS_ENTITY_KILL", e);
    }
}
