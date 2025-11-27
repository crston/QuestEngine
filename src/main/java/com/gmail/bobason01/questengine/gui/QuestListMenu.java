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

/**
 * QuestListMenu (Optimized)
 * - Stream API 제거 -> Loop 최적화 (렌더링 속도 향상)
 * - NamespacedKey 캐싱
 * - 불필요한 객체 생성 최소화
 */
public final class QuestListMenu implements Listener {

    private final QuestEnginePlugin plugin;
    private final NamespacedKey questIdKey;
    private final Pattern pagePattern = Pattern.compile("\\b(\\d+)\\b");

    // Grid Slots (미리 계산)
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
        String title = ChatColor.translateAlternateColorCodes('&',
                plugin.msg().get("gui.list.title").replace("%page%", String.valueOf(page + 1)));

        GuiHolder holder = new GuiHolder("Q_LIST");
        Inventory inv = Bukkit.createInventory(holder, 54, title);
        holder.setInventory(inv);

        fill(inv);
        drawTopBar(p, inv);
        drawBottomBar(inv);
        drawQuests(p, inv, page);

        plugin.gui().putSession(p, "list_page", page);
        plugin.gui().sound(p, "open");
        p.openInventory(inv);
    }

    private void drawQuests(Player p, Inventory inv, int page) {
        // 1. 활성 퀘스트 목록 가져오기
        List<String> activeIds = plugin.engine().progress().activeQuestIds(p.getUniqueId(), p.getName());
        List<QuestDef> quests = new ArrayList<>(activeIds.size());

        // 2. 검색어 준비
        String search = getSearch(p);
        String needle = (search == null || search.isBlank()) ? null : ChatColor.stripColor(search).toLowerCase(Locale.ROOT);

        // 3. 필터링 및 수집 (Loop 최적화)
        for (String id : activeIds) {
            QuestDef def = plugin.engine().quests().get(id);
            if (def == null) continue;

            if (needle != null) {
                // 검색어가 있으면 필터링
                String name = ChatColor.stripColor(displayNameOf(def)).toLowerCase(Locale.ROOT);
                boolean hit = name.contains(needle);
                if (!hit) {
                    List<String> lore = loreOf(def);
                    if (lore != null) {
                        for (String l : lore) {
                            if (ChatColor.stripColor(l).toLowerCase(Locale.ROOT).contains(needle)) {
                                hit = true;
                                break;
                            }
                        }
                    }
                }
                if (!hit) continue;
            }
            quests.add(def);
        }

        // 4. 정렬
        quests.sort(Comparator.comparing(this::displayNameOf, String.CASE_INSENSITIVE_ORDER));
        if (!getAsc(p)) Collections.reverse(quests);

        // 5. 페이지네이션
        int start = page * SLOTS.length;
        int end = Math.min(quests.size(), start + SLOTS.length);

        // 슬롯 초기화
        for (int s : SLOTS) inv.setItem(s, null);

        int slotIdx = 0;
        List<String> loreTemplate = plugin.msg().list("gui.lore.list");
        String cancelMsg = plugin.msg().get("gui.list.right_click_cancel");

        for (int i = start; i < end; i++) {
            QuestDef d = quests.get(i);
            int value = plugin.engine().progress().value(p.getUniqueId(), p.getName(), d.id);
            String reward = ChatColor.stripColor(d.display.reward == null ? "" : d.display.reward);

            List<String> lore = new ArrayList<>(loreTemplate.size() + 2);
            for (String line : loreTemplate) {
                lore.add(ChatColor.translateAlternateColorCodes('&', line
                        .replace("%value%", String.valueOf(value))
                        .replace("%target%", String.valueOf(d.amount))
                        .replace("%reward%", reward)));
            }
            lore.add(" ");
            lore.add(cancelMsg);

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
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', "&f" + displayNameOf(d)));
            meta.setLore(lore);
            if (d.display.customModelData > 0) meta.setCustomModelData(d.display.customModelData);

            // PersistentDataContainer에 ID 저장 (핵심)
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

        // 상단/하단 버튼 처리
        if (slot == 0) { // Search
            p.closeInventory();
            ChatInput.await(p, plugin.msg().get("gui.list.search_prompt"), (pp, text) -> {
                setSearch(pp, text);
                Bukkit.getScheduler().runTask(plugin, () -> open(pp, 0));
            });
            plugin.gui().sound(p, "click");
        }
        else if (slot == 1) { plugin.gui().openLeaderboard(p); plugin.gui().sound(p, "click"); }
        else if (slot == 2) { plugin.gui().openPublic(p); plugin.gui().sound(p, "click"); }
        else if (slot == 8) { // Sort
            plugin.gui().putSession(p, "list_sort_asc", !getAsc(p));
            Bukkit.getScheduler().runTaskLater(plugin, () -> open(p, page), 1L);
            plugin.gui().sound(p, "page");
        }
        else if (slot == 45) { // Prev
            if (page > 0) {
                Bukkit.getScheduler().runTaskLater(plugin, () -> open(p, page - 1), 1L);
                plugin.gui().sound(p, "page");
            }
        }
        else if (slot == 53) { // Next
            Bukkit.getScheduler().runTaskLater(plugin, () -> open(p, page + 1), 1L);
            plugin.gui().sound(p, "page");
        }
        else if (slot == 49) { // Page Input
            p.closeInventory();
            ChatInput.await(p, plugin.msg().get("gui.list.page_input_prompt"), (pp, text) -> {
                int to = 0;
                try { to = Math.max(0, Integer.parseInt(text.trim()) - 1); } catch (Exception ignored) {}
                int dest = to;
                Bukkit.getScheduler().runTaskLater(plugin, () -> open(pp, dest), 1L);
            });
            plugin.gui().sound(p, "click");
        }
        else {
            // 퀘스트 아이템 클릭 처리
            ItemStack clicked = e.getCurrentItem();
            if (clicked != null && clicked.hasItemMeta()) {
                String qid = clicked.getItemMeta().getPersistentDataContainer().get(questIdKey, PersistentDataType.STRING);
                if (qid != null && !qid.isEmpty() && e.getClick().isRightClick()) {
                    QuestDef q = plugin.engine().quests().get(qid);
                    if (q != null) {
                        plugin.gui().putSession(p, "confirm_target", q);
                        plugin.gui().putSession(p, "confirm_back_page", page);
                        plugin.gui().confirm().open(p, q);
                    }
                }
            }
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof GuiHolder gh && "Q_LIST".equals(gh.id())) {
            e.setCancelled(true);
        }
    }

    // --- Utils ---

    private void drawTopBar(Player p, Inventory inv) {
        if (isBtn("search")) inv.setItem(0, icon("search", "gui.list.search"));
        if (isBtn("leaderboard")) inv.setItem(1, icon("leaderboard", "gui.list.leaderboard"));
        if (isBtn("public")) inv.setItem(2, icon("public", "gui.list.public"));

        if (isBtn("sort")) {
            String order = plugin.msg().get(getAsc(p) ? "gui.list.order_asc" : "gui.list.order_desc");
            ItemStack item = icon("sort", "gui.list.sort");
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(meta.getDisplayName().replace("%order%", order));
                item.setItemMeta(meta);
            }
            inv.setItem(8, item);
        }
    }

    private void drawBottomBar(Inventory inv) {
        if (isBtn("prev")) inv.setItem(45, icon("prev", "gui.list.prev"));
        if (isBtn("page_input")) inv.setItem(49, icon("page_input", "gui.list.page_input"));
        if (isBtn("next")) inv.setItem(53, icon("next", "gui.list.next"));
    }

    private ItemStack icon(String key, String langKey) {
        String path = "gui.icons." + key;
        Material mat = Material.matchMaterial(plugin.getConfig().getString(path + ".material", "BOOK"));
        int model = plugin.getConfig().getInt(path + ".model", -1);
        String name = plugin.msg().get(langKey);
        // Lore는 config에서 가져오거나 lang에서 가져오도록 확장 가능하지만, 여기선 기본 처리
        return createIcon(mat, name, model);
    }

    private ItemStack createIcon(Material m, String name, int model) {
        ItemStack item = new ItemStack(m == null ? Material.BOOK : m);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));
        if (model > 0) meta.setCustomModelData(model);
        item.setItemMeta(meta);
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

    private List<String> loreOf(QuestDef q) {
        return q.display != null ? q.display.description : null;
    }
}