package com.gmail.bobason01.questengine.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class GuiHolder implements InventoryHolder {

    private final String id;
    private Inventory inv;

    public GuiHolder(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public void setInventory(Inventory inv) {
        this.inv = inv;
    }

    @Override
    public Inventory getInventory() {
        return inv;
    }
}
