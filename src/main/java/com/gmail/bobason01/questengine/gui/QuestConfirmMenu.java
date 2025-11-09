package com.gmail.bobason01.questengine.gui;

import com.gmail.bobason01.questengine.QuestEnginePlugin;
import com.gmail.bobason01.questengine.quest.QuestDef;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

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

        // YES / NO 버튼만 표시
        ItemStack yes = icon(Material.LIME_WOOL, "§aYES");
        ItemStack no = icon(Material.RED_WOOL, "§cNO");

        inv.setItem(11, yes);
        inv.setItem(15, no);

        plugin.gui().sound(p, "open");
        p.openInventory(inv);
        plugin.gui().putSession(p, "confirm_target", quest);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof GuiHolder gh)) return;
        if (!"Q_CONFIRM".equals(gh.id())) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;

        QuestDef quest = (QuestDef) plugin.gui().getSession(p, "confirm_target");
        if (quest == null) return;

        String qName = quest.name != null ? quest.name : quest.id;

        if (e.getRawSlot() == 11) { // YES
            plugin.engine().cancelQuest(p, quest.id);
            p.sendMessage("§eQuest §f" + qName + " §ehas been cancelled.");
            plugin.gui().sound(p, "cancel");
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (p.isOnline()) plugin.gui().openList(p);
            }, 2L);
            plugin.gui().putSession(p, "confirm_target", null);
        }

        if (e.getRawSlot() == 15) { // NO
            plugin.gui().sound(p, "click");
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (p.isOnline()) plugin.gui().openList(p);
            }, 2L);
            plugin.gui().putSession(p, "confirm_target", null);
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
