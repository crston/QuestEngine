package com.gmail.bobason01.questengine.gui;

import com.gmail.bobason01.questengine.QuestEnginePlugin;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * QuestGuiManager
 * Central manager for all QuestEngine GUIs
 * Manages sessions, sounds, basic utilities, and GUI entry points
 * Registers common GUI protection listener
 */
public final class QuestGuiManager {

    private final QuestEnginePlugin plugin;

    private final Map<UUID, Map<String, Object>> sessions = new ConcurrentHashMap<>();

    private final LeaderboardMenu leaderboardMenu;
    private final QuestListMenu questListMenu;
    private final PublicQuestMenu publicQuestMenu;
    private final QuestConfirmMenu confirmMenu;

    public QuestGuiManager(QuestEnginePlugin plugin) {
        this.plugin = plugin;

        // chat input bootstrap
        ChatInput.init(plugin);

        // sub guis
        this.leaderboardMenu = new LeaderboardMenu(plugin);
        this.questListMenu = new QuestListMenu(plugin);
        this.publicQuestMenu = new PublicQuestMenu(plugin);
        this.confirmMenu = new QuestConfirmMenu(plugin);

        // common protection listener
        // constructor self registers to Bukkit with plugin name QuestEngine
        try {
            new GuiProtectionListener();
        } catch (Throwable t) {
            Bukkit.getLogger().warning("[QuestGuiManager] GuiProtectionListener registration failed");
        }

        Bukkit.getLogger().info("[QuestGuiManager] Initialized");
    }

    public void openLeaderboard(Player p) {
        if (p == null) return;
        leaderboardMenu.open(p);
    }

    public void openList(Player p) {
        if (p == null) return;
        questListMenu.open(p, 0);
    }

    public void openPublic(Player p) {
        if (p == null) return;
        publicQuestMenu.open(p, 0);
    }

    public void openConfirm(Player p, com.gmail.bobason01.questengine.quest.QuestDef quest) {
        if (p == null || quest == null) return;
        confirmMenu.open(p, quest);
    }

    public void putSession(Player p, String key, Object value) {
        if (p == null || key == null) return;
        if (value == null) {
            Map<String, Object> map = sessions.get(p.getUniqueId());
            if (map != null) map.remove(key);
            return;
        }
        sessions.computeIfAbsent(p.getUniqueId(), k -> new ConcurrentHashMap<>()).put(key, value);
    }

    public Object getSession(Player p, String key) {
        if (p == null || key == null) return null;
        Map<String, Object> map = sessions.get(p.getUniqueId());
        return map == null ? null : map.get(key);
    }

    public void clearSession(Player p) {
        if (p == null) return;
        sessions.remove(p.getUniqueId());
    }

    public void sound(Player p, String type) {
        if (p == null || type == null) return;
        try {
            switch (type.toLowerCase()) {
                case "open" -> p.playSound(p.getLocation(), Sound.UI_TOAST_IN, 0.7f, 1.2f);
                case "page" -> p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);
                case "click" -> p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, 0.8f);
                case "cancel" -> p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.7f, 0.9f);
                case "success" -> p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.4f);
                default -> { /* no op */ }
            }
        } catch (Throwable ignored) {
        }
    }

    public void fill(Inventory inv, ItemStack filler) {
        if (inv == null || filler == null) return;
        try {
            ItemStack item = filler.clone();
            for (int i = 0; i < inv.getSize(); i++) {
                if (inv.getItem(i) == null) inv.setItem(i, item);
            }
        } catch (Throwable ignored) {
        }
    }

    public ItemStack getStatic(String key) {
        return null;
    }

    public QuestEnginePlugin plugin() {
        return plugin;
    }

    public Map<UUID, Map<String, Object>> sessions() {
        return sessions;
    }

    public LeaderboardMenu leaderboard() {
        return leaderboardMenu;
    }

    public QuestListMenu list() {
        return questListMenu;
    }

    public PublicQuestMenu publicMenu() {
        return publicQuestMenu;
    }

    public QuestConfirmMenu confirm() {
        return confirmMenu;
    }
}
