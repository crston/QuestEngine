package com.gmail.bobason01.questengine.gui;

import com.gmail.bobason01.questengine.QuestEnginePlugin;
import com.gmail.bobason01.questengine.quest.QuestDef;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * QuestConfirmMenu
 * Simple yes no confirm menu for canceling a quest
 * Fully protected from extraction
 */
public final class QuestConfirmMenu implements Listener {

    private final QuestEnginePlugin plugin;

    public QuestConfirmMenu(QuestEnginePlugin plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void open(Player p, QuestDef quest) {
        String qName = quest.name != null ? quest.name : quest.id;
        String title = plugin.msg().get("gui.confirm.title").replace("%quest%", qName);

        Inventory inv = Bukkit.createInventory(new GuiHolder("Q_CONFIRM"), 27, title);
        ((GuiHolder) inv.getHolder()).setInventory(inv);

        inv.setItem(11, icon(Material.LIME_WOOL, "§aYES"));
        inv.setItem(15, icon(Material.RED_WOOL, "§cNO"));

        plugin.gui().sound(p, "open");
        p.openInventory(inv);

        plugin.gui().putSession(p, "confirm_target", quest);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof GuiHolder gh)) return;
        if (!"Q_CONFIRM".equals(gh.id())) return;

        e.setCancelled(true);
        if (e.getClickedInventory() == null) return;
        if (e.getClickedInventory().getType() != org.bukkit.event.inventory.InventoryType.CHEST) {
            e.setCancelled(true);
            return;
        }
        switch (e.getAction()) {
            case MOVE_TO_OTHER_INVENTORY, HOTBAR_MOVE_AND_READD, HOTBAR_SWAP, COLLECT_TO_CURSOR,
                 DROP_ONE_CURSOR, DROP_ALL_CURSOR, DROP_ONE_SLOT, DROP_ALL_SLOT,
                 PLACE_ALL, PLACE_SOME, PLACE_ONE, PICKUP_ALL, PICKUP_HALF,
                 PICKUP_SOME, PICKUP_ONE -> e.setCancelled(true);
            default -> {}
        }

        if (!(e.getWhoClicked() instanceof Player p)) return;

        QuestDef quest = (QuestDef) plugin.gui().getSession(p, "confirm_target");
        if (quest == null) return;

        if (e.getRawSlot() == 11) {
            plugin.engine().cancelQuest(p, quest.id);
            p.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    "&eQuest &f" + (quest.name != null ? quest.name : quest.id) + " &ehas been cancelled."));
            plugin.gui().sound(p, "cancel");
            Bukkit.getScheduler().runTaskLater(plugin, () -> plugin.gui().openList(p), 1L);
            plugin.gui().putSession(p, "confirm_target", null);
        }

        if (e.getRawSlot() == 15) {
            plugin.gui().sound(p, "click");
            Bukkit.getScheduler().runTaskLater(plugin, () -> plugin.gui().openList(p), 1L);
            plugin.gui().putSession(p, "confirm_target", null);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (!(e.getInventory().getHolder() instanceof GuiHolder gh)) return;
        if (!"Q_CONFIRM".equals(gh.id())) return;
        for (int slot : e.getRawSlots()) {
            if (slot < e.getInventory().getSize()) {
                e.setCancelled(true);
                return;
            }
        }
    }

    private ItemStack icon(Material mat, String name) {
        ItemStack i = new ItemStack(mat);
        ItemMeta m = i.getItemMeta();
        m.setDisplayName(name);
        i.setItemMeta(m);
        return i;
    }
}
