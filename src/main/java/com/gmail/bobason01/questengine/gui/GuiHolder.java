package com.gmail.bobason01.questengine.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * GuiHolder
 * Simple holder with id and inventory reference
 */
public final class GuiHolder implements InventoryHolder {
    private final String id;
    private Inventory inventory;

    public GuiHolder(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
