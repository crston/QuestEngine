package com.gmail.bobason01.questengine.runtime;

import io.lumine.mythic.bukkit.BukkitAPIHelper;
import io.lumine.mythic.bukkit.events.MythicMobDeathEvent;
import io.lumine.mythic.core.mobs.ActiveMob;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.Bukkit;
import org.bukkit.Location;
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

/**
 * EventDispatcher (Optimized)
 * - PlayerMoveEvent: 블록 단위 이동만 감지 (TPS 방어 핵심)
 * - Soft Dependency: Citizens/MythicMobs가 없어도 안전하게 작동 (클래스 분리)
 * - Traffic Safety: 불필요한 이벤트 필터링 및 루프 최적화
 */
public final class EventDispatcher implements Listener {

    private final Engine engine;

    public EventDispatcher(Plugin plugin, Engine engine) {
        this.engine = engine;

        // 1. 기본 Bukkit 이벤트 등록
        Bukkit.getPluginManager().registerEvents(this, plugin);

        // 2. Citizens 존재 시에만 전용 리스너 등록 (NoClassDefFoundError 방지)
        if (Bukkit.getPluginManager().isPluginEnabled("Citizens")) {
            Bukkit.getPluginManager().registerEvents(new CitizensListener(engine), plugin);
            plugin.getLogger().info("[QuestEngine] Hooked into Citizens");
        }

        // 3. MythicMobs 존재 시에만 전용 리스너 등록
        if (Bukkit.getPluginManager().isPluginEnabled("MythicMobs")) {
            Bukkit.getPluginManager().registerEvents(new MythicMobsListener(engine), plugin);
            plugin.getLogger().info("[QuestEngine] Hooked into MythicMobs");
        }
    }

    // 헬퍼: 널 체크 최소화
    private void handle(Player player, String key, Event event) {
        if (player != null) engine.handle(player, key, event);
    }

    // ------------------------------------------------------------------------
    // [핵심 최적화] PLAYER MOVE
    // ------------------------------------------------------------------------
    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        // 최적화: 시점(Yaw/Pitch)만 변경된 경우 무시
        // XYZ 좌표가 변하지 않았으면 리턴 (정수형 Block 좌표 비교가 가장 빠름)
        Location from = e.getFrom();
        Location to = e.getTo();

        if (to == null) return; // 드물지만 방어 코드
        if (from.getBlockX() == to.getBlockX() &&
                from.getBlockY() == to.getBlockY() &&
                from.getBlockZ() == to.getBlockZ()) {
            return;
        }

        handle(e.getPlayer(), "PLAYER_WALK", e);
    }

    // ------------------------------------------------------------------------
    // [최적화] INTERACT (양손 문제 해결)
    // ------------------------------------------------------------------------
    @EventHandler(ignoreCancelled = true)
    public void onEntityInteract(PlayerInteractEntityEvent e) {
        // 최적화: 왼손 이벤트 무시
        if (e.getHand() != EquipmentSlot.HAND) return;

        Entity target = e.getRightClicked();
        if (target == null) return;

        // NPC나 MythicMobs 처리는 별도 리스너에서 하거나, 여기서 통합 처리
        // 성능을 위해 일반 엔티티만 여기서 빠르게 처리하고 나머지는 Hook 리스너에 위임 가능
        // 하지만 여기서는 통합성을 위해 ID 추출 로직을 사용하되, 가벼운 방식 적용

        String id = resolveSimpleTargetId(target);
        Map<String, Object> ctx = new HashMap<>();
        ctx.put("target_id", id);
        ctx.put("entity", target);

        engine.handleCustom(e.getPlayer(), "ENTITY_INTERACT", ctx);
    }

    private String resolveSimpleTargetId(Entity entity) {
        // 메타데이터 검사를 통해 무거운 API 호출 방지
        if (entity.hasMetadata("NPC")) return "CITIZENS_" + entity.getEntityId(); // 실제 ID 조회는 CitizensListener에 위임 권장
        return entity.getType().name();
    }

    // ------------------------------------------------------------------------
    // BLOCK EVENTS
    // ------------------------------------------------------------------------
    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent e) { handle(e.getPlayer(), "BLOCK_BREAK", e); }

    @EventHandler(ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent e) { handle(e.getPlayer(), "BLOCK_PLACE", e); }

    // ------------------------------------------------------------------------
    // ITEM / INVENTORY
    // ------------------------------------------------------------------------
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
    public void onEnchant(EnchantItemEvent e) { handle(e.getEnchanter(), "ITEM_ENCHANT", e); }

    @EventHandler(ignoreCancelled = true)
    public void onFish(PlayerFishEvent e) { handle(e.getPlayer(), "FISHING", e); }

    // ------------------------------------------------------------------------
    // PLAYER CORE
    // ------------------------------------------------------------------------
    @EventHandler public void onJoin(PlayerJoinEvent e) { handle(e.getPlayer(), "PLAYER_PRE_JOIN", e); }
    @EventHandler public void onQuit(PlayerQuitEvent e) { handle(e.getPlayer(), "PLAYER_LEAVE", e); }
    @EventHandler public void onRespawn(PlayerRespawnEvent e) { handle(e.getPlayer(), "PLAYER_RESPAWN", e); }

    @EventHandler(ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent e) { handle(e.getPlayer(), "PLAYER_CHAT", e); }

    @EventHandler(ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent e) { handle(e.getPlayer(), "PLAYER_COMMAND", e); }

    @EventHandler(ignoreCancelled = true)
    public void onLevelChange(PlayerLevelChangeEvent e) { handle(e.getPlayer(), "PLAYER_LEVELUP", e); }

    // ------------------------------------------------------------------------
    // ENTITY
    // ------------------------------------------------------------------------
    @EventHandler(ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent e) {
        Player killer = e.getEntity().getKiller();
        if (killer != null) handle(killer, "MOBKILLING", e);
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent e) {
        if (e.getDamager() instanceof Player p) handle(p, "DEAL_DAMAGE", e);
    }

    // ------------------------------------------------------------------------
    // [분리된 리스너] CITIZENS (플러그인이 있을 때만 로드됨)
    // ------------------------------------------------------------------------
    private static class CitizensListener implements Listener {
        private final Engine engine;

        CitizensListener(Engine engine) { this.engine = engine; }

        @EventHandler(ignoreCancelled = true)
        public void onNpcInteract(PlayerInteractEntityEvent e) {
            if (e.getHand() != EquipmentSlot.HAND) return;
            Entity target = e.getRightClicked();

            // hasMetadata 체크로 빠른 패스
            if (!target.hasMetadata("NPC")) return;

            NPC npc = CitizensAPI.getNPCRegistry().getNPC(target);
            if (npc == null) return;

            String key = "CITIZENS_" + npc.getId();

            // 맵 생성 최소화 (HashMap 사용)
            Map<String, Object> ctx = new HashMap<>();
            ctx.put("target_id", key);
            ctx.put("npc_id", npc.getId());
            ctx.put("entity", target);

            engine.handleCustom(e.getPlayer(), "ENTITY_INTERACT", ctx);
        }
    }

    // ------------------------------------------------------------------------
    // [분리된 리스너] MYTHICMOBS (플러그인이 있을 때만 로드됨)
    // ------------------------------------------------------------------------
    private static class MythicMobsListener implements Listener {
        private final Engine engine;
        private final BukkitAPIHelper api;

        MythicMobsListener(Engine engine) {
            this.engine = engine;
            this.api = new BukkitAPIHelper();
        }

        @EventHandler(ignoreCancelled = true)
        public void onMythicDeath(MythicMobDeathEvent e) {
            if (e.getKiller() instanceof Player p) {
                engine.handle(p, "MYTHICMOBS_ENTITY_KILL", e);
            }
        }

        @EventHandler(ignoreCancelled = true)
        public void onInteract(PlayerInteractEntityEvent e) {
            if (e.getHand() != EquipmentSlot.HAND) return;
            if (api.isMythicMob(e.getRightClicked())) {
                ActiveMob am = api.getMythicMobInstance(e.getRightClicked());
                if (am != null) {
                    String key = "MYTHIC_" + am.getType().getInternalName();
                    Map<String, Object> ctx = new HashMap<>();
                    ctx.put("target_id", key);
                    ctx.put("entity", e.getRightClicked());
                    engine.handleCustom(e.getPlayer(), "ENTITY_INTERACT", ctx);
                }
            }
        }
    }
}