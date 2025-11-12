package com.gmail.bobason01.questengine.util;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * ItemBuilder — safe fluent builder for ItemStack
 * Supports display name, lore, custom model data, item flags, etc.
 * Fully null-safe and optimized for GUI building.
 */
public final class ItemBuilder {

    private final ItemStack item;
    private final ItemMeta meta;
    private final List<String> lore = new ArrayList<>();

    public ItemBuilder(Material mat) {
        if (mat == null) mat = Material.BARRIER;
        this.item = new ItemStack(mat);
        this.meta = this.item.getItemMeta();
    }

    public ItemBuilder(ItemStack base) {
        if (base == null || base.getType() == Material.AIR) {
            this.item = new ItemStack(Material.BARRIER);
        } else {
            this.item = base.clone();
        }
        this.meta = this.item.getItemMeta();
        if (meta != null && meta.hasLore()) {
            lore.addAll(meta.getLore());
        }
    }

    /** Set display name with color codes (&) */
    public ItemBuilder setName(String name) {
        if (meta == null) return this;
        meta.setDisplayName(color(name));
        return this;
    }

    /** Add one line to lore */
    public ItemBuilder addLore(String line) {
        if (line == null) return this;
        lore.add(color(line));
        return this;
    }

    /** Replace full lore list */
    public ItemBuilder setLore(List<String> lines) {
        lore.clear();
        if (lines != null) {
            for (String s : lines) lore.add(color(s));
        }
        return this;
    }

    /** Set CustomModelData if supported */
    public ItemBuilder setModelData(int data) {
        if (meta != null) {
            try {
                meta.setCustomModelData(data);
            } catch (Throwable ignored) {}
        }
        return this;
    }

    /** Add all item flags for clean GUI icons */
    public ItemBuilder hideAllFlags() {
        if (meta != null) {
            for (ItemFlag f : ItemFlag.values()) {
                meta.addItemFlags(f);
            }
        }
        return this;
    }

    /** Build the final ItemStack */
    public ItemStack build() {
        if (meta != null) {
            if (!lore.isEmpty()) meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private static String color(String s) {
        if (s == null) return "";
        return ChatColor.translateAlternateColorCodes('&', s);
    }
}
