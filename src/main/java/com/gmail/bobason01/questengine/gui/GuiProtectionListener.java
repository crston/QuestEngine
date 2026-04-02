package com.gmail.bobason01.questengine.gui;

import com.gmail.bobason01.questengine.QuestEnginePlugin;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.inventory.Inventory;

public final class GuiProtectionListener implements Listener {

    public GuiProtectionListener(QuestEnginePlugin plugin) {
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    private boolean isProtected(Inventory inv) {
        return inv != null
                && inv.getHolder() instanceof GuiHolder gh
                && gh.id() != null
                && gh.id().startsWith("Q_");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onClick(InventoryClickEvent e) {
        Inventory top = e.getView().getTopInventory();
        if (!isProtected(top)) return;

        // 1. 상단 GUI 클릭은 무조건 취소 (아이템 빼기/넣기 방지)
        // 로직 처리는 QuestEditorMenu 등의 Listener에서 처리함
        if (e.getClickedInventory() == top) {
            e.setCancelled(true);
        }
        // 2. 하단(플레이어 인벤)에서 Shift 클릭으로 상단으로 보내는 행위 차단
        else if (e.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
            e.setCancelled(true);
        }
        // 3. 더블 클릭으로 아이템을 모으는 행위 차단 (GUI 아이템이 딸려오는 것 방지)
        else if (e.getAction() == InventoryAction.COLLECT_TO_CURSOR) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onDrag(InventoryDragEvent e) {
        if (isProtected(e.getView().getTopInventory())) {
            // 드래그 대상 슬롯 중에 상단 인벤토리가 하나라도 포함되면 취소
            int size = e.getView().getTopInventory().getSize();
            for (int slot : e.getRawSlots()) {
                if (slot < size) {
                    e.setCancelled(true);
                    return;
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onMoveItem(InventoryMoveItemEvent e) {
        // 호퍼나 다른 플러그인에 의한 이동 차단
        if (isProtected(e.getDestination()) || isProtected(e.getSource())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPickup(InventoryPickupItemEvent e) {
        if (isProtected(e.getInventory())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDrop(PlayerDropItemEvent e) {
        if (e.getPlayer().getOpenInventory() != null) {
            Inventory top = e.getPlayer().getOpenInventory().getTopInventory();
            if (isProtected(top)) {
            }
        }
    }
}