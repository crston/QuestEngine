package com.gmail.bobason01.questengine.runtime;

import io.lumine.mythic.bukkit.BukkitAPIHelper;
import io.lumine.mythic.bukkit.events.MythicMobDeathEvent;
import io.lumine.mythic.bukkit.events.MythicMobSpawnEvent;
import io.lumine.mythic.core.mobs.ActiveMob;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.Plugin;

import java.util.Map;

/**
 * EventDispatcher
 * QuestEngine 글로벌 이벤트 브로드캐스터
 * Citizens / MythicMobs / 일반 엔티티 완전 호환
 * Paper/Purpur 완벽 지원
 */
public final class EventDispatcher implements Listener {

    private final Plugin plugin;
    private final Engine engine;
    private final boolean hasCitizens;
    private final boolean hasMythic;
    private final BukkitAPIHelper mythicAPI;

    public EventDispatcher(Plugin plugin, Engine engine) {
        this.plugin = plugin;
        this.engine = engine;
        this.hasCitizens = Bukkit.getPluginManager().isPluginEnabled("Citizens");
        this.hasMythic = Bukkit.getPluginManager().isPluginEnabled("MythicMobs");
        this.mythicAPI = hasMythic ? new BukkitAPIHelper() : null;

        Bukkit.getPluginManager().registerEvents(this, plugin);
        plugin.getLogger().info("[QuestEngine] EventDispatcher fully registered (Citizens:" + hasCitizens + ", MythicMobs:" + hasMythic + ")");
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

    // ------------------------------------------------------------------------
    // 핵심: ENTITY_INTERACT (NPC / MythicMob / 일반 엔티티 전부 지원)
    // ------------------------------------------------------------------------
    @EventHandler(ignoreCancelled = true)
    public void onEntityInteract(PlayerInteractEntityEvent e) {
        Player p = e.getPlayer();
        Entity target = e.getRightClicked();
        if (target == null) return;

        String id = resolveTargetId(target);
        e.getPlayer().setMetadata("qe_last_interact", new FixedMetadataValue(plugin, id)); // optional debug marker

        // 커스텀 컨텍스트로 타깃 ID도 전달
        engine.handleCustom(p, "ENTITY_INTERACT", Map.of("target_id", id, "entity", target));
    }

    private String resolveTargetId(Entity entity) {
        // Citizens NPC 우선
        if (hasCitizens && CitizensAPI.hasImplementation() && CitizensAPI.getNPCRegistry().isNPC(entity)) {
            NPC npc = CitizensAPI.getNPCRegistry().getNPC(entity);
            if (npc != null) return "CITIZENS_" + npc.getId();
        }
        // MythicMobs 엔티티
        if (hasMythic && mythicAPI != null) {
            ActiveMob am = mythicAPI.getMythicMobInstance(entity);
            if (am != null) return "MYTHIC_" + am.getType().getInternalName();
        }
        // 일반 엔티티
        return entity.getType().name();
    }

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
