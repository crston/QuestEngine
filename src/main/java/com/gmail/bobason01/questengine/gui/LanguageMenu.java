package com.gmail.bobason01.questengine.gui;

import com.gmail.bobason01.questengine.QuestEnginePlugin;
import com.gmail.bobason01.questengine.progress.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

public final class LanguageMenu implements Listener {

    private final QuestEnginePlugin plugin;

    public LanguageMenu(QuestEnginePlugin plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void open(Player p) {
        String title = plugin.msg().get(p, "language_selector_title");
        Inventory inv = Bukkit.createInventory(new GuiHolder("LANG_SELECT"), 27, title);

        List<String> langs = plugin.msg().getAvailableLanguages();
        int slot = 10;

        for (String code : langs) {
            if (slot > 16) break;
            inv.setItem(slot++, createLangIcon(code));
        }

        p.openInventory(inv);
    }

    private ItemStack createLangIcon(String code) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            String name = code.equalsIgnoreCase("ko") ? "&e한국어 (Korean)" : "&eEnglish (영어)";
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));
            meta.setLore(List.of(ChatColor.GRAY + "Code: " + code.toUpperCase()));
            item.setItemMeta(meta);
        }
        return item;
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof GuiHolder gh)) return;
        if (!"LANG_SELECT".equals(gh.id())) return;

        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;

        ItemStack clicked = e.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;

        // 로어에서 국가 코드 추출
        List<String> lore = clicked.getItemMeta().getLore();
        if (lore == null || lore.isEmpty()) return;

        String code = lore.get(0).replace(ChatColor.GRAY + "Code: ", "").toLowerCase();

        // 언어 변경 및 저장 대기열에 등록 (핵심 수정 부분)
        PlayerData data = plugin.progress().of(p.getUniqueId(), p.getName());
        data.setLanguage(code);
        plugin.progress().save(data); // ProgressRepository 에 저장을 지시하여 디스크/DB에 기록되게 함

        p.sendMessage(plugin.msg().get(p, "language_changed").replace("%lang%", code.toUpperCase()));

        p.closeInventory();
        plugin.gui().sound(p, "success");

        // 퀘스트 목록 다시 열기
        Bukkit.getScheduler().runTaskLater(plugin, () -> plugin.gui().list().open(p, 0), 1L);
    }
}