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

public final class PublicQuestMenu implements Listener {

    private final QuestEnginePlugin plugin;
    private final NamespacedKey questIdKey;

    private static final int[] SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };

    public PublicQuestMenu(QuestEnginePlugin plugin) {
        this.plugin = plugin;
        this.questIdKey = new NamespacedKey(plugin, "qid");
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void open(Player p, int page) {
        if (p == null) return;

        String title = ChatColor.translateAlternateColorCodes('&',
                getMsg("gui.public.title", "&8Quest Board | Page %page%")
                        .replace("%page%", String.valueOf(page + 1)));

        GuiHolder holder = new GuiHolder("Q_PUBLIC");
        Inventory inv = Bukkit.createInventory(holder, 54, title);
        holder.setInventory(inv);

        // [수정] 배경 채우기 (이름 없는 유리판 사용)
        fill(inv);

        drawTopBar(p, inv);
        drawBottomBar(inv);
        drawQuests(p, inv, page);

        plugin.gui().putSession(p, "public_page", page);
        plugin.gui().sound(p, "open");
        p.openInventory(inv);
    }

    // [추가] 배경 채우기 메서드 (QuestListMenu와 동일하게 이름 숨김)
    private void fill(Inventory inv) {
        ItemStack filler = createIcon(Material.GRAY_STAINED_GLASS_PANE, " ", -1);
        for (int i = 0; i < inv.getSize(); i++) {
            inv.setItem(i, filler);
        }
    }

    // [헬퍼] 아이템 생성 (이름 및 모델 데이터 적용)
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

    private void drawQuests(Player p, Inventory inv, int page) {
        Collection<QuestDef> allQuests = plugin.engine().quests().all();
        Set<String> activeIds = new HashSet<>(plugin.engine().progress().activeQuestIds(p.getUniqueId(), p.getName()));

        List<QuestDef> visible = new ArrayList<>();
        String search = getSearch(p);
        String needle = (search == null || search.isBlank()) ? null : ChatColor.stripColor(search).toLowerCase(Locale.ROOT);

        for (QuestDef q : allQuests) {
            if (!q.isPublic) continue;
            if (activeIds.contains(q.id)) continue;

            if (needle != null) {
                String name = ChatColor.stripColor(displayNameOf(q)).toLowerCase(Locale.ROOT);
                if (!name.contains(needle)) continue;
            }
            visible.add(q);
        }

        visible.sort(Comparator.comparing(this::displayNameOf, String.CASE_INSENSITIVE_ORDER));

        int start = page * SLOTS.length;
        int end = Math.min(visible.size(), start + SLOTS.length);

        for (int s : SLOTS) inv.setItem(s, null);

        String rewardLabel = getMsg("gui.public.reward_label", "&eReward: &f");
        String leftClick = getMsg("gui.public.left_click_start", "&a[Left-Click] to Start");

        int slotIdx = 0;
        for (int i = start; i < end; i++) {
            QuestDef q = visible.get(i);
            List<String> lore = new ArrayList<>();

            if (q.display != null && q.display.description != null) {
                for (String line : q.display.description) {
                    lore.add(ChatColor.translateAlternateColorCodes('&', "&7" + line));
                }
            }

            if (q.display != null && q.display.reward != null && !q.display.reward.isBlank()) {
                lore.add(" ");
                lore.add(ChatColor.translateAlternateColorCodes('&', rewardLabel + q.display.reward));
            }

            lore.add(" ");
            lore.add(ChatColor.translateAlternateColorCodes('&', leftClick));

            inv.setItem(SLOTS[slotIdx++], createQuestIcon(q, lore));
        }
    }

    private ItemStack createQuestIcon(QuestDef q, List<String> lore) {
        Material mat = Material.BOOK;
        if (q.display != null && q.display.icon != null) {
            try { mat = Material.valueOf(q.display.icon.toUpperCase(Locale.ROOT)); } catch (Exception ignored) {}
        }

        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', "&f" + displayNameOf(q)));
            meta.setLore(lore);
            if (q.display != null && q.display.customModelData > 0) {
                meta.setCustomModelData(q.display.customModelData);
            }
            meta.getPersistentDataContainer().set(questIdKey, PersistentDataType.STRING, q.id);
            item.setItemMeta(meta);
        }
        return item;
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof GuiHolder gh)) return;
        if (!"Q_PUBLIC".equals(gh.id())) return;

        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;

        int slot = e.getRawSlot();
        int page = plugin.gui().getSession(p, "public_page") instanceof Integer i ? i : 0;

        if (slot == 0 && isBtn("back")) {
            plugin.gui().sound(p, "click");
            Bukkit.getScheduler().runTaskLater(plugin, () -> plugin.gui().openList(p, 0), 1L);
            return;
        }
        if (slot == 8 && isBtn("search")) {
            p.closeInventory();
            ChatInput.await(p, getMsg("gui.public.search_prompt", "Enter keyword:"), (pp, text) -> {
                setSearch(pp, text);
                Bukkit.getScheduler().runTask(plugin, () -> open(pp, 0));
            });
            plugin.gui().sound(p, "click");
            return;
        }
        if (slot == 45 && isBtn("prev")) {
            if (page > 0) {
                plugin.gui().sound(p, "page");
                Bukkit.getScheduler().runTaskLater(plugin, () -> open(p, page - 1), 1L);
            }
            return;
        }
        if (slot == 53 && isBtn("next")) {
            plugin.gui().sound(p, "page");
            Bukkit.getScheduler().runTaskLater(plugin, () -> open(p, page + 1), 1L);
            return;
        }

        ItemStack clicked = e.getCurrentItem();
        if (clicked != null && clicked.hasItemMeta()) {
            String qid = clicked.getItemMeta().getPersistentDataContainer().get(questIdKey, PersistentDataType.STRING);
            if (qid != null) {
                QuestDef q = plugin.engine().quests().get(qid);
                if (q != null && e.getClick().isLeftClick()) {
                    try {
                        plugin.engine().startQuest(p, q);
                        plugin.gui().sound(p, "success");
                        Bukkit.getScheduler().runTaskLater(plugin, () -> open(p, page), 1L);
                    } catch (Throwable t) {
                        p.sendMessage(getMsg("gui.public.error_accept", "&cError accepting quest."));
                        plugin.gui().sound(p, "cancel");
                    }
                }
            }
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof GuiHolder gh && "Q_PUBLIC".equals(gh.id())) {
            e.setCancelled(true);
        }
    }

    private String getMsg(String key, String def) {
        String val = plugin.msg().get(key);
        if (val == null || val.equals(key)) return def;
        return val;
    }

    private void drawTopBar(Player p, Inventory inv) {
        if (isBtn("back")) inv.setItem(0, icon("back", "gui.public.back"));
        if (isBtn("search")) inv.setItem(8, icon("search", "gui.public.search"));
    }

    private void drawBottomBar(Inventory inv) {
        if (isBtn("prev")) inv.setItem(45, icon("prev", "gui.public.prev"));
        if (isBtn("next")) inv.setItem(53, icon("next", "gui.public.next"));
    }

    private ItemStack icon(String key, String langKey) {
        String path = "gui.public.icons." + key;
        Material mat = Material.matchMaterial(plugin.getConfig().getString(path + ".material", "BOOK"));
        int model = plugin.getConfig().getInt(path + ".model", -1);
        String name = getMsg(langKey, langKey);
        return createIcon(mat, name, model);
    }

    private boolean isBtn(String key) { return plugin.getConfig().getBoolean("gui.public.buttons." + key, true); }
    private String getSearch(Player p) { Object v = plugin.gui().getSession(p, "public_search"); return v == null ? "" : v.toString(); }
    private void setSearch(Player p, String q) { plugin.gui().putSession(p, "public_search", q == null ? "" : q.trim()); }

    private String displayNameOf(QuestDef q) {
        if (q.display != null && q.display.title != null && !q.display.title.isBlank()) {
            return ChatColor.stripColor(q.display.title);
        }
        return q.name != null ? ChatColor.stripColor(q.name) : q.id;
    }
}