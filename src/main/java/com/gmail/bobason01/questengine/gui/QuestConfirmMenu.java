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
    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");

    public QuestConfirmMenu(QuestEnginePlugin plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void open(Player p, QuestDef q) {
        if (p == null || q == null) return;

        String qName = q.name != null ? q.name : q.id;

        // [FIX] Msg.get(p, path)로 플레이어 인자 추가
        String rawTitle = plugin.msg().get(p, "gui.confirm.title").replace("%quest%", qName);
        String title = format(p, rawTitle);

        Inventory inv = Bukkit.createInventory(new GuiHolder("Q_CONFIRM"), 27, title);

        // 버튼 텍스트 다국어화 (필요시 messages.yml에 키 추가)
        inv.setItem(11, icon(Material.LIME_WOOL, format(p, "&aYES")));
        inv.setItem(15, icon(Material.RED_WOOL, format(p, "&cNO")));

        plugin.gui().putSession(p, "confirm_target", q);
        p.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof GuiHolder gh)) return;
        if (!"Q_CONFIRM".equals(gh.id())) return;

        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;

        QuestDef q = (QuestDef) plugin.gui().getSession(p, "confirm_target");
        if (q == null) return;

        int slot = e.getRawSlot();

        // 이전 페이지 번호 가져오기
        int backPage = 0;
        Object bp = plugin.gui().getSession(p, "confirm_back_page");
        if (bp instanceof Integer i) backPage = i;

        if (slot == 11) { // YES: 퀘스트 포기
            plugin.engine().cancelQuest(p, q);

            // [FIX] Msg.get(p, path)로 플레이어 인자 추가
            String msgStr = plugin.msg().get(p, "gui.confirm.cancel_done")
                    .replace("%quest%", q.name != null ? q.name : q.id);
            p.sendMessage(format(p, msgStr));

            int finalBackPage = backPage;
            Bukkit.getScheduler().runTask(plugin, () -> plugin.gui().list().open(p, finalBackPage));
            return;
        }

        if (slot == 15) { // NO: 돌아가기
            int finalBackPage = backPage;
            Bukkit.getScheduler().runTask(plugin, () -> plugin.gui().list().open(p, finalBackPage));
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof GuiHolder gh && "Q_CONFIRM".equals(gh.id())) {
            e.setCancelled(true);
        }
    }

    private ItemStack icon(Material m, String name) {
        ItemStack it = new ItemStack(m);
        ItemMeta im = it.getItemMeta();
        if (im != null) {
            im.setDisplayName(name);
            it.setItemMeta(im);
        }
        return it;
    }

    private String format(Player p, String raw) {
        if (raw == null) return "";
        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI") && p != null) {
            raw = PlaceholderAPI.setPlaceholders(p, raw);
        }
        Matcher matcher = HEX_PATTERN.matcher(raw);
        StringBuffer buffer = new StringBuffer(raw.length());
        while (matcher.find()) {
            String group = matcher.group(1);
            matcher.appendReplacement(buffer, "§x§" + group.charAt(0) + "§" + group.charAt(1) +
                    "§" + group.charAt(2) + "§" + group.charAt(3) +
                    "§" + group.charAt(4) + "§" + group.charAt(5));
        }
        matcher.appendTail(buffer);
        return ChatColor.translateAlternateColorCodes('&', buffer.toString());
    }
}