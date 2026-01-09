package com.gmail.bobason01.questengine.gui;

import com.gmail.bobason01.questengine.QuestEnginePlugin;
import com.gmail.bobason01.questengine.quest.QuestDef;
import me.clip.placeholderapi.PlaceholderAPI;
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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class QuestConfirmMenu implements Listener {

    private final QuestEnginePlugin plugin;
    // Hex 색상 패턴 (Engine과 동일)
    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");

    public QuestConfirmMenu(QuestEnginePlugin plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void open(Player p, QuestDef q) {
        if (p == null || q == null) return;

        String qName = q.name != null ? q.name : q.id;

        // 제목에도 포맷팅 적용 (Hex + & 코드 지원)
        String rawTitle = plugin.msg().get("gui.confirm.title").replace("%quest%", qName);
        String title = format(p, rawTitle);

        Inventory inv = Bukkit.createInventory(new GuiHolder("Q_CONFIRM"), 27, title);
        ((GuiHolder) inv.getHolder()).setInventory(inv);

        // 버튼들은 이미 § 코드를 쓰고 있어서 문제없지만, 혹시 모르니 format 적용
        inv.setItem(11, icon(Material.LIME_WOOL, format(p, "&aYES")));
        inv.setItem(15, icon(Material.RED_WOOL, format(p, "&cNO")));

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

            String msg = plugin.msg().get("gui.confirm.cancel_done")
                    .replace("%quest%", q.name != null ? q.name : q.id);
            p.sendMessage(format(p, msg));

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
        im.setDisplayName(name); // 이미 포맷팅된 문자열을 받음
        it.setItemMeta(im);
        return it;
    }

    // Engine 클래스와 동일한 포맷팅 헬퍼
    private String format(Player p, String raw) {
        if (raw == null) return "";

        // PAPI 지원 (플러그인이 설치된 경우)
        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI") && p != null) {
            raw = PlaceholderAPI.setPlaceholders(p, raw);
        }

        // Hex 처리
        Matcher matcher = HEX_PATTERN.matcher(raw);
        StringBuffer buffer = new StringBuffer(raw.length());
        while (matcher.find()) {
            String group = matcher.group(1);
            matcher.appendReplacement(buffer, "§x§" + group.charAt(0) + "§" + group.charAt(1) +
                    "§" + group.charAt(2) + "§" + group.charAt(3) +
                    "§" + group.charAt(4) + "§" + group.charAt(5));
        }
        matcher.appendTail(buffer);
        raw = buffer.toString();

        // & 코드 처리
        return ChatColor.translateAlternateColorCodes('&', raw);
    }
}