package com.gmail.bobason01.questengine.gui;

import com.gmail.bobason01.questengine.QuestEnginePlugin;
import com.gmail.bobason01.questengine.quest.QuestDef;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
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
import java.util.stream.Collectors;

public final class QuestListMenu implements Listener {

    private final QuestEnginePlugin plugin;
    private final Pattern pagePattern = Pattern.compile("\\b(\\d+)\\b");
    private final NamespacedKey questIdKey;

    public QuestListMenu(QuestEnginePlugin plugin) {
        this.plugin = plugin;
        this.questIdKey = new NamespacedKey(plugin, "qid");
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void open(Player p, int page) {
        String title = ChatColor.translateAlternateColorCodes('&',
                plugin.msg().get("gui.list.title").replace("%page%", String.valueOf(page + 1)));

        Inventory inv = Bukkit.createInventory(new GuiHolder("Q_LIST"), 54, title);
        ((GuiHolder) inv.getHolder()).setInventory(inv);

        fill(inv);
        drawTopBar(p, inv);
        drawBottomBar(inv);
        drawQuests(p, inv, page);

        plugin.gui().putSession(p, "list_page", page);

        playSounds(p, "open");
        p.openInventory(inv);
    }

    private void drawTopBar(Player p, Inventory inv) {
        if (isButtonEnabled("search")) {
            inv.setItem(0, iconWithModel("search",
                    plugin.msg().get("gui.list.search"),
                    List.of(plugin.msg().get("gui.list.search_lore"))));
        }

        if (isButtonEnabled("leaderboard")) {
            inv.setItem(1, iconWithModel("leaderboard",
                    plugin.msg().get("gui.list.leaderboard"),
                    List.of(plugin.msg().get("gui.list.leaderboard_lore"))));
        }

        if (isButtonEnabled("public")) {
            inv.setItem(2, iconWithModel("public",
                    plugin.msg().get("gui.list.public"),
                    List.of(plugin.msg().get("gui.list.public_lore"))));
        }

        if (isButtonEnabled("sort")) {
            String order = plugin.msg().get(getAsc(p) ? "gui.list.order_asc" : "gui.list.order_desc");
            inv.setItem(8, iconWithModel("sort",
                    plugin.msg().get("gui.list.sort").replace("%order%", order),
                    List.of(plugin.msg().get("gui.list.sort_lore"))));
        }
    }

    private void drawBottomBar(Inventory inv) {
        if (isButtonEnabled("prev")) {
            inv.setItem(45, iconWithModel("prev", plugin.msg().get("gui.list.prev"), null));
        }

        if (isButtonEnabled("page_input")) {
            inv.setItem(49, iconWithModel("page_input",
                    plugin.msg().get("gui.list.page_input"),
                    List.of(plugin.msg().get("gui.list.page_input_lore"))));
        }

        if (isButtonEnabled("next")) {
            inv.setItem(53, iconWithModel("next", plugin.msg().get("gui.list.next"), null));
        }
    }

    private void drawQuests(Player p, Inventory inv, int page) {
        List<String> activeIds =
                plugin.engine().progress().activeQuestIds(p.getUniqueId(), p.getName());

        List<QuestDef> all = activeIds.stream()
                .map(id -> plugin.engine().quests().get(id))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        String q = getSearch(p);
        if (q != null && !q.trim().isEmpty()) {
            String needle = ChatColor.stripColor(q).toLowerCase(Locale.ROOT);
            all = all.stream().filter(d -> {
                String name = ChatColor.stripColor(displayNameOf(d)).toLowerCase(Locale.ROOT);
                boolean nameHit = name.contains(needle);

                boolean loreHit = loreOf(d).stream()
                        .anyMatch(l -> ChatColor.stripColor(l).toLowerCase(Locale.ROOT).contains(needle));

                return nameHit || loreHit;
            }).collect(Collectors.toList());
        }

        all.sort(Comparator.comparing(this::displayNameOf, Comparator.nullsLast(String::compareToIgnoreCase)));
        if (!getAsc(p)) {
            Collections.reverse(all);
        }

        int start = page * 28;
        int end = Math.min(all.size(), start + 28);
        int[] slots = gridSlots();

        for (int s : slots) {
            inv.setItem(s, null);
        }

        int idx = 0;

        for (int i = start; i < end && idx < slots.length; i++) {
            QuestDef d = all.get(i);
            List<String> lore = new ArrayList<>();

            for (String line : descriptionOf(d)) {
                lore.add(ChatColor.translateAlternateColorCodes('&',
                        "&7" + ChatColor.stripColor(line)));
            }

            lore.add(" ");

            int value = plugin.engine().progress().value(p.getUniqueId(), p.getName(), d.id);
            lore.add(ChatColor.translateAlternateColorCodes('&',
                    "&aProgress: &f" + value + "/" + d.amount));

            String reward = rewardOf(d);
            if (reward != null && !reward.isBlank()) {
                lore.add(ChatColor.translateAlternateColorCodes('&',
                        "&eReward: &f" + ChatColor.stripColor(reward)));
            }

            lore.add(" ");
            lore.add(plugin.msg().get("gui.list.right_click_cancel"));

            inv.setItem(slots[idx++],
                    questIcon(iconOf(d),
                            ChatColor.translateAlternateColorCodes('&',
                                    "&f" + displayNameOf(d)),
                            lore,
                            d.display.customModelData,
                            d.id));
        }
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof GuiHolder gh)) return;
        if (!"Q_LIST".equals(gh.id())) return;

        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;

        int slot = e.getRawSlot();
        int page = getCurrentPage(p, e);

        switch (slot) {
            case 0 -> {
                p.closeInventory();
                ChatInput.get().await(p,
                        plugin.msg().get("gui.list.search_prompt"),
                        (pp, text) -> {
                            setSearch(pp, text == null ? "" : text.trim());
                            Bukkit.getScheduler().runTask(plugin, () -> open(pp, 0));
                        });
                playSounds(p, "click");
            }
            case 1 -> {
                plugin.gui().openLeaderboard(p);
                playSounds(p, "click");
            }
            case 2 -> {
                plugin.gui().openPublic(p);
                playSounds(p, "click");
            }
            case 8 -> {
                plugin.gui().putSession(p, "list_sort_asc", !getAsc(p));
                Bukkit.getScheduler().runTaskLater(plugin, () -> open(p, page), 1L);
                playSounds(p, "page");
            }
            case 45 -> {
                Bukkit.getScheduler().runTaskLater(plugin,
                        () -> open(p, Math.max(0, page - 1)), 1L);
                playSounds(p, "page");
            }
            case 53 -> {
                Bukkit.getScheduler().runTaskLater(plugin,
                        () -> open(p, page + 1), 1L);
                playSounds(p, "page");
            }
            case 49 -> {
                p.closeInventory();
                ChatInput.get().await(p,
                        plugin.msg().get("gui.list.page_input_prompt"),
                        (pp, text) -> {
                            int to = 0;
                            try {
                                to = Math.max(0, Integer.parseInt(text.trim()) - 1);
                            } catch (Exception ignored) {
                            }
                            int dest = to;
                            Bukkit.getScheduler().runTaskLater(plugin,
                                    () -> open(pp, dest), 1L);
                        });
                playSounds(p, "click");
            }
            default -> {
                ItemStack clicked = e.getCurrentItem();
                if (clicked == null || !clicked.hasItemMeta()) return;

                ItemMeta meta = clicked.getItemMeta();
                String qid = meta.getPersistentDataContainer().get(questIdKey, PersistentDataType.STRING);
                if (qid == null || qid.isEmpty()) return;

                QuestDef q = plugin.engine().quests().get(qid);
                if (q == null) return;

                if (e.getClick().isRightClick()) {
                    openConfirmCancel(p, q, page);
                }
            }
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (!(e.getInventory().getHolder() instanceof GuiHolder gh)) return;
        if (!"Q_LIST".equals(gh.id())) return;

        for (int slot : e.getRawSlots()) {
            if (slot < e.getInventory().getSize()) {
                e.setCancelled(true);
                return;
            }
        }
    }

    private void openConfirmCancel(Player p, QuestDef quest, int backPage) {
        plugin.gui().putSession(p, "confirm_target", quest);
        plugin.gui().putSession(p, "confirm_back_page", backPage);
        plugin.gui().confirm().open(p, quest);
    }

    private ItemStack iconWithModel(String key, String name, List<String> lore) {
        String path = "gui.icons." + key;
        String matName = plugin.getConfig().getString(path + ".material", "BOOK");
        int model = plugin.getConfig().getInt(path + ".model", -1);

        Material mat = Material.matchMaterial(matName);
        if (mat == null) mat = Material.BOOK;

        return icon(mat, name, lore, model);
    }

    private ItemStack icon(Material m, String name, List<String> lore, int model) {
        ItemStack it = new ItemStack(m == null ? Material.BOOK : m);
        ItemMeta im = it.getItemMeta();

        if (name != null) {
            im.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));
        }

        if (lore != null && !lore.isEmpty()) {
            List<String> colored = lore.stream()
                    .map(s -> ChatColor.translateAlternateColorCodes('&', s))
                    .collect(Collectors.toList());
            im.setLore(colored);
        }

        if (model > 0) {
            im.setCustomModelData(model);
        }

        it.setItemMeta(im);
        return it;
    }

    private ItemStack questIcon(Material m, String name, List<String> lore, int model, String questId) {
        ItemStack it = new ItemStack(m == null ? Material.BOOK : m);
        ItemMeta im = it.getItemMeta();

        if (name != null) {
            im.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));
        }

        if (lore != null && !lore.isEmpty()) {
            List<String> colored = lore.stream()
                    .map(s -> ChatColor.translateAlternateColorCodes('&', s))
                    .collect(Collectors.toList());
            im.setLore(colored);
        }

        if (model > 0) {
            im.setCustomModelData(model);
        }

        if (questId != null && !questId.isEmpty()) {
            im.getPersistentDataContainer().set(questIdKey, PersistentDataType.STRING, questId);
        }

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
            } catch (IllegalArgumentException ex) {
                p.playSound(p.getLocation(), s, 1f, 1f);
            }
        }
    }

    private boolean isButtonEnabled(String key) {
        return plugin.getConfig().getBoolean("gui.list.buttons." + key, true);
    }

    private boolean getAsc(Player p) {
        Object v = plugin.gui().getSession(p, "list_sort_asc");
        return v instanceof Boolean b ? b : true;
    }

    private String getSearch(Player p) {
        Object v = plugin.gui().getSession(p, "list_search");
        return v == null ? "" : v.toString();
    }

    private void setSearch(Player p, String q) {
        plugin.gui().putSession(p, "list_search", q == null ? "" : q);
    }

    private int getCurrentPage(Player p, InventoryClickEvent e) {
        Object obj = plugin.gui().getSession(p, "list_page");
        if (obj instanceof Integer i) return i;

        String title = ChatColor.stripColor(e.getView().getTitle());
        Matcher m = pagePattern.matcher(title);

        if (m.find()) {
            try {
                return Math.max(0, Integer.parseInt(m.group(1)) - 1);
            } catch (NumberFormatException ignored) {
            }
        }
        return 0;
    }

    private int[] gridSlots() {
        return new int[]{
                10, 11, 12, 13, 14, 15, 16,
                19, 20, 21, 22, 23, 24, 25,
                28, 29, 30, 31, 32, 33, 34,
                37, 38, 39, 40, 41, 42, 43
        };
    }

    private void fill(Inventory inv) {
        ItemStack f = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta im = f.getItemMeta();
        im.setDisplayName(" ");
        f.setItemMeta(im);

        for (int i = 0; i < inv.getSize(); i++) {
            inv.setItem(i, f);
        }
    }

    private List<String> descriptionOf(QuestDef q) {
        if (q == null || q.display == null) return Collections.emptyList();
        return q.display.description == null ? Collections.emptyList() : q.display.description;
    }

    private String rewardOf(QuestDef q) {
        if (q == null || q.display == null) return "";
        return q.display.reward == null ? "" : q.display.reward;
    }

    private String displayNameOf(QuestDef q) {
        if (q == null) return "unknown";

        if (q.display != null && q.display.title != null && !q.display.title.isBlank()) {
            return ChatColor.stripColor(q.display.title);
        }

        if (q.name != null && !q.name.isBlank()) {
            return ChatColor.stripColor(q.name);
        }

        return q.id;
    }

    private List<String> loreOf(QuestDef q) {
        if (q == null || q.display == null) return Collections.emptyList();
        return q.display.description == null ? Collections.emptyList() : q.display.description;
    }

    private Material iconOf(QuestDef q) {
        if (q == null || q.display == null || q.display.icon == null)
            return Material.BOOK;

        try {
            return Material.valueOf(q.display.icon.toUpperCase(Locale.ROOT));
        } catch (Throwable ignored) {
            return Material.BOOK;
        }
    }
}
