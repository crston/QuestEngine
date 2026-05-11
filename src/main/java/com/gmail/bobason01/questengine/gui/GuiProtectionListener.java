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

        if (e.getClickedInventory() == top) {
            e.setCancelled(true);
        }
        else if (e.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
            e.setCancelled(true);
        }
        else if (e.getAction() == InventoryAction.COLLECT_TO_CURSOR) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onDrag(InventoryDragEvent e) {
        if (isProtected(e.getView().getTopInventory())) {
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