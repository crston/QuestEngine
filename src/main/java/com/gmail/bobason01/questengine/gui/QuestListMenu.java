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

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class QuestListMenu implements Listener {

    private final QuestEnginePlugin plugin;
    private final Pattern pagePattern = Pattern.compile("\\b(\\d+)\\b");

    public QuestListMenu(QuestEnginePlugin plugin) {
        this.plugin = plugin;
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
        plugin.gui().sound(p, "open");
        p.openInventory(inv);
    }

    private void drawTopBar(Player p, Inventory inv) {
        String order = plugin.msg().get(getAsc(p) ? "gui.list.order_asc" : "gui.list.order_desc");
        inv.setItem(0, icon(Material.OAK_SIGN,
                plugin.msg().get("gui.list.search"),
                List.of(plugin.msg().get("gui.list.search_lore"))));
        inv.setItem(1, icon(Material.PLAYER_HEAD,
                plugin.msg().get("gui.list.leaderboard"),
                List.of(plugin.msg().get("gui.list.leaderboard_lore"))));
        inv.setItem(2, icon(Material.BOOK,
                plugin.msg().get("gui.list.public"),
                List.of(plugin.msg().get("gui.list.public_lore"))));
        inv.setItem(8, icon(Material.COMPARATOR,
                plugin.msg().get("gui.list.sort").replace("%order%", order),
                List.of(plugin.msg().get("gui.list.sort_lore"))));
    }

    private void drawBottomBar(Inventory inv) {
        inv.setItem(45, icon(Material.ARROW,
                plugin.msg().get("gui.list.prev"), null));
        inv.setItem(49, icon(Material.NAME_TAG,
                plugin.msg().get("gui.list.page_input"),
                List.of(plugin.msg().get("gui.list.page_input_lore"))));
        inv.setItem(53, icon(Material.ARROW,
                plugin.msg().get("gui.list.next"), null));
    }

    private void drawQuests(Player p, Inventory inv, int page) {
        List<String> activeIds = plugin.engine().progress().activeQuestIds(p.getUniqueId(), p.getName());
        List<QuestDef> all = activeIds.stream()
                .map(id -> plugin.engine().quests().get(id))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        String q = getSearch(p);
        if (q != null && !q.trim().isEmpty()) {
            String needle = ChatColor.stripColor(q).toLowerCase(Locale.ROOT);
            all = all.stream().filter(d -> {
                String name = ChatColor.stripColor(displayNameOf(d));
                boolean nameHit = name != null && name.toLowerCase(Locale.ROOT).contains(needle);
                boolean loreHit = loreOf(d).stream().anyMatch(l ->
                        ChatColor.stripColor(l).toLowerCase(Locale.ROOT).contains(needle));
                return nameHit || loreHit;
            }).collect(Collectors.toList());
        }

        all.sort(Comparator.comparing(this::displayNameOf, Comparator.nullsLast(String::compareToIgnoreCase)));
        if (!getAsc(p)) Collections.reverse(all);

        int start = page * 28;
        int end = Math.min(all.size(), start + 28);
        int[] slots = gridSlots();

        for (int s : slots) inv.setItem(s, null);

        int idx = 0;
        for (int i = start; i < end && idx < slots.length; i++) {
            QuestDef d = all.get(i);
            List<String> lore = new ArrayList<>();

            List<String> desc = descriptionOf(d);
            if (!desc.isEmpty()) {
                for (String line : desc) {
                    lore.add(ChatColor.translateAlternateColorCodes('&', "&7" + ChatColor.stripColor(line)));
                }
                lore.add(" ");
            }

            int value = plugin.engine().progress().value(p.getUniqueId(), p.getName(), d.id);
            int target = d.amount;
            lore.add(ChatColor.translateAlternateColorCodes('&', "&aProgress: &f" + value + "/" + target));

            String reward = rewardOf(d);
            if (reward != null && !reward.isBlank()) {
                lore.add(ChatColor.translateAlternateColorCodes('&',
                        "&eReward: &f" + ChatColor.stripColor(reward)));
            }

            lore.add(" ");
            lore.add(plugin.msg().get("gui.list.left_click_complete"));
            lore.add(plugin.msg().get("gui.list.right_click_cancel"));

            inv.setItem(slots[idx++],
                    icon(iconOf(d),
                            ChatColor.translateAlternateColorCodes('&', "&f" + displayNameOf(d)),
                            lore));
        }
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof GuiHolder gh)) return;
        if (!"Q_LIST".equals(gh.id())) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;

        Inventory inv = e.getInventory();
        int slot = e.getRawSlot();
        int page = getCurrentPage(p, e);

        if (slot == 1) {
            plugin.gui().sound(p, "click");
            Bukkit.getScheduler().runTaskLater(plugin, () -> plugin.gui().openLeaderboard(p), 1L);
            return;
        }

        if (slot == 2) {
            plugin.gui().sound(p, "click");
            Bukkit.getScheduler().runTaskLater(plugin, () -> plugin.gui().openPublic(p), 1L);
            return;
        }

        if (slot == 0) {
            p.closeInventory();
            ChatInput.get().await(p, plugin.msg().get("gui.list.search_prompt"), (pp, text) -> {
                setSearch(pp, text == null ? "" : text.trim());
                Bukkit.getScheduler().runTask(plugin, () -> open(pp, 0));
            });
            return;
        }

        if (slot == 8) {
            boolean next = !getAsc(p);
            plugin.gui().putSession(p, "list_sort_asc", next);
            String order = plugin.msg().get(next ? "gui.list.order_asc" : "gui.list.order_desc");
            inv.setItem(8, icon(Material.COMPARATOR,
                    plugin.msg().get("gui.list.sort").replace("%order%", order),
                    List.of(plugin.msg().get("gui.list.sort_lore"))));
            drawQuests(p, inv, page);
            plugin.gui().sound(p, "page");
            return;
        }

        if (slot == 45) {
            int newPage = Math.max(0, page - 1);
            plugin.gui().sound(p, "page");
            Bukkit.getScheduler().runTaskLater(plugin, () -> open(p, newPage), 1L);
            return;
        }

        if (slot == 53) {
            int newPage = page + 1;
            plugin.gui().sound(p, "page");
            Bukkit.getScheduler().runTaskLater(plugin, () -> open(p, newPage), 1L);
            return;
        }

        if (slot == 49) {
            p.closeInventory();
            ChatInput.get().await(p, plugin.msg().get("gui.list.page_input_prompt"), (pp, text) -> {
                int to = 0;
                try {
                    to = Math.max(0, Integer.parseInt(text.trim()) - 1);
                } catch (Exception ignored) {}
                final int dest = to;
                Bukkit.getScheduler().runTaskLater(plugin, () -> open(pp, dest), 1L);
            });
            return;
        }

        ItemStack clicked = e.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;
        String name = ChatColor.stripColor(clicked.getItemMeta().getDisplayName());
        QuestDef d = findByDisplayName(name, p);
        if (d == null) return;

        if (e.getClick().isLeftClick()) {
            tryComplete(p, d);
            plugin.gui().sound(p, "click");
            Bukkit.getScheduler().runTaskLater(plugin, () -> open(p, page), 2L);
        }

        if (e.getClick().isRightClick()) {
            openConfirmCancel(p, d, page);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (!(e.getInventory().getHolder() instanceof GuiHolder gh)) return;
        if (!"Q_LIST".equals(gh.id())) return;
        e.setCancelled(true);
    }

    private void openConfirmCancel(Player p, QuestDef quest, int backPage) {
        String title = ChatColor.translateAlternateColorCodes('&',
                plugin.msg().get("gui.confirm.title").replace("%quest%", displayNameOf(quest)));
        Inventory inv = Bukkit.createInventory(new GuiHolder("Q_CONFIRM"), 27, title);
        ((GuiHolder) inv.getHolder()).setInventory(inv);

        inv.setItem(11, icon(Material.LIME_WOOL, "§aYES", null));
        inv.setItem(15, icon(Material.RED_WOOL, "§cNO", null));

        plugin.gui().sound(p, "open");
        p.openInventory(inv);

        plugin.gui().putSession(p, "confirm_target", quest);
        plugin.gui().putSession(p, "confirm_back_page", backPage);
    }

    @EventHandler
    public void onConfirmClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof GuiHolder gh)) return;
        if (!"Q_CONFIRM".equals(gh.id())) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;

        QuestDef quest = (QuestDef) plugin.gui().getSession(p, "confirm_target");
        Object obj = plugin.gui().getSession(p, "confirm_back_page");
        int backPage = obj instanceof Integer i ? i : 0;
        if (quest == null) return;

        if (e.getRawSlot() == 11) {
            plugin.engine().cancelQuest(p, quest.id);
            p.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    "&eQuest &f" + displayNameOf(quest) + " &ehas been cancelled."));
            plugin.gui().sound(p, "cancel");
            Bukkit.getScheduler().runTaskLater(plugin, () -> open(p, backPage), 1L);
        }

        if (e.getRawSlot() == 15) {
            plugin.gui().sound(p, "click");
            Bukkit.getScheduler().runTaskLater(plugin, () -> open(p, backPage), 1L);
        }
    }

    private List<String> descriptionOf(QuestDef q) {
        try {
            Field f = q.getClass().getDeclaredField("display");
            f.setAccessible(true);
            Object disp = f.get(q);
            if (disp != null) {
                Field df = disp.getClass().getDeclaredField("description");
                df.setAccessible(true);
                Object v = df.get(disp);
                if (v instanceof List<?> l) {
                    return l.stream().map(String::valueOf).collect(Collectors.toList());
                }
            }
        } catch (Throwable ignored) {}
        return Collections.emptyList();
    }

    private String rewardOf(QuestDef q) {
        try {
            Field f = q.getClass().getDeclaredField("display");
            f.setAccessible(true);
            Object disp = f.get(q);
            if (disp != null) {
                Field df = disp.getClass().getDeclaredField("reward");
                df.setAccessible(true);
                Object v = df.get(disp);
                if (v != null) return String.valueOf(v);
            }
        } catch (Throwable ignored) {}
        return "";
    }

    private String displayNameOf(QuestDef q) {
        try {
            Field f = q.getClass().getDeclaredField("name");
            f.setAccessible(true);
            Object v = f.get(q);
            if (v != null) return String.valueOf(v);
        } catch (Throwable ignored) {}
        try {
            Field f = q.getClass().getDeclaredField("id");
            f.setAccessible(true);
            Object v = f.get(q);
            if (v != null) return String.valueOf(v);
        } catch (Throwable ignored) {}
        return "unknown";
    }

    private List<String> loreOf(QuestDef q) {
        try {
            Field f = q.getClass().getDeclaredField("display");
            f.setAccessible(true);
            Object disp = f.get(q);
            if (disp != null) {
                Field df = disp.getClass().getDeclaredField("description");
                df.setAccessible(true);
                Object v = df.get(disp);
                if (v instanceof List<?> l) {
                    return l.stream().map(String::valueOf).collect(Collectors.toList());
                }
            }
        } catch (Throwable ignored) {}
        return Collections.emptyList();
    }

    private Material iconOf(QuestDef q) {
        try {
            Field f = q.getClass().getDeclaredField("display");
            f.setAccessible(true);
            Object disp = f.get(q);
            if (disp != null) {
                Field df = disp.getClass().getDeclaredField("icon");
                df.setAccessible(true);
                Object v = df.get(disp);
                if (v != null) return Material.matchMaterial(String.valueOf(v));
            }
        } catch (Throwable ignored) {}
        return Material.BOOK;
    }

    private ItemStack icon(Material m, String name, List<String> lore) {
        ItemStack it = new ItemStack(m == null ? Material.BOOK : m);
        ItemMeta im = it.getItemMeta();
        if (name != null) im.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));
        if (lore != null) {
            List<String> colored = new ArrayList<>();
            for (String l : lore) colored.add(ChatColor.translateAlternateColorCodes('&', l));
            im.setLore(colored);
        }
        it.setItemMeta(im);
        return it;
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
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, f);
    }

    private boolean getAsc(Player p) {
        Object v = plugin.gui().getSession(p, "list_sort_asc");
        return (v instanceof Boolean b) ? b : true;
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
            } catch (NumberFormatException ignored) {}
        }
        return 0;
    }

    private String idOf(QuestDef q) {
        try {
            Field f = q.getClass().getDeclaredField("id");
            f.setAccessible(true);
            Object v = f.get(q);
            if (v != null) return String.valueOf(v);
        } catch (Throwable ignored) {}
        return displayNameOf(q).toLowerCase(Locale.ROOT).replace(' ', '_');
    }

    private void tryComplete(Player p, QuestDef q) {
        try {
            Method can = plugin.engine().getClass().getMethod("canCompleteQuest", Player.class, String.class);
            boolean ok = (boolean) can.invoke(plugin.engine(), p, idOf(q));
            if (ok) {
                Method complete = plugin.engine().getClass().getMethod("completeQuest", Player.class, String.class);
                complete.invoke(plugin.engine(), p, idOf(q));
                p.sendMessage(plugin.msg().pref("gui.list.quest_completed")
                        .replace("%quest%", displayNameOf(q)));
                plugin.gui().sound(p, "success");
                return;
            }
            p.sendMessage(plugin.msg().pref("gui.list.quest_cannot_complete"));
        } catch (NoSuchMethodException ns) {
            p.sendMessage(plugin.msg().pref("gui.list.method_missing"));
        } catch (Throwable t) {
            p.sendMessage(plugin.msg().pref("gui.list.quest_complete_error"));
        }
    }
    private QuestDef findByDisplayName(String display, Player p) {
        for (String id : plugin.engine().progress().activeQuestIds(p.getUniqueId(), p.getName())) {
            QuestDef q = plugin.engine().quests().get(id);
            if (q == null) continue;
            String name = ChatColor.stripColor(displayNameOf(q));
            if (display.equalsIgnoreCase(name)) {
                return q;
            }
        }
        return null;
    }
}
