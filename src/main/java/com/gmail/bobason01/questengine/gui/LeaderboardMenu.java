package com.gmail.bobason01.questengine.gui;

import com.gmail.bobason01.questengine.QuestEnginePlugin;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

/**
 * LeaderboardMenu
 * Quest points leaderboard GUI
 * Async skin fetch with cache
 * Fully protected clicks and drags
 */
public final class LeaderboardMenu implements Listener {

    private final QuestEnginePlugin plugin;
    private final ExecutorService asyncPool = Executors.newFixedThreadPool(3);
    private static final Map<UUID, String> TEXTURE_CACHE = new ConcurrentHashMap<>();

    public LeaderboardMenu(QuestEnginePlugin plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void open(Player p) {
        String title = plugin.msg().get("gui.leaderboard.title");
        Inventory inv = Bukkit.createInventory(new GuiHolder("Q_LEADERBOARD"), 54, title);
        ((GuiHolder) inv.getHolder()).setInventory(inv);

        fill(inv);
        drawTopBar(inv);
        drawTopPlayersAsync(inv);

        plugin.gui().sound(p, "open");
        p.openInventory(inv);
    }

    private void drawTopBar(Inventory inv) {
        inv.setItem(0, icon(Material.ARROW,
                plugin.msg().get("gui.leaderboard.back"),
                List.of(plugin.msg().get("gui.leaderboard.back_lore"))));
    }

    private void drawTopPlayersAsync(Inventory inv) {
        CompletableFuture.runAsync(() -> {
            List<Map.Entry<UUID, Integer>> top = plugin.engine().progress().top(36);
            int[] slots = gridSlots();

            for (int i = 0; i < top.size() && i < slots.length; i++) {
                Map.Entry<UUID, Integer> e = top.get(i);
                UUID uuid = e.getKey();
                int rank = i + 1;
                int points = e.getValue();
                int slot = slots[i];

                ItemStack head = buildPlayerHead(uuid, rank, points);
                Bukkit.getScheduler().runTask(plugin, () -> inv.setItem(slot, head));

                if (Bukkit.getOnlineMode() && !TEXTURE_CACHE.containsKey(uuid)) {
                    CompletableFuture.runAsync(() -> {
                        String value = fetchTextureValue(uuid);
                        if (value != null && !value.isEmpty()) {
                            TEXTURE_CACHE.put(uuid, value);
                            Bukkit.getScheduler().runTask(plugin, () -> {
                                try {
                                    ItemStack updated = buildPlayerHead(uuid, rank, points);
                                    inv.setItem(slot, updated);
                                } catch (Throwable ignored) {}
                            });
                        }
                    }, asyncPool);
                }
            }
        }, asyncPool);
    }

    private ItemStack buildPlayerHead(UUID uuid, int rank, int points) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();

        OfflinePlayer offline = Bukkit.getOfflinePlayer(uuid);
        String name = (offline != null && offline.getName() != null) ? offline.getName() : "Unknown";

        String display = plugin.msg().get("gui.leaderboard.rank_format")
                .replace("%rank%", String.valueOf(rank))
                .replace("%player%", name)
                .replace("%points%", String.valueOf(points));
        List<String> lore = List.of(
                plugin.msg().get("gui.leaderboard.rank_points")
                        .replace("%points%", String.valueOf(points))
        );

        meta.setDisplayName(display);
        meta.setLore(lore);

        String cached = TEXTURE_CACHE.get(uuid);
        if (cached != null) {
            applyTexture(meta, cached);
        } else {
            meta.setOwningPlayer(offline);
        }

        head.setItemMeta(meta);
        return head;
    }

    private void applyTexture(SkullMeta meta, String textureValue) {
        try {
            GameProfile profile = new GameProfile(UUID.randomUUID(), null);
            profile.getProperties().put("textures", new Property("textures", textureValue));
            Field profileField = meta.getClass().getDeclaredField("profile");
            profileField.setAccessible(true);
            profileField.set(meta, profile);
        } catch (Exception ignored) {}
    }

    private String fetchTextureValue(UUID uuid) {
        try (InputStream in = new URL("https://sessionserver.mojang.com/session/minecraft/profile/" + uuid.toString().replace("-", "")).openStream()) {
            String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            int start = json.indexOf("\"value\":\"") + 9;
            if (start < 9) return null;
            int end = json.indexOf('"', start);
            return json.substring(start, end);
        } catch (Exception e) {
            return null;
        }
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof GuiHolder gh)) return;
        if (!"Q_LEADERBOARD".equals(gh.id())) return;

        e.setCancelled(true);
        if (e.getClickedInventory() == null) return;
        if (e.getClickedInventory().getType() != org.bukkit.event.inventory.InventoryType.CHEST) {
            e.setCancelled(true);
            return;
        }
        switch (e.getAction()) {
            case MOVE_TO_OTHER_INVENTORY, HOTBAR_MOVE_AND_READD, HOTBAR_SWAP, COLLECT_TO_CURSOR,
                 DROP_ONE_CURSOR, DROP_ALL_CURSOR, DROP_ONE_SLOT, DROP_ALL_SLOT,
                 PLACE_ALL, PLACE_SOME, PLACE_ONE, PICKUP_ALL, PICKUP_HALF,
                 PICKUP_SOME, PICKUP_ONE -> e.setCancelled(true);
            default -> {}
        }

        if (!(e.getWhoClicked() instanceof Player p)) return;
        int slot = e.getRawSlot();
        if (slot == 0) {
            plugin.gui().sound(p, "click");
            Bukkit.getScheduler().runTaskLater(plugin, () -> plugin.gui().openList(p), 1L);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (!(e.getInventory().getHolder() instanceof GuiHolder gh)) return;
        if (!"Q_LEADERBOARD".equals(gh.id())) return;
        for (int slot : e.getRawSlots()) {
            if (slot < e.getInventory().getSize()) {
                e.setCancelled(true);
                return;
            }
        }
    }

    private void fill(Inventory inv) {
        ItemStack f = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta im = f.getItemMeta();
        im.setDisplayName(" ");
        f.setItemMeta(im);
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, f);
    }

    private ItemStack icon(Material m, String name, List<String> lore) {
        ItemStack it = new ItemStack(m == null ? Material.BOOK : m);
        ItemMeta im = it.getItemMeta();
        im.setDisplayName(name);
        if (lore != null) im.setLore(lore);
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
}
