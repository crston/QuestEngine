package com.gmail.bobason01.questengine.gui;

import com.gmail.bobason01.questengine.QuestEnginePlugin;
import com.gmail.bobason01.questengine.quest.QuestDef;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class QuestListMenu implements Listener {

    private final QuestEnginePlugin plugin;
    private final NamespacedKey questIdKey;
    private final Pattern pagePattern = Pattern.compile("\\b(\\d+)\\b");

    private static final int[] SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };

    public QuestListMenu(QuestEnginePlugin plugin) {
        this.plugin = plugin;
        this.questIdKey = new NamespacedKey(plugin, "qid");
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void open(Player p, int page) {
        String title = plugin.msg().get(p, "gui.list.title").replace("%page%", String.valueOf(page + 1));
        GuiHolder holder = new GuiHolder("Q_LIST");
        Inventory inv = Bukkit.createInventory(holder, 54, title);
        holder.setInventory(inv);

        fill(inv);
        drawTopBar(p, inv);
        drawBottomBar(p, inv);
        drawQuests(p, inv, page);

        plugin.gui().putSession(p, "list_page", page);
        plugin.gui().sound(p, "open");
        p.openInventory(inv);
    }

    private void drawQuests(Player p, Inventory inv, int page) {
        List<String> activeIds = plugin.engine().progress().activeQuestIds(p.getUniqueId(), p.getName());
        List<QuestDef> quests = new ArrayList<>();

        String search = getSearch(p);
        String needle = (search == null || search.isBlank()) ? null : ChatColor.stripColor(search).toLowerCase(Locale.ROOT);

        for (String id : activeIds) {
            QuestDef def = plugin.engine().quests().get(id);
            if (def == null) continue;
            if (needle != null) {
                String name = ChatColor.stripColor(displayNameOf(def)).toLowerCase(Locale.ROOT);
                if (!name.contains(needle)) continue;
            }
            quests.add(def);
        }

        quests.sort(Comparator.comparing(this::displayNameOf, String.CASE_INSENSITIVE_ORDER));
        if (!getAsc(p)) Collections.reverse(quests);

        int start = page * SLOTS.length;
        int end = Math.min(quests.size(), start + SLOTS.length);

        for (int s : SLOTS) inv.setItem(s, null);

        int slotIdx = 0;
        List<String> loreTemplate = plugin.msg().list(p, "gui.lore.list");
        String clickGuide = plugin.msg().get(p, "gui.list.click_guide");

        for (int i = start; i < end; i++) {
            QuestDef d = quests.get(i);
            int value = plugin.engine().progress().of(p.getUniqueId(), p.getName()).valueOf(d.id);
            String reward = plugin.msg().color(d.display.reward);

            List<String> lore = new ArrayList<>();
            if (d.display.description != null) {
                for (String line : d.display.description) {
                    lore.add(ChatColor.translateAlternateColorCodes('&', "&7" + line));
                }
            }

            for (String line : loreTemplate) {
                lore.add(line.replace("%value%", String.valueOf(value))
                        .replace("%target%", String.valueOf(d.amount))
                        .replace("%reward%", reward));
            }

            lore.add(" ");
            lore.add(clickGuide);
            inv.setItem(SLOTS[slotIdx++], createQuestIcon(d, lore));
        }
    }

    private ItemStack createQuestIcon(QuestDef d, List<String> lore) {
        Material mat = Material.BOOK;
        if (d.display.icon != null) {
            try { mat = Material.valueOf(d.display.icon.toUpperCase(Locale.ROOT)); } catch (Exception ignored) {}
        }
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', "&f" + (d.display.title != null ? d.display.title : d.id)));
            meta.setLore(lore);
            if (d.display.customModelData > 0) meta.setCustomModelData(d.display.customModelData);
            meta.getPersistentDataContainer().set(questIdKey, PersistentDataType.STRING, d.id);
            item.setItemMeta(meta);
        }
        return item;
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof GuiHolder gh)) return;
        if (!"Q_LIST".equals(gh.id())) return;

        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;

        int slot = e.getRawSlot();
        int page = getCurrentPage(p, e);

        if (slot == 0) {
            p.closeInventory();
            ChatInput.await(p, plugin.msg().get(p, "gui.list.search_prompt"), (pp, text) -> {
                setSearch(pp, text);
                Bukkit.getScheduler().runTask(plugin, () -> open(pp, 0));
            });
            plugin.gui().sound(p, "click");
        }
        else if (slot == 1) { plugin.gui().openLeaderboard(p); plugin.gui().sound(p, "click"); }
        else if (slot == 2) { plugin.gui().openPublic(p, 0); plugin.gui().sound(p, "click"); }
        else if (slot == 3 && isBtn("language")) {
            plugin.gui().openLanguageMenu(p);
            plugin.gui().sound(p, "click");
        }
        else if (slot == 8) {
            plugin.gui().putSession(p, "list_sort_asc", !getAsc(p));
            Bukkit.getScheduler().runTaskLater(plugin, () -> open(p, page), 1L);
            plugin.gui().sound(p, "page");
        }
        else if (slot == 45 && page > 0) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> open(p, page - 1), 1L);
            plugin.gui().sound(p, "page");
        }
        else if (slot == 53) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> open(p, page + 1), 1L);
            plugin.gui().sound(p, "page");
        }
        else if (slot == 49) {
            p.closeInventory();
            ChatInput.await(p, plugin.msg().get(p, "gui.list.page_input_prompt"), (pp, text) -> {
                int dest = 0; try { dest = Math.max(0, Integer.parseInt(text.trim()) - 1); } catch (Exception ignored) {}
                int finalDest = dest; Bukkit.getScheduler().runTaskLater(plugin, () -> open(pp, finalDest), 1L);
            });
            plugin.gui().sound(p, "click");
        }
        else {
            ItemStack clicked = e.getCurrentItem();
            if (clicked == null || !clicked.hasItemMeta()) return;

            String qid = clicked.getItemMeta().getPersistentDataContainer().get(questIdKey, PersistentDataType.STRING);
            if (qid == null) return;

            QuestDef q = plugin.engine().quests().get(qid);
            if (q == null) return;

            if (e.getClick().isLeftClick()) {
                if (q.display != null) {
                    boolean handled = false;

                    if (q.display.leftClickTip != null && !q.display.leftClickTip.isEmpty()) {
                        p.sendMessage(applyPlaceholders(p, q.display.leftClickTip));
                        handled = true;
                    }
                    if (q.display.leftClickCommand != null && !q.display.leftClickCommand.isEmpty()) {
                        p.performCommand(applyPlaceholders(p, q.display.leftClickCommand));
                        handled = true;
                    }

                    // Hint Command 무조건 명령어로 처리
                    if (!handled && q.display.hint != null && !q.display.hint.isEmpty()) {
                        String cmd = q.display.hint;
                        if (cmd.startsWith("/")) cmd = cmd.substring(1);

                        // PAPI 및 변수 치환 후 실행
                        p.performCommand(applyPlaceholders(p, cmd));
                    }
                }
                p.closeInventory();
                plugin.gui().sound(p, "click");
            }
            else if (e.getClick().isRightClick()) {
                plugin.gui().putSession(p, "confirm_target", q);
                plugin.gui().putSession(p, "confirm_back_page", page);
                plugin.gui().confirm().open(p, q);
                plugin.gui().sound(p, "click");
            }
        }
    }

    private String applyPlaceholders(Player p, String text) {
        if (text == null || text.isEmpty()) return "";

        // 기본 색상 및 플레이어 이름 치환
        text = ChatColor.translateAlternateColorCodes('&', text.replace("%player%", p.getName()));

        // PlaceholderAPI 연동
        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            return me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(p, text);
        }

        return text;
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof GuiHolder gh && "Q_LIST".equals(gh.id())) e.setCancelled(true);
    }

    private void drawTopBar(Player p, Inventory inv) {
        if (isBtn("search")) inv.setItem(0, icon(p, "search", "gui.list.search"));
        if (isBtn("leaderboard")) inv.setItem(1, icon(p, "leaderboard", "gui.list.leaderboard"));
        if (isBtn("public")) inv.setItem(2, icon(p, "public", "gui.list.public"));
        if (isBtn("language")) inv.setItem(3, icon(p, "language", "gui.list.lang_button"));

        if (isBtn("sort")) {
            String orderStr = getAsc(p) ? "gui.list.order_asc" : "gui.list.order_desc";
            String order = plugin.msg().get(p, orderStr);
            ItemStack item = icon(p, "sort", "gui.list.sort");
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(meta.getDisplayName().replace("%order%", order));
                item.setItemMeta(meta);
            }
            inv.setItem(8, item);
        }
    }

    private void drawBottomBar(Player p, Inventory inv) {
        if (isBtn("prev")) inv.setItem(45, icon(p, "prev", "gui.list.prev"));
        if (isBtn("page_input")) inv.setItem(49, icon(p, "page_input", "gui.list.page_input"));
        if (isBtn("next")) inv.setItem(53, icon(p, "next", "gui.list.next"));
    }

    private ItemStack icon(Player p, String key, String langKey) {
        String path = "gui.icons." + key;
        Material mat = Material.matchMaterial(plugin.getConfig().getString(path + ".material", "BOOK"));
        int model = plugin.getConfig().getInt(path + ".model", -1);
        String name = plugin.msg().get(p, langKey);
        return createIcon(mat, name, model);
    }

    private ItemStack createIcon(Material m, String name, int model) {
        ItemStack item = new ItemStack(m == null ? Material.BOOK : m);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));
            if (model > 0) meta.setCustomModelData(model);
            item.setItemMeta(meta);
        }
        return item;
    }

    private void fill(Inventory inv) {
        ItemStack filler = createIcon(Material.GRAY_STAINED_GLASS_PANE, " ", -1);
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, filler);
    }

    private boolean isBtn(String key) { return plugin.getConfig().getBoolean("gui.list.buttons." + key, true); }
    private boolean getAsc(Player p) { Object v = plugin.gui().getSession(p, "list_sort_asc"); return !(v instanceof Boolean) || (Boolean) v; }
    private String getSearch(Player p) { Object v = plugin.gui().getSession(p, "list_search"); return v == null ? "" : v.toString(); }
    private void setSearch(Player p, String q) { plugin.gui().putSession(p, "list_search", q == null ? "" : q.trim()); }

    private int getCurrentPage(Player p, InventoryClickEvent e) {
        Object obj = plugin.gui().getSession(p, "list_page");
        if (obj instanceof Integer i) return i;
        try {
            Matcher m = pagePattern.matcher(ChatColor.stripColor(e.getView().getTitle()));
            if (m.find()) return Math.max(0, Integer.parseInt(m.group(1)) - 1);
        } catch (Exception ignored) {}
        return 0;
    }

    private String displayNameOf(QuestDef q) {
        if (q.display != null && q.display.title != null) return ChatColor.stripColor(q.display.title);
        return q.id;
    }
}