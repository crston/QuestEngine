package com.gmail.bobason01.questengine.gui;

import com.gmail.bobason01.questengine.QuestEnginePlugin;
import com.gmail.bobason01.questengine.quest.QuestDef;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

/**
 * QuestConfirmMenu
 * - CustomModelData + ResourcePack Sound + Toggleable Buttons
 * - Cancel confirmation for quests
 * - TPS-safe, GC-free
 */
public final class QuestConfirmMenu implements Listener {

    private final QuestEnginePlugin plugin;

    public QuestConfirmMenu(QuestEnginePlugin plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void open(Player p, QuestDef quest) {
        if (p == null || quest == null) return;

        String qName = quest.name != null ? quest.name : quest.id;
        String title = ChatColor.translateAlternateColorCodes('&',
                plugin.msg().get("gui.confirm.title").replace("%quest%", qName));

        Inventory inv = Bukkit.createInventory(new GuiHolder("Q_CONFIRM"), 27, title);
        ((GuiHolder) inv.getHolder()).setInventory(inv);

        // YES 버튼
        if (isButtonEnabled("yes")) {
            inv.setItem(11, iconWithModel("yes",
                    plugin.msg().get("gui.confirm.yes", "&aYES")));
        }

        // NO 버튼
        if (isButtonEnabled("no")) {
            inv.setItem(15, iconWithModel("no",
                    plugin.msg().get("gui.confirm.no", "&cNO")));
        }

        playSounds(p, "open");
        p.openInventory(inv);

        plugin.gui().putSession(p, "confirm_target", quest);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof GuiHolder gh)) return;
        if (!"Q_CONFIRM".equals(gh.id())) return;

        e.setCancelled(true);
        if (e.getClickedInventory() == null) return;
        if (!(e.getWhoClicked() instanceof Player p)) return;

        QuestDef quest = (QuestDef) plugin.gui().getSession(p, "confirm_target");
        if (quest == null) return;

        int slot = e.getRawSlot();

        // YES
        if (slot == 11 && isButtonEnabled("yes")) {
            plugin.engine().cancelQuest(p, quest.id);
            p.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    plugin.msg().get("gui.confirm.cancelled")
                            .replace("%quest%", quest.name != null ? quest.name : quest.id)));
            playSounds(p, "cancel");
            Bukkit.getScheduler().runTaskLater(plugin, () -> plugin.gui().openList(p), 1L);
            plugin.gui().removeSession(p, "confirm_target");
            return;
        }

        // NO
        if (slot == 15 && isButtonEnabled("no")) {
            playSounds(p, "click");
            Bukkit.getScheduler().runTaskLater(plugin, () -> plugin.gui().openList(p), 1L);
            plugin.gui().removeSession(p, "confirm_target");
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

    // ==========================================================
    //  CONFIG-BASED ICON AND SOUND SYSTEM
    // ==========================================================

    private ItemStack iconWithModel(String key, String name) {
        String path = "gui.confirm.icons." + key;
        String matName = plugin.getConfig().getString(path + ".material", "WOOL");
        int model = plugin.getConfig().getInt(path + ".model", -1);
        Material mat = Material.matchMaterial(matName);
        if (mat == null) mat = Material.BOOK;

        ItemStack it = new ItemStack(mat);
        ItemMeta im = it.getItemMeta();
        if (name != null)
            im.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));
        if (model > 0)
            im.setCustomModelData(model);
        it.setItemMeta(im);
        return it;
    }

    private void playSounds(Player p, String key) {
        List<String> sounds = plugin.getConfig().getStringList("gui.sounds." + key);
        if (sounds.isEmpty()) return;
        for (String s : sounds) {
            try {
                Sound enumSound = Sound.valueOf(s);
                p.playSound(p.getLocation(), enumSound, 1f, 1f);
            } catch (IllegalArgumentException e) {
                // custom resourcepack sound
                p.playSound(p.getLocation(), s, 1f, 1f);
            }
        }
    }

    private boolean isButtonEnabled(String key) {
        return plugin.getConfig().getBoolean("gui.confirm.buttons." + key, true);
    }
}
