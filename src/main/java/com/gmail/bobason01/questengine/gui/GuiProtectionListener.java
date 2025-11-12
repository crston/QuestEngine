package com.gmail.bobason01.questengine.gui;

import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerDropItemEvent;

/**
 * GuiProtectionListener
 * 모든 QuestEngine GUI에서 아이템 이동, 드래그, 드롭, 복사 방지
 * - shift-click, drag, number key, collect to cursor, drop 등 완전 차단
 */
public final class GuiProtectionListener implements Listener {

    public GuiProtectionListener() {
        Bukkit.getPluginManager().registerEvents(this, Bukkit.getPluginManager().getPlugin("QuestEngine"));
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof GuiHolder gh)) return;
        String id = gh.id();
        if (!id.startsWith("Q_")) return;

        e.setCancelled(true);
        if (e.getClickedInventory() == null) return;

        // 플레이어 인벤토리 클릭도 전부 취소
        if (e.getClickedInventory().getType() != InventoryType.CHEST) {
            e.setCancelled(true);
            return;
        }

        // 모든 액션 차단
        switch (e.getAction()) {
            case MOVE_TO_OTHER_INVENTORY, HOTBAR_MOVE_AND_READD,
                 HOTBAR_SWAP, COLLECT_TO_CURSOR, DROP_ONE_CURSOR,
                 DROP_ALL_CURSOR, DROP_ONE_SLOT, DROP_ALL_SLOT,
                 PLACE_ALL, PLACE_SOME, PLACE_ONE,
                 PICKUP_ALL, PICKUP_HALF, PICKUP_SOME, PICKUP_ONE -> e.setCancelled(true);
            default -> {}
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (!(e.getInventory().getHolder() instanceof GuiHolder gh)) return;
        if (!gh.id().startsWith("Q_")) return;

        for (int slot : e.getRawSlots()) {
            if (slot < e.getInventory().getSize()) {
                e.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent e) {
        if (e.getPlayer().getOpenInventory() == null) return;
        if (!(e.getPlayer().getOpenInventory().getTopInventory().getHolder() instanceof GuiHolder gh)) return;
        if (!gh.id().startsWith("Q_")) return;
        e.setCancelled(true);
    }

    @EventHandler
    public void onMove(InventoryMoveItemEvent e) {
        if (e.getDestination().getHolder() instanceof GuiHolder
                || e.getSource().getHolder() instanceof GuiHolder) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onPickup(InventoryPickupItemEvent e) {
        if (e.getInventory().getHolder() instanceof GuiHolder) {
            e.setCancelled(true);
        }
    }
}
