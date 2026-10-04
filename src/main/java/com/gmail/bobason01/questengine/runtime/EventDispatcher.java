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
import org.bukkit.event.vehicle.VehicleEnterEvent;
import org.bukkit.event.vehicle.VehicleExitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.projectiles.ProjectileSource;

import java.lang.reflect.Method;

public final class EventDispatcher implements Listener {

    private final Engine engine;
    private Method isMenuClickMethod;
    private Object apiInstance;

    @SuppressWarnings("unchecked")
    public EventDispatcher(Plugin plugin, Engine engine) {
        this.engine = engine;

        try {
            Class<?> providerClass = Class.forName("com.gmail.bobason01.api.CraftSlotAPIProvider");
            Method getMethod = providerClass.getMethod("get");
            this.apiInstance = getMethod.invoke(null);

            if (this.apiInstance != null) {
                this.isMenuClickMethod = this.apiInstance.getClass().getMethod("isMenuClick", InventoryClickEvent.class);
                plugin.getLogger().info("[QuestEngine] CraftSlotAPI successfully injected via reflection.");
            }
        } catch (Exception ignored) {
        }

        Bukkit.getPluginManager().registerEvents(this, plugin);

        try {
            Class<? extends Event> guiEventClass = (Class<? extends Event>) Class.forName("com.gmail.bobason01.event.GUIOpenEvent");
            Bukkit.getPluginManager().registerEvent(guiEventClass, this, EventPriority.NORMAL, (listener, event) -> {
                if (guiEventClass.isInstance(event)) {
                    Player p = null;
                    try {
                        p = (Player) event.getClass().getMethod("getPlayer").invoke(event);
                    } catch (Throwable ignored) {}
                    if (p != null) handle(p, "GUIMANAGER_OPEN", event);
                }
            }, plugin, true);
            plugin.getLogger().info("[QuestEngine] Dynamically hooked into GUIManager's GUIOpenEvent.");
        } catch (Throwable ignored) {
        }

        try {
            Class<? extends Event> hotkeyEventClass = (Class<? extends Event>) Class.forName("com.gmail.bobason01.api.event.HotkeyInputEvent");
            Bukkit.getPluginManager().registerEvent(hotkeyEventClass, this, EventPriority.NORMAL, (listener, event) -> {
                if (hotkeyEventClass.isInstance(event)) {
                    Player p = null;
                    try {
                        p = (Player) event.getClass().getMethod("getPlayer").invoke(event);
                    } catch (Throwable ignored) {}
                    if (p != null) handle(p, "HOTKEY_INPUT", event);
                }
            }, plugin, true);
            plugin.getLogger().info("[QuestEngine] Dynamically hooked into HotkeyManager's HotkeyInputEvent.");
        } catch (Throwable ignored) {
        }

        // MythicMobDeathEvent — resolve getKiller() correctly (docs alias MYTHICMOBS_KILL)
        try {
            Class<? extends Event> mythicDeath =
                    (Class<? extends Event>) Class.forName("io.lumine.mythic.bukkit.events.MythicMobDeathEvent");
            Method getKiller = mythicDeath.getMethod("getKiller");
            Bukkit.getPluginManager().registerEvent(
                    mythicDeath,
                    this,
                    EventPriority.MONITOR,
                    (listener, event) -> {
                        if (!mythicDeath.isInstance(event)) return;
                        try {
                            Object killerObj = getKiller.invoke(event);
                            if (killerObj instanceof Player killer) {
                                handle(killer, "MYTHICMOBS_ENTITY_KILL", event);
                            }
                        } catch (Throwable ignored) {}
                    },
                    plugin,
                    true
            );
            plugin.getLogger().info("[QuestEngine] Dynamically hooked MythicMobDeathEvent.");
        } catch (Throwable ignored) {
        }

        // Paper CompostItemEvent (optional)
        try {
            Class<? extends Event> compost =
                    (Class<? extends Event>) Class.forName("io.papermc.paper.event.entity.CompostItemEvent");
            Bukkit.getPluginManager().registerEvent(
                    compost,
                    this,
                    EventPriority.MONITOR,
                    (listener, event) -> {
                        try {
                            Object entity = event.getClass().getMethod("getEntity").invoke(event);
                            if (entity instanceof Player p) handle(p, "COMPOSTING", event);
                        } catch (Throwable ignored) {}
                    },
                    plugin,
                    true
            );
        } catch (Throwable ignored) {
        }
    }

    private void handle(Player player, String key, Event event) {
        if (player != null) engine.handle(player, key, event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onCraftSlotClick(InventoryClickEvent e) {
        if (apiInstance == null || isMenuClickMethod == null) return;
        if (!(e.getWhoClicked() instanceof Player p)) return;

        try {
            boolean isMenuClick = (boolean) isMenuClickMethod.invoke(apiInstance, e);
            if (isMenuClick) {
                handle(p, "CRAFTSLOT_CLICK", e);
            }
        } catch (Exception ignored) {}
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent e) { handle(e.getPlayer(), "BLOCK_BREAK", e); }

    @EventHandler(ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent e) { handle(e.getPlayer(), "BLOCK_PLACE", e); }

    @EventHandler(ignoreCancelled = true)
    public void onFertilize(BlockFertilizeEvent e) {
        handle(e.getPlayer(), "BLOCK_FERTILIZING", e);
        handle(e.getPlayer(), "FARMING", e);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockIgnite(BlockIgniteEvent e) {
        if (e.getPlayer() != null) handle(e.getPlayer(), "BLOCK_IGNITE", e);
    }

    @EventHandler(ignoreCancelled = true)
    public void onCompost(InventoryMoveItemEvent e) { handle(null, "COMPOSTING", e); }

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

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        handle(e.getPlayer(), "PLAYER_LEAVE", e);
        engine.cleanupPlayer(e.getPlayer().getUniqueId());
    }

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

    @EventHandler(ignoreCancelled = true)
    public void onSneak(PlayerToggleSneakEvent e) {
        if (e.isSneaking()) handle(e.getPlayer(), "PLAYER_SNEAK", e);
    }

    @EventHandler(ignoreCancelled = true)
    public void onSprint(PlayerToggleSprintEvent e) {
        if (e.isSprinting()) handle(e.getPlayer(), "PLAYER_SPRINT", e);
    }

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
            handle(killer, "ENTITY_KILL", e);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onTame(EntityTameEvent e) {
        if (e.getOwner() instanceof Player p) handle(p, "TAMING", e);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreed(EntityBreedEvent e) {
        if (e.getBreeder() instanceof Player p) handle(p, "BREEDING", e);
    }

    @EventHandler(ignoreCancelled = true)
    public void onCure(EntityTransformEvent e) {
        if (e.getTransformReason() == EntityTransformEvent.TransformReason.CURED) {
            for (Entity near : e.getEntity().getNearbyEntities(10, 10, 10)) {
                if (near instanceof Player p) handle(p, "CURING", e);
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onSpawn(EntitySpawnEvent e) { handle(null, "ENTITY_SPAWN", e); }

    @EventHandler(ignoreCancelled = true)
    public void onProjectileLaunch(ProjectileLaunchEvent e) {
        ProjectileSource shooter = e.getEntity().getShooter();
        if (shooter instanceof Player p) handle(p, "PROJECTILE_LAUNCH", e);
    }

    @EventHandler(ignoreCancelled = true)
    public void onVehicleEnter(VehicleEnterEvent e) {
        if (e.getEntered() instanceof Player p) handle(p, "VEHICLE_ENTER", e);
    }

    @EventHandler(ignoreCancelled = true)
    public void onVehicleExit(VehicleExitEvent e) {
        if (e.getExited() instanceof Player p) handle(p, "VEHICLE_EXIT", e);
    }

    @EventHandler(ignoreCancelled = true)
    public void onItemHeld(PlayerItemHeldEvent e) {
        handle(e.getPlayer(), "ITEM_HELD", e);
        handle(e.getPlayer(), "ITEM_SELECT", e);
    }

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
    public void onItemDrop(PlayerDropItemEvent e) {
        handle(e.getPlayer(), "ITEM_DROP", e);
        handle(e.getPlayer(), "BLOCK_ITEM_DROPPING", e);
    }

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

    @EventHandler(ignoreCancelled = true)
    public void onFish(PlayerFishEvent e) { handle(e.getPlayer(), "FISHING", e); }

    @EventHandler(ignoreCancelled = true)
    public void onMilk(PlayerBucketFillEvent e) {
        if (e.getItemStack() != null && e.getItemStack().getType() == Material.MILK_BUCKET) {
            handle(e.getPlayer(), "MILKING", e);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent e) { handle(e.getPlayer(), "BUCKET_EMPTY", e); }

    @EventHandler(ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent e) { handle(e.getPlayer(), "BUCKET_FILL", e); }

    @EventHandler(ignoreCancelled = true)
    public void onSmelt(FurnaceSmeltEvent e) {
        // Attribute progress to nearest online player in the furnace world chunk (best-effort)
        if (e.getBlock() == null) return;
        Location loc = e.getBlock().getLocation();
        Player nearest = null;
        double best = 64 * 64;
        for (Player p : loc.getWorld().getPlayers()) {
            if (!p.getWorld().equals(loc.getWorld())) continue;
            double d = p.getLocation().distanceSquared(loc);
            if (d < best) {
                best = d;
                nearest = p;
            }
        }
        if (nearest != null) handle(nearest, "SMELTING", e);
    }

    @EventHandler(ignoreCancelled = true)
    public void onShear(PlayerShearEntityEvent e) { handle(e.getPlayer(), "SHEARING", e); handle(e.getPlayer(), "BLOCK_SHEARING", e); }

    @EventHandler(ignoreCancelled = true)
    public void onTrade(TradeSelectEvent e) {
        if (e.getWhoClicked() instanceof Player p) handle(p, "TRADING", e);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBarter(PiglinBarterEvent e) {
        for (Entity near : e.getEntity().getNearbyEntities(10, 10, 10)) {
            if (near instanceof Player p) handle(p, "PLAYER_BARTERING", e);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent e) {
        handle(e.getPlayer(), "ITEM_INTERACT", e);
        if (e.getAction() == Action.RIGHT_CLICK_BLOCK) {
            handle(e.getPlayer(), "FARMING", e);
            if (e.getItem() != null && e.getItem().getType().name().endsWith("_AXE")) {
                handle(e.getPlayer(), "BLOCK_STRIP", e);
            }
        }
    }
}