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
 * - 퀘스트 포인트 리더보드 GUI
 * - Mojang 스킨 자동 적용 (비동기)
 * - messages.yml 기반 다국어 지원
 * - TPS 안정성 보장
 */
public final class LeaderboardMenu implements Listener {

    private final QuestEnginePlugin plugin;
    private final ExecutorService asyncPool = Executors.newFixedThreadPool(3);
    private static final Map<UUID, String> TEXTURE_CACHE = new ConcurrentHashMap<>();

    public LeaderboardMenu(QuestEnginePlugin plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    // =============================================================
    // GUI 열기
    // =============================================================
    public void open(Player p) {
        String title = plugin.msg().get("gui.leaderboard.title");
        Inventory inv = Bukkit.createInventory(new GuiHolder("Q_LEADERBOARD"), 54, title);
        ((GuiHolder) inv.getHolder()).setInventory(inv);

        fill(inv);
        drawTopBar(p, inv);
        drawTopPlayersAsync(inv);

        plugin.gui().sound(p, "open");
        p.openInventory(inv);
    }

    // =============================================================
    // GUI 구성
    // =============================================================
    private void drawTopBar(Player p, Inventory inv) {
        inv.setItem(0, icon(Material.ARROW,
                plugin.msg().get("gui.leaderboard.back"),
                List.of(plugin.msg().get("gui.leaderboard.back_lore"))));
    }

    private void drawTopPlayersAsync(Inventory inv) {
        CompletableFuture.runAsync(() -> {
            List<Map.Entry<UUID, Integer>> top = plugin.engine().progress().top(36);
            int[] slots = {
                    10, 11, 12, 13, 14, 15, 16,
                    19, 20, 21, 22, 23, 24, 25,
                    28, 29, 30, 31, 32, 33, 34,
                    37, 38, 39, 40, 41, 42, 43
            };

            for (int i = 0; i < top.size() && i < slots.length; i++) {
                Map.Entry<UUID, Integer> e = top.get(i);
                UUID uuid = e.getKey();
                int rank = i + 1;
                int points = e.getValue();
                int slot = slots[i];

                ItemStack head = buildPlayerHead(uuid, rank, points);
                Bukkit.getScheduler().runTask(plugin, () -> inv.setItem(slot, head));
            }
        }, asyncPool);
    }

    // =============================================================
    // 스킨 및 머리 생성
    // =============================================================

    private ItemStack buildPlayerHead(UUID uuid, int rank, int points) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();

        OfflinePlayer offline = Bukkit.getOfflinePlayer(uuid);
        String name = (offline != null && offline.getName() != null) ? offline.getName() : "Unknown";

        // 표시 이름과 로어 먼저 설정
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

        // 기본 스킨
        meta.setOwningPlayer(offline);
        head.setItemMeta(meta);

        // 캐시 확인
        String cached = TEXTURE_CACHE.get(uuid);
        if (cached != null) {
            applyTexture(meta, cached);
            head.setItemMeta(meta);
            return head;
        }

        // 온라인 모드에서만 비동기 스킨 로드
        if (Bukkit.getOnlineMode()) {
            CompletableFuture.runAsync(() -> {
                try {
                    String value = fetchTextureValue(uuid);
                    if (value != null && !value.isEmpty()) {
                        TEXTURE_CACHE.put(uuid, value);
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            try {
                                ItemStack updated = new ItemStack(Material.PLAYER_HEAD);
                                SkullMeta sm = (SkullMeta) updated.getItemMeta();
                                sm.setDisplayName(display);
                                sm.setLore(lore);
                                applyTexture(sm, value);
                                sm.setOwningPlayer(offline);
                                updated.setItemMeta(sm);
                                // 이미 열린 인벤토리에 반영
                                for (Player viewer : Bukkit.getOnlinePlayers()) {
                                    Inventory top = viewer.getOpenInventory().getTopInventory();
                                    if (top != null && top.getHolder() instanceof GuiHolder gh
                                            && "Q_LEADERBOARD".equals(gh.id())) {
                                        top.addItem(updated);
                                    }
                                }
                            } catch (Throwable ignored) {}
                        });
                    }
                } catch (Throwable ignored) {}
            }, asyncPool);
        }

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

    // =============================================================
    // 이벤트 처리
    // =============================================================
    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof GuiHolder gh)) return;
        if (!"Q_LEADERBOARD".equals(gh.id())) return;
        e.setCancelled(true);
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
        e.setCancelled(true);
    }

    // =============================================================
    // 유틸
    // =============================================================
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
}
