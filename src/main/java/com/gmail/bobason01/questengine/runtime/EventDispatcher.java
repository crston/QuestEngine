package com.gmail.bobason01.questengine.runtime;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.Map;

public final class EventDispatcher implements Listener {

    private final Engine engine;

    public EventDispatcher(Plugin plugin, Engine engine) {
        this.engine = engine;
        Bukkit.getPluginManager().registerEvents(this, plugin);

        if (Bukkit.getPluginManager().isPluginEnabled("Citizens")) {
            Bukkit.getPluginManager().registerEvents(new CitizensListener(engine), plugin);
        }
        if (Bukkit.getPluginManager().isPluginEnabled("MythicMobs")) {
            Bukkit.getPluginManager().registerEvents(new MythicMobsListener(engine), plugin);
        }
    }

    private void handle(Player player, String key, Event event) {
        if (player != null) engine.handle(player, key, event);
    }

    // --- BLOCK EVENTS ---

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent e) { handle(e.getPlayer(), "BLOCK_BREAK", e); }

    @EventHandler(ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent e) { handle(e.getPlayer(), "BLOCK_PLACE", e); }

    @EventHandler(ignoreCancelled = true)
    public void onFertilize(BlockFertilizeEvent e) { handle(e.getPlayer(), "BLOCK_FERTILIZING", e); }

    @EventHandler(ignoreCancelled = true)
    public void onCompost(InventoryMoveItemEvent e) {
        handle(null, "COMPOSTING", e);
    }

    // --- PLAYER MOVEMENTS & STATUS ---

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        Location from = e.getFrom();
        Location to = e.getTo();
        if (to == null) return;
        if (from.getBlockX() == to.getBlockX() && from.getBlockY() == to.getBlockY() && from.getBlockZ() == to.getBlockZ()) return;
        handle(e.getPlayer(), "PLAYER_WALK", e);
        handle(e.getPlayer(), "DISTANCE_FROM", e);
    }

    @EventHandler public void onJoin(PlayerJoinEvent e) { handle(e.getPlayer(), "PLAYER_PRE_JOIN", e); }
    @EventHandler public void onQuit(PlayerQuitEvent e) { handle(e.getPlayer(), "PLAYER_LEAVE", e); }
    @EventHandler public void onRespawn(PlayerRespawnEvent e) { handle(e.getPlayer(), "PLAYER_RESPAWN", e); }
    @EventHandler public void onDeath(PlayerDeathEvent e) { handle(e.getEntity(), "PLAYER_DEATH", e); }
    @EventHandler public void onTeleport(PlayerTeleportEvent e) { handle(e.getPlayer(), "PLAYER_TELEPORT", e); }
    @EventHandler public void onBedEnter(PlayerBedEnterEvent e) { handle(e.getPlayer(), "PLAYER_BED_ENTER", e); }
    @EventHandler public void onWorldChange(PlayerChangedWorldEvent e) { handle(e.getPlayer(), "PLAYER_WORLD_CHANGE", e); }

    @EventHandler(ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent e) { handle(e.getPlayer(), "PLAYER_CHAT", e); }

    @EventHandler(ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent e) { handle(e.getPlayer(), "PLAYER_COMMAND", e); }

    @EventHandler(ignoreCancelled = true)
    public void onExp(PlayerExpChangeEvent e) { handle(e.getPlayer(), "PLAYER_EXP_GAIN", e); }

    @EventHandler(ignoreCancelled = true)
    public void onLevel(PlayerLevelChangeEvent e) { handle(e.getPlayer(), "PLAYER_LEVELUP", e); }

    @EventHandler(ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent e) { handle(e.getPlayer(), "PLAYER_SWAP_HAND", e); }

    // --- COMBAT & ENTITY ---

    @EventHandler(ignoreCancelled = true)
    public void onAttack(EntityDamageByEntityEvent e) {
        if (e.getDamager() instanceof Player p) {
            handle(p, "PLAYER_ATTACK", e);
            handle(p, "DEAL_DAMAGE", e);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityKill(EntityDeathEvent e) {
        Player killer = e.getEntity().getKiller();
        if (killer != null) {
            handle(killer, "PLAYER_KILL", e);
            handle(killer, "MOBKILLING", e);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onTame(EntityTameEvent e) {
        if (e.getOwner() instanceof Player p) handle(p, "TAMING", e);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreed(EntityBreedEvent e) {
        if (e.getBreeder() instanceof Player p) handle(p, "BREEDNG", e);
    }

    @EventHandler(ignoreCancelled = true)
    public void onCure(EntityTransformEvent e) {
        if (e.getTransformReason() == EntityTransformEvent.TransformReason.CURED) {
            // 주변 플레이어 탐색 필요 (Curing 직접 이벤트에 플레이어 없음)
            for (Entity near : e.getEntity().getNearbyEntities(10, 10, 10)) {
                if (near instanceof Player p) handle(p, "CURING", e);
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onSpawn(EntitySpawnEvent e) {
        handle(null, "ENTITY_SPAWN", e);
    }

    // --- ITEM & INVENTORY ---

    @EventHandler(ignoreCancelled = true)
    public void onItemConsume(PlayerItemConsumeEvent e) { handle(e.getPlayer(), "ITEM_CONSUME", e); }

    @EventHandler(ignoreCancelled = true)
    public void onCraft(CraftItemEvent e) {
        if (e.getWhoClicked() instanceof Player p) handle(p, "ITEM_CRAFT", e);
    }

    @EventHandler(ignoreCancelled = true)
    public void onItemPickup(EntityPickupItemEvent e) {
        if (e.getEntity() instanceof Player p) handle(p, "ITEM_PICKUP", e);
    }

    @EventHandler(ignoreCancelled = true)
    public void onItemDrop(PlayerDropItemEvent e) { handle(e.getPlayer(), "ITEM_DROP", e); }

    @EventHandler(ignoreCancelled = true)
    public void onEnchant(EnchantItemEvent e) { handle(e.getEnchanter(), "ITEM_ENCHANT", e); handle(e.getEnchanter(), "ENCHANTING", e); }

    @EventHandler(ignoreCancelled = true)
    public void onItemBreak(PlayerItemBreakEvent e) { handle(e.getPlayer(), "ITEM_BREAK", e); }

    @EventHandler(ignoreCancelled = true)
    public void onItemDamage(PlayerItemDamageEvent e) { handle(e.getPlayer(), "ITEM_DAMAGE", e); }

    @EventHandler(ignoreCancelled = true)
    public void onItemMend(PlayerItemMendEvent e) { handle(e.getPlayer(), "ITEM_MENDING", e); }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryMove(InventoryClickEvent e) {
        if (e.getWhoClicked() instanceof Player p) handle(p, "ITEM_MOVE", e);
    }

    @EventHandler(ignoreCancelled = true)
    public void onArmor(InventoryClickEvent e) {
        if (e.getSlotType() == InventoryType.SlotType.ARMOR) {
            if (e.getWhoClicked() instanceof Player p) handle(p, "PLAYER_ARMOR", e);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onSmith(SmithItemEvent e) {
        if (e.getWhoClicked() instanceof Player p) handle(p, "SMITHING", e);
    }

    // --- FARMING & INTERACT ---

    @EventHandler(ignoreCancelled = true)
    public void onFish(PlayerFishEvent e) { handle(e.getPlayer(), "FISHING", e); }

    @EventHandler(ignoreCancelled = true)
    public void onMilk(PlayerBucketFillEvent e) {
        if (e.getBlockClicked() != null && e.getBlockClicked().getType() == Material.AIR) { // 소 클릭 체크
            handle(e.getPlayer(), "MILKING", e);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent e) { handle(e.getPlayer(), "BUCKET_EMPTY", e); }

    @EventHandler(ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent e) { handle(e.getPlayer(), "BUCKET_FILL", e); }

    @EventHandler(ignoreCancelled = true)
    public void onShear(PlayerShearEntityEvent e) { handle(e.getPlayer(), "SHEARING", e); handle(e.getPlayer(), "BLOCK_SHEARING", e); }

    @EventHandler(ignoreCancelled = true)
    public void onTrade(TradeSelectEvent e) {
        if (e.getWhoClicked() instanceof Player p) handle(p, "TRADING", e);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBarter(PiglinBarterEvent e) {
        // 주변 플레이어에게 바터링 이벤트 전송
        for (Entity near : e.getEntity().getNearbyEntities(10, 10, 10)) {
            if (near instanceof Player p) handle(p, "PLAYER_BARTERING", e);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent e) {
        handle(e.getPlayer(), "ITEM_INTERACT", e);
        if (e.getAction() == Action.RIGHT_CLICK_BLOCK) {
            handle(e.getPlayer(), "FARMING", e);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityInteractMain(PlayerInteractEntityEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        Entity target = e.getRightClicked();
        if (target.hasMetadata("NPC") || target.hasMetadata("MythicMob")) return;

        Map<String, Object> ctx = new HashMap<>();
        ctx.put("target_id", target.getType().name());
        engine.handleCustom(e.getPlayer(), "ENTITY_INTERACT", ctx);
    }

    // --- INNER CLASSES (HOOKS) ---

    private static class CitizensListener implements Listener {
        private final Engine engine;
        CitizensListener(Engine engine) { this.engine = engine; }

        @EventHandler(ignoreCancelled = true)
        public void onNpcInteract(PlayerInteractEntityEvent e) {
            if (e.getHand() != EquipmentSlot.HAND) return;
            Entity target = e.getRightClicked();
            if (!target.hasMetadata("NPC")) return;
            net.citizensnpcs.api.npc.NPC npc = net.citizensnpcs.api.CitizensAPI.getNPCRegistry().getNPC(target);
            if (npc == null) return;

            Map<String, Object> ctx = new HashMap<>();
            ctx.put("target_id", "CITIZENS_" + npc.getId());
            engine.handleCustom(e.getPlayer(), "ENTITY_INTERACT", ctx);
        }
    }

    private static class MythicMobsListener implements Listener {
        private final Engine engine;
        private final io.lumine.mythic.bukkit.BukkitAPIHelper api = new io.lumine.mythic.bukkit.BukkitAPIHelper();
        MythicMobsListener(Engine engine) { this.engine = engine; }

        @EventHandler(ignoreCancelled = true)
        public void onMythicDeath(io.lumine.mythic.bukkit.events.MythicMobDeathEvent e) {
            if (e.getKiller() instanceof Player p) engine.handle(p, "MYTHICMOBS_ENTITY_KILL", e);
        }

        @EventHandler(ignoreCancelled = true)
        public void onMythicSpawn(io.lumine.mythic.bukkit.events.MythicMobSpawnEvent e) {
            engine.handle(null, "MYTHICMOBS_ENTITY_SPAWN", e);
        }

        @EventHandler(ignoreCancelled = true)
        public void onInteract(PlayerInteractEntityEvent e) {
            if (e.getHand() != EquipmentSlot.HAND) return;
            if (api.isMythicMob(e.getRightClicked())) {
                io.lumine.mythic.core.mobs.ActiveMob am = api.getMythicMobInstance(e.getRightClicked());
                if (am != null) {
                    Map<String, Object> ctx = new HashMap<>();
                    ctx.put("target_id", "MYTHIC_" + am.getType().getInternalName());
                    engine.handleCustom(e.getPlayer(), "ENTITY_INTERACT", ctx);
                }
            }
        }
    }
}