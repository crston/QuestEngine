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

import java.lang.reflect.Field;
import java.util.*;
import java.util.stream.Collectors;

/**
 * PublicQuestMenu
 * - 다국어 메시지 기반 GUI
 * - config.yml에 정의된 CustomModelData / 버튼 활성화 / 사운드 지원
 * - 완전한 보호 (추출, 중복, 드래그 차단)
 */
public final class PublicQuestMenu implements Listener {

    private final QuestEnginePlugin plugin;

    public PublicQuestMenu(QuestEnginePlugin plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void open(Player p, int page) {
        if (p == null) return;

        String title = ChatColor.translateAlternateColorCodes('&',
                plugin.msg().get("gui.public.title").replace("%page%", String.valueOf(page + 1)));
        Inventory inv = Bukkit.createInventory(new GuiHolder("Q_PUBLIC"), 54, title);
        ((GuiHolder) inv.getHolder()).setInventory(inv);

        fill(inv);
        drawTopBar(p, inv);
        drawBottomBar(inv);
        drawQuests(p, inv, page);

        plugin.gui().putSession(p, "public_page", page);
        playSounds(p, "open");
        p.openInventory(inv);
    }

    private void drawTopBar(Player p, Inventory inv) {
        if (isButtonEnabled("back")) {
            inv.setItem(0, iconWithModel("back",
                    plugin.msg().get("gui.public.back"),
                    List.of(plugin.msg().get("gui.public.back_lore"))));
        }
        if (isButtonEnabled("search")) {
            inv.setItem(8, iconWithModel("search",
                    plugin.msg().get("gui.public.search"),
                    List.of(plugin.msg().get("gui.public.search_lore"))));
        }
    }

    private void drawBottomBar(Inventory inv) {
        if (isButtonEnabled("prev"))
            inv.setItem(45, iconWithModel("prev",
                    plugin.msg().get("gui.public.prev"),
                    List.of(plugin.msg().get("gui.public.prev_lore"))));
        if (isButtonEnabled("next"))
            inv.setItem(53, iconWithModel("next",
                    plugin.msg().get("gui.public.next"),
                    List.of(plugin.msg().get("gui.public.next_lore"))));
    }

    private void drawQuests(Player p, Inventory inv, int page) {
        List<QuestDef> all = safeAll().stream()
                .filter(this::isPublic)
                .collect(Collectors.toList());

        Set<String> active = new HashSet<>(plugin.engine().progress().activeQuestIds(p.getUniqueId(), p.getName()));
        all = all.stream().filter(q -> !active.contains(idOf(q))).collect(Collectors.toList());

        String search = getSearch(p);
        if (search != null && !search.isBlank()) {
            String needle = ChatColor.stripColor(search).toLowerCase(Locale.ROOT);
            all = all.stream()
                    .filter(d -> ChatColor.stripColor(displayNameOf(d)).toLowerCase(Locale.ROOT).contains(needle))
                    .collect(Collectors.toList());
        }

        all.sort(Comparator.comparing(this::displayNameOf, Comparator.nullsLast(String::compareToIgnoreCase)));

        int start = page * 28;
        int end = Math.min(all.size(), start + 28);
        int[] slots = gridSlots();

        for (int s : slots) inv.setItem(s, null);

        String rewardLabel = plugin.msg().get("gui.public.reward_label", "&eReward: &f");
        String leftClick = plugin.msg().get("gui.public.left_click_start", "&aLeft-click: Start quest");

        int idx = 0;
        for (int i = start; i < end && idx < slots.length; i++) {
            QuestDef qd = all.get(i);
            List<String> lore = new ArrayList<>();

            for (String desc : descriptionOf(qd))
                lore.add("&7" + ChatColor.stripColor(desc));

            String reward = rewardOf(qd);
            if (reward != null && !reward.isBlank()) {
                lore.add(" ");
                lore.add(rewardLabel + ChatColor.stripColor(reward));
            }

            lore.add(" ");
            lore.add(leftClick);

            inv.setItem(slots[idx++], icon(iconOf(qd),
                    "&f" + displayNameOf(qd),
                    lore,
                    getModelOf(qd)));
        }
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof GuiHolder gh)) return;
        if (!"Q_PUBLIC".equals(gh.id())) return;

        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;
        int slot = e.getRawSlot();
        int page = plugin.gui().getSession(p, "public_page") instanceof Integer i ? i : 0;

        if (slot == 0 && isButtonEnabled("back")) {
            playSounds(p, "click");
            Bukkit.getScheduler().runTaskLater(plugin, () -> plugin.gui().openList(p), 1L);
            return;
        }
        if (slot == 8 && isButtonEnabled("search")) {
            p.closeInventory();
            ChatInput.await(p, plugin.msg().get("gui.public.search_prompt"), (pp, text) -> {
                String input = text == null ? "" : text.trim();
                setSearch(pp, input);
                Bukkit.getScheduler().runTask(plugin, () -> open(pp, 0));
            });
            playSounds(p, "click");
            return;
        }
        if (slot == 45 && isButtonEnabled("prev")) {
            int newPage = Math.max(0, page - 1);
            playSounds(p, "page");
            Bukkit.getScheduler().runTaskLater(plugin, () -> open(p, newPage), 1L);
            return;
        }
        if (slot == 53 && isButtonEnabled("next")) {
            int newPage = page + 1;
            playSounds(p, "page");
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
                p.sendMessage(plugin.msg().pref("gui.public.accepted")
                        .replace("%quest%", displayNameOf(d)));
                playSounds(p, "success");
                Bukkit.getScheduler().runTaskLater(plugin, () -> open(p, page), 2L);
            } catch (Throwable t) {
                p.sendMessage(plugin.msg().pref("gui.public.error_accept"));
                playSounds(p, "error");
            }
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (!(e.getInventory().getHolder() instanceof GuiHolder gh)) return;
        if (!"Q_PUBLIC".equals(gh.id())) return;
        for (int slot : e.getRawSlots()) {
            if (slot < e.getInventory().getSize()) {
                e.setCancelled(true);
                return;
            }
        }
    }

    // ==============================================================
    // Utilities
    // ==============================================================

    private ItemStack iconWithModel(String key, String name, List<String> lore) {
        String path = "gui.public.icons." + key;
        String matName = plugin.getConfig().getString(path + ".material", "BOOK");
        int model = plugin.getConfig().getInt(path + ".model", -1);
        Material mat = Material.matchMaterial(matName);
        if (mat == null) mat = Material.BOOK;
        return icon(mat, name, lore, model);
    }

    private ItemStack icon(Material m, String name, List<String> lore, int model) {
        ItemStack it = new ItemStack(m == null ? Material.BOOK : m);
        ItemMeta im = it.getItemMeta();
        if (name != null) im.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));
        if (lore != null && !lore.isEmpty()) {
            List<String> colored = lore.stream()
                    .map(s -> ChatColor.translateAlternateColorCodes('&', s))
                    .collect(Collectors.toList());
            im.setLore(colored);
        }
        if (model > 0) im.setCustomModelData(model);
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
                // 리소스팩 커스텀 사운드
                p.playSound(p.getLocation(), s, 1f, 1f);
            }
        }
    }

    private boolean isButtonEnabled(String key) {
        return plugin.getConfig().getBoolean("gui.public.buttons." + key, true);
    }

    private int getModelOf(QuestDef q) {
        try {
            Field f = q.getClass().getDeclaredField("display");
            f.setAccessible(true);
            Object disp = f.get(q);
            if (disp != null) {
                Field mf = disp.getClass().getDeclaredField("customModelData");
                mf.setAccessible(true);
                Object v = mf.get(disp);
                if (v instanceof Number n) return n.intValue();
            }
        } catch (Throwable ignored) {}
        return -1;
    }

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
            if (all instanceof Collection<?> col) {
                List<QuestDef> out = new ArrayList<>();
                for (Object o : col) if (o instanceof QuestDef q) out.add(q);
                return out;
            }
        } catch (Throwable ignored) {}
        return Collections.emptyList();
    }

    private QuestDef findByDisplayName(String display) {
        for (QuestDef q : safeAll()) {
            if (display.equalsIgnoreCase(ChatColor.stripColor(displayNameOf(q)))) return q;
        }
        return null;
    }

    private String idOf(QuestDef q) {
        try {
            Field f = q.getClass().getDeclaredField("id");
            f.setAccessible(true);
            Object v = f.get(q);
            if (v != null) return v.toString();
        } catch (Throwable ignored) {}
        return "unknown";
    }

    private String displayNameOf(QuestDef q) {
        try {
            Field f = q.getClass().getDeclaredField("name");
            f.setAccessible(true);
            Object v = f.get(q);
            if (v != null) return ChatColor.translateAlternateColorCodes('&', v.toString());
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
                if (v != null && !String.valueOf(v).isBlank())
                    return ChatColor.translateAlternateColorCodes('&', String.valueOf(v));
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
