package com.gmail.bobason01.questengine.gui;

import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerDropItemEvent;

public final class GuiProtectionListener implements Listener {

    public GuiProtectionListener() {
        Bukkit.getPluginManager().registerEvents(this,
                Bukkit.getPluginManager().getPlugin("QuestEngine"));
    }

    // --------------------------------------------------------------
    // 클릭 이벤트: "아이템 이동"을 막되, 클릭 자체는 막지 않는다
    // --------------------------------------------------------------
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onClick(InventoryClickEvent e) {

        if (!(e.getInventory().getHolder() instanceof GuiHolder gh)) return;
        if (!gh.id().startsWith("Q_")) return;

        // 슬롯 클릭 허용 (onClick 전달됨)
        // 이동/드래그/집기 등만 차단
        switch (e.getAction()) {

            case MOVE_TO_OTHER_INVENTORY,
                 HOTBAR_MOVE_AND_READD,
                 HOTBAR_SWAP,
                 COLLECT_TO_CURSOR,
                 DROP_ONE_CURSOR,
                 DROP_ALL_CURSOR,
                 DROP_ONE_SLOT,
                 DROP_ALL_SLOT,
                 PLACE_ALL,
                 PLACE_SOME,
                 PLACE_ONE,
                 PICKUP_ALL,
                 PICKUP_HALF,
                 PICKUP_SOME,
                 PICKUP_ONE -> e.setCancelled(true);

            default -> {}
        }
    }

    // --------------------------------------------------------------
    // 드래그 시 GUI 내부는 차단
    // --------------------------------------------------------------
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = false)
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
