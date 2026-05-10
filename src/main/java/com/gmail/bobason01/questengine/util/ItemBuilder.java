package com.gmail.bobason01.questengine.util;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public final class ItemBuilder {

    private ItemStack item;
    private ItemMeta meta;
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

    /** 아이템 종류(Material) 변경 */
    public ItemBuilder setType(Material mat) {
        if (mat != null) this.item.setType(mat);
        return this;
    }

    /**
     * 모델 설정 (Hybrid 지원)
     * @param model Integer(CustomModelData) 또는 String(ItemModel)
     */
    public ItemBuilder setModel(Object model) {
        if (meta == null || model == null) return this;

        if (model instanceof Integer i) {
            // 숫자라면 기존 CustomModelData 적용
            if (i == -1) meta.setCustomModelData(null);
            else meta.setCustomModelData(i);
        }
        else if (model instanceof String s && !s.isEmpty()) {
            // 문자열이라면 Item Model (1.21.4+) 적용 시도
            try {
                // 문자열이 숫자 형태라면 CustomModelData로 우선 처리
                int parsedInt = Integer.parseInt(s);
                meta.setCustomModelData(parsedInt);
            } catch (NumberFormatException e) {
                // 순수 문자열인 경우에만 Item Model로 처리
                try {
                    NamespacedKey key = NamespacedKey.fromString(s);
                    if (key != null) meta.setItemModel(key);
                } catch (Throwable ignored) {
                    // 서버 버전이 낮아 setItemModel 메서드가 없는 경우 무시
                }
            }
        }
        return this;
    }

    public ItemBuilder setName(String name) {
        if (meta == null) return this;
        meta.setDisplayName(color(name));
        return this;
    }

    public ItemBuilder addLore(String line) {
        if (line == null) return this;
        lore.add(color(line));
        return this;
    }

    public ItemBuilder setLore(List<String> lines) {
        lore.clear();
        if (lines != null) {
            for (String s : lines) lore.add(color(s));
        }
        return this;
    }

    public ItemBuilder hideAllFlags() {
        if (meta != null) {
            for (ItemFlag f : ItemFlag.values()) {
                meta.addItemFlags(f);
            }
        }
        return this;
    }

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