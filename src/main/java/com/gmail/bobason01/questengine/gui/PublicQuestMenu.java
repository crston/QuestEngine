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
import java.util.stream.Collectors;

/**
 * PublicQuestMenu
 * - 공개 퀘스트 목록 GUI
 * - display.title, description, reward 완전 표시
 * - 색상 코드(&) 지원
 */
public final class PublicQuestMenu implements Listener {

    private final QuestEnginePlugin plugin;

    public PublicQuestMenu(QuestEnginePlugin plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void open(Player p, int page) {
        String title = ChatColor.translateAlternateColorCodes('&',
                plugin.msg().get("gui.public.title").replace("%page%", String.valueOf(page + 1)));
        Inventory inv = Bukkit.createInventory(new GuiHolder("Q_PUBLIC"), 54, title);
        ((GuiHolder) inv.getHolder()).setInventory(inv);

        fill(inv);
        drawTopBar(p, inv);
        drawBottomBar(inv);
        drawQuests(p, inv, page);

        plugin.gui().putSession(p, "public_page", page);
        plugin.gui().sound(p, "open");
        p.openInventory(inv);
    }

    private void drawTopBar(Player p, Inventory inv) {
        inv.setItem(0, icon(Material.ARROW,
                plugin.msg().get("gui.public.back"),
                List.of(plugin.msg().get("gui.public.back_lore"))));

        inv.setItem(8, icon(Material.OAK_SIGN,
                plugin.msg().get("gui.public.search"),
                List.of(plugin.msg().get("gui.public.search_lore"))));
    }

    private void drawBottomBar(Inventory inv) {
        inv.setItem(45, icon(Material.ARROW,
                plugin.msg().get("gui.public.prev"),
                List.of(plugin.msg().get("gui.public.prev_lore"))));
        inv.setItem(53, icon(Material.ARROW,
                plugin.msg().get("gui.public.next"),
                List.of(plugin.msg().get("gui.public.next_lore"))));
    }

    // ==============================================================
    // 퀘스트 표시
    // ==============================================================
    private void drawQuests(Player p, Inventory inv, int page) {
        List<QuestDef> all = safeAll().stream()
                .filter(this::isPublic)
                .collect(Collectors.toList());

        Set<String> active = new HashSet<>(plugin.engine().progress().activeQuestIds(p.getUniqueId(), p.getName()));
        all = all.stream()
                .filter(q -> !active.contains(idOf(q)))
                .collect(Collectors.toList());

        String q = getSearch(p);
        if (q != null && !q.isBlank()) {
            String needle = q.toLowerCase(Locale.ROOT);
            all = all.stream().filter(d -> {
                String name = ChatColor.stripColor(displayNameOf(d)).toLowerCase(Locale.ROOT);
                return name.contains(needle);
            }).collect(Collectors.toList());
        }

        all.sort(Comparator.comparing(this::displayNameOf, Comparator.nullsLast(String::compareToIgnoreCase)));

        int start = page * 28;
        int end = Math.min(all.size(), start + 28);
        int[] slots = gridSlots();

        for (int s : slots) inv.setItem(s, null);

        int idx = 0;
        for (int i = start; i < end && idx < slots.length; i++) {
            QuestDef qd = all.get(i);
            List<String> lore = new ArrayList<>();

            // ── 설명
            for (String desc : descriptionOf(qd)) {
                lore.add("&7" + ChatColor.stripColor(desc));
            }

            // ── 설명과 보상 사이 한 줄 띄우기
            lore.add(" ");

            // ── 보상
            String reward = rewardOf(qd);
            if (reward != null && !reward.isBlank()) {
                lore.add("&e보상: &f" + ChatColor.stripColor(reward));
            }

            // ── 구분선
            lore.add(" ");
            lore.add("&a좌클릭: &7수락");
            lore.add("&c우클릭: &7취소");

            inv.setItem(slots[idx++], icon(iconOf(qd), "&f" + displayNameOf(qd), lore));
        }
    }

    // ==============================================================
    // 클릭 처리
    // ==============================================================
    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof GuiHolder gh)) return;
        if (!"Q_PUBLIC".equals(gh.id())) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;

        int slot = e.getRawSlot();
        Object obj = plugin.gui().getSession(p, "public_page");
        int page = obj instanceof Integer i ? i : 0;

        if (slot == 0) {
            plugin.gui().sound(p, "click");
            Bukkit.getScheduler().runTaskLater(plugin, () -> plugin.gui().openList(p), 1L);
            return;
        }

        if (slot == 8) {
            p.closeInventory();
            ChatInput.get().await(p, plugin.msg().get("gui.public.search_prompt"), (pp, text) -> {
                setSearch(pp, text == null ? "" : text.trim());
                Bukkit.getScheduler().runTask(plugin, () -> open(pp, 0));
            });
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

        ItemStack clicked = e.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;
        String name = ChatColor.stripColor(clicked.getItemMeta().getDisplayName());
        QuestDef d = findByDisplayName(name);
        if (d == null) return;

        if (e.getClick().isLeftClick()) {
            try {
                plugin.engine().startQuest(p, d);
                p.sendMessage(plugin.msg().pref("gui.public.accepted").replace("%quest%", displayNameOf(d)));
                plugin.gui().sound(p, "success");
                Bukkit.getScheduler().runTaskLater(plugin, () -> open(p, page), 2L);
            } catch (Throwable t) {
                p.sendMessage(plugin.msg().pref("gui.public.error_accept"));
            }
            return;
        }

        if (e.getClick().isRightClick()) {
            plugin.engine().cancelQuest(p, d);
            plugin.gui().sound(p, "cancel");
            p.sendMessage(plugin.msg().pref("gui.public.cancelled").replace("%quest%", displayNameOf(d)));
            Bukkit.getScheduler().runTaskLater(plugin, () -> open(p, page), 2L);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (!(e.getInventory().getHolder() instanceof GuiHolder gh)) return;
        if (!"Q_PUBLIC".equals(gh.id())) return;
        e.setCancelled(true);
    }

    // ==============================================================
    // 데이터 헬퍼
    // ==============================================================
    private String getSearch(Player p) {
        Object v = plugin.gui().getSession(p, "public_search");
        return v == null ? "" : v.toString();
    }

    private void setSearch(Player p, String q) {
        plugin.gui().putSession(p, "public_search", q == null ? "" : q);
    }

    private Collection<QuestDef> safeAll() {
        try {
            Object all = plugin.engine().quests().all();
            if (all instanceof Map<?, ?> map) {
                List<QuestDef> out = new ArrayList<>();
                for (Object v : map.values()) if (v instanceof QuestDef q) out.add(q);
                return out;
            }
            if (all instanceof Collection<?> col) {
                List<QuestDef> out = new ArrayList<>();
                for (Object v : col) if (v instanceof QuestDef q) out.add(q);
                return out;
            }
        } catch (Throwable ignored) {}
        try {
            Collection<String> ids = plugin.engine().quests().ids();
            List<QuestDef> out = new ArrayList<>(ids.size());
            for (String id : ids) {
                QuestDef q = plugin.engine().quests().get(id);
                if (q != null) out.add(q);
            }
            return out;
        } catch (Throwable t) {
            return Collections.emptyList();
        }
    }

    private QuestDef findByDisplayName(String display) {
        for (QuestDef q : safeAll()) {
            if (display.equalsIgnoreCase(ChatColor.stripColor(displayNameOf(q)))) return q;
        }
        return null;
    }

    private String idOf(QuestDef q) {
        try {
            Method m = q.getClass().getMethod("getId");
            Object v = m.invoke(q);
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

    private String displayNameOf(QuestDef q) {
        try {
            Field f = q.getClass().getDeclaredField("display");
            f.setAccessible(true);
            Object disp = f.get(q);
            if (disp != null) {
                Field tf = disp.getClass().getDeclaredField("title");
                tf.setAccessible(true);
                Object v = tf.get(disp);
                if (v != null && !String.valueOf(v).isBlank()) {
                    return ChatColor.translateAlternateColorCodes('&', String.valueOf(v));
                }
            }
        } catch (Throwable ignored) {}

        try {
            Field f = q.getClass().getDeclaredField("name");
            f.setAccessible(true);
            Object v = f.get(q);
            if (v != null && !String.valueOf(v).isBlank()) {
                return ChatColor.translateAlternateColorCodes('&', String.valueOf(v));
            }
        } catch (Throwable ignored) {}

        try {
            Field f = q.getClass().getDeclaredField("id");
            f.setAccessible(true);
            Object v = f.get(q);
            if (v != null && !String.valueOf(v).isBlank()) {
                return ChatColor.translateAlternateColorCodes('&', String.valueOf(v));
            }
        } catch (Throwable ignored) {}

        return "unknown";
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
                    List<String> out = new ArrayList<>();
                    for (Object o : l) out.add(String.valueOf(o));
                    return out;
                }
            }
        } catch (Throwable ignored) {}
        return List.of();
    }

    private String rewardOf(QuestDef q) {
        try {
            Field f = q.getClass().getDeclaredField("display");
            f.setAccessible(true);
            Object disp = f.get(q);
            if (disp != null) {
                Field rf = disp.getClass().getDeclaredField("reward");
                rf.setAccessible(true);
                Object v = rf.get(disp);
                if (v != null && !String.valueOf(v).isBlank()) {
                    return ChatColor.translateAlternateColorCodes('&', String.valueOf(v));
                }
            }
        } catch (Throwable ignored) {}
        return "";
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

    private boolean isPublic(QuestDef q) {
        try {
            Field f = q.getClass().getDeclaredField("isPublic");
            f.setAccessible(true);
            Object v = f.get(q);
            if (v instanceof Boolean b) return b;
        } catch (Throwable ignored) {}
        return false;
    }

    private ItemStack icon(Material m, String name, List<String> lore) {
        ItemStack it = new ItemStack(m == null ? Material.BOOK : m);
        ItemMeta im = it.getItemMeta();

        if (name != null) {
            im.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));
        }

        if (lore != null) {
            List<String> coloredLore = new ArrayList<>(lore.size());
            for (String line : lore) {
                coloredLore.add(ChatColor.translateAlternateColorCodes('&', line));
            }
            im.setLore(coloredLore);
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
}
