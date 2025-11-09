package com.gmail.bobason01.questengine.gui;

import com.gmail.bobason01.questengine.QuestEnginePlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

/**
 * ChatInput
 * Await chat text from a player and deliver to callback
 */
public final class ChatInput implements Listener {

    private static ChatInput INSTANCE;

    private final QuestEnginePlugin plugin;
    private final Map<UUID, BiConsumer<Player, String>> waiting = new ConcurrentHashMap<>();

    private ChatInput(QuestEnginePlugin plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public static void init(QuestEnginePlugin plugin) {
        if (INSTANCE == null) {
            INSTANCE = new ChatInput(plugin);
        }
    }

    public static ChatInput get() {
        return INSTANCE;
    }

    public void await(Player p, String prompt, BiConsumer<Player, String> handler) {
        try {
            p.sendMessage(prompt);
        } catch (Throwable ignored) {
        }
        waiting.put(p.getUniqueId(), handler);
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent e) {
        BiConsumer<Player, String> handler = waiting.remove(e.getPlayer().getUniqueId());
        if (handler == null) return;
        String msg = e.getMessage();
        e.setCancelled(true);
        Bukkit.getScheduler().runTask(plugin, () -> handler.accept(e.getPlayer(), msg));
    }
}
