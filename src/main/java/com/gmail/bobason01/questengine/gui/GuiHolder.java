package com.gmail.bobason01.questengine.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * GuiHolder
 * QuestEngine GUI의 고유 식별자
 * - 인벤토리 구분용 ID를 가지고 있음
 * - GuiProtectionListener에서 GUI 식별 시 사용됨
 */
public final class GuiHolder implements InventoryHolder {

    private final String id;
    private Inventory inventory;

    public GuiHolder(String id) {
        this.id = id;
    }

    /**
     * GUI 식별용 ID
     * 예: Q_LIST, Q_PUBLIC, Q_LEADERBOARD 등
     */
    public String id() {
        return id;
    }

    /**
     * 인벤토리 참조 저장
     */
    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    /**
     * 인벤토리 반환
     */
    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
