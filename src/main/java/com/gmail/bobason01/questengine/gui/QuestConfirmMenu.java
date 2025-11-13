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

public final class QuestConfirmMenu implements Listener {

    private final QuestEnginePlugin plugin;

    public QuestConfirmMenu(QuestEnginePlugin plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void open(Player p, QuestDef q) {
        if (p == null || q == null) return;

        String qName = q.name != null ? q.name : q.id;
        String title = ChatColor.translateAlternateColorCodes('&',
                plugin.msg().get("gui.confirm.title").replace("%quest%", qName));

        Inventory inv = Bukkit.createInventory(new GuiHolder("Q_CONFIRM"), 27, title);
        ((GuiHolder) inv.getHolder()).setInventory(inv);

        inv.setItem(11, icon(Material.LIME_WOOL, "§aYES"));
        inv.setItem(15, icon(Material.RED_WOOL, "§cNO"));

        plugin.gui().putSession(p, "confirm_target", q);

        p.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof GuiHolder gh)) return;
        if (!"Q_CONFIRM".equals(gh.id())) return;

        if (!(e.getWhoClicked() instanceof Player p)) return;
        QuestDef q = (QuestDef) plugin.gui().getSession(p, "confirm_target");
        if (q == null) return;

        e.setCancelled(true);

        int slot = e.getRawSlot();

        int backPage;
        Object bp = plugin.gui().getSession(p, "confirm_back_page");
        if (bp instanceof Integer i) backPage = i;
        else {
            backPage = 0;
        }

        if (slot == 11) {
            plugin.engine().cancelQuest(p, q.id);
            p.sendMessage(
                    plugin.msg().get("gui.confirm.cancel_done")
                            .replace("%quest%", q.name != null ? q.name : q.id)
            );
            Bukkit.getScheduler().runTask(plugin, () -> plugin.gui().openList(p));
            Bukkit.getScheduler().runTask(plugin, () -> plugin.gui().list().open(p, backPage));
            return;
        }

        if (slot == 15) {
            Bukkit.getScheduler().runTask(plugin, () -> plugin.gui().list().open(p, backPage));
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

    private ItemStack icon(Material m, String name) {
        ItemStack it = new ItemStack(m);
        ItemMeta im = it.getItemMeta();
        im.setDisplayName(name);
        it.setItemMeta(im);
        return it;
    }
}
