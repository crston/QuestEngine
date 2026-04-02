package com.gmail.bobason01.questengine.gui;

import com.gmail.bobason01.questengine.QuestEnginePlugin;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
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

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class LeaderboardMenu implements Listener {

    private final QuestEnginePlugin plugin;
    private static final Map<UUID, String> TEXTURE_CACHE = new ConcurrentHashMap<>();
    private static Field profileField;

    static {
        try {
            Class<?> metaClass = Class.forName("org.bukkit.craftbukkit." + Bukkit.getServer().getClass().getPackage().getName().split("\\.")[3] + ".inventory.CraftMetaSkull");
            profileField = metaClass.getDeclaredField("profile");
            profileField.setAccessible(true);
        } catch (Throwable t) {
            profileField = null;
        }
    }

    private static final int[] SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };

    public LeaderboardMenu(QuestEnginePlugin plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void open(Player p) {
        if (p == null) return;

        String title = ChatColor.translateAlternateColorCodes('&', plugin.msg().get("gui.leaderboard.title"));
        GuiHolder holder = new GuiHolder("Q_LEADERBOARD");
        Inventory inv = Bukkit.createInventory(holder, 54, title);
        holder.setInventory(inv);

        fill(inv);

        drawTopBar(inv);
        drawTopPlayersAsync(inv);

        plugin.gui().sound(p, "open");
        p.openInventory(inv);
    }

    private void fill(Inventory inv) {
        ItemStack filler = createIcon(Material.GRAY_STAINED_GLASS_PANE, " ", -1);
        for (int i = 0; i < inv.getSize(); i++) {
            inv.setItem(i, filler);
        }
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

    private void drawTopBar(Inventory inv) {
        if (isBtn("back")) {
            inv.setItem(0, icon("back", "gui.leaderboard.back"));
        }
    }

    private void drawTopPlayersAsync(Inventory inv) {
        plugin.engine().asyncPool().execute(() -> {
            List<Map.Entry<UUID, Integer>> top = plugin.engine().progress().top(SLOTS.length);

            Bukkit.getScheduler().runTask(plugin, () -> {
                for (int i = 0; i < top.size() && i < SLOTS.length; i++) {
                    Map.Entry<UUID, Integer> e = top.get(i);
                    inv.setItem(SLOTS[i], buildHead(e.getKey(), i + 1, e.getValue(), false));
                }
            });

            if (Bukkit.getOnlineMode()) {
                for (int i = 0; i < top.size() && i < SLOTS.length; i++) {
                    Map.Entry<UUID, Integer> e = top.get(i);
                    UUID uuid = e.getKey();
                    int slot = SLOTS[i];

                    if (!TEXTURE_CACHE.containsKey(uuid)) {
                        String texture = fetchTexture(uuid);
                        if (texture != null) {
                            TEXTURE_CACHE.put(uuid, texture);
                            final int rank = i + 1;
                            final int pts = e.getValue();
                            Bukkit.getScheduler().runTask(plugin, () ->
                                    inv.setItem(slot, buildHead(uuid, rank, pts, true))
                            );
                        }
                    } else {
                        final int rank = i + 1;
                        final int pts = e.getValue();
                        Bukkit.getScheduler().runTask(plugin, () ->
                                inv.setItem(slot, buildHead(uuid, rank, pts, true))
                        );
                    }
                }
            }
        });
    }

    private ItemStack buildHead(UUID uuid, int rank, int points, boolean useTexture) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        if (meta == null) return head;

        OfflinePlayer off = Bukkit.getOfflinePlayer(uuid);
        String name = (off.getName() != null) ? off.getName() : "Unknown";

        String nameFmt = plugin.msg().get("gui.leaderboard.rank_format")
                .replace("%rank%", String.valueOf(rank))
                .replace("%player%", name)
                .replace("%points%", String.valueOf(points));

        meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', nameFmt));

        String loreFmt = plugin.msg().get("gui.leaderboard.rank_points")
                .replace("%points%", String.valueOf(points));
        meta.setLore(Collections.singletonList(ChatColor.translateAlternateColorCodes('&', loreFmt)));

        if (useTexture && profileField != null) {
            String tex = TEXTURE_CACHE.get(uuid);
            if (tex != null) {
                GameProfile profile = new GameProfile(uuid, name);
                profile.getProperties().put("textures", new Property("textures", tex));
                try {
                    profileField.set(meta, profile);
                } catch (Exception ignored) {}
            } else {
                meta.setOwningPlayer(off);
            }
        } else {
            meta.setOwningPlayer(off);
        }

        head.setItemMeta(meta);
        return head;
    }

    private String fetchTexture(UUID uuid) {
        try {
            URL url = new URL("https://sessionserver.mojang.com/session/minecraft/profile/" + uuid.toString().replace("-", ""));
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setReadTimeout(3000);

            if (conn.getResponseCode() == 200) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                reader.close();

                String json = sb.toString();
                String marker = "\"value\" : \"";
                int start = json.indexOf(marker);
                if (start == -1) {
                    marker = "\"value\":\"";
                    start = json.indexOf(marker);
                }

                if (start != -1) {
                    start += marker.length();
                    int end = json.indexOf("\"", start);
                    if (end != -1) {
                        return json.substring(start, end);
                    }
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof GuiHolder gh)) return;
        if (!"Q_LEADERBOARD".equals(gh.id())) return;

        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;

        if (e.getRawSlot() == 0 && isBtn("back")) {
            plugin.gui().sound(p, "click");
            Bukkit.getScheduler().runTaskLater(plugin, () -> plugin.gui().openList(p, 0), 1L);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof GuiHolder gh && "Q_LEADERBOARD".equals(gh.id())) {
            e.setCancelled(true);
        }
    }

    private boolean isBtn(String key) { return plugin.getConfig().getBoolean("gui.leaderboard.buttons." + key, true); }

    private ItemStack icon(String key, String langKey) {
        String path = "gui.leaderboard.icons." + key;
        Material mat = Material.matchMaterial(plugin.getConfig().getString(path + ".material", "BOOK"));
        int model = plugin.getConfig().getInt(path + ".model", -1);
        String name = plugin.msg().get(langKey);
        return createIcon(mat, name, model);
    }
}