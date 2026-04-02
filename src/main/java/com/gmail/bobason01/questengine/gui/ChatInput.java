package com.gmail.bobason01.questengine.gui;

import com.gmail.bobason01.questengine.QuestEnginePlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public final class ChatInput implements Listener {

    private static ChatInput INSTANCE;
    private static QuestEnginePlugin plugin;

    // 비동기 채팅 이벤트에서 접근하므로 ConcurrentHashMap 필수
    private final Map<UUID, BiConsumer<Player, String>> waiting = new ConcurrentHashMap<>();

    private ChatInput(QuestEnginePlugin pl) {
        plugin = pl;
        Bukkit.getPluginManager().registerEvents(this, pl);
    }

    public static void init(QuestEnginePlugin pl) {
        if (INSTANCE == null) {
            INSTANCE = new ChatInput(pl);
        }
    }

    static ChatInput get() {
        if (INSTANCE == null) {
            throw new IllegalStateException("ChatInput not initialized!");
        }
        return INSTANCE;
    }

    public static void await(Player p, String prompt, BiConsumer<Player, String> handler) {
        if (p == null || handler == null) return;
        if (prompt != null && !prompt.isEmpty()) {
            p.sendMessage(prompt);
        }
        get().waiting.put(p.getUniqueId(), handler);
    }

    public static void await(Player p, Consumer<String> handler) {
        await(p, null, (player, msg) -> handler.accept(msg));
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncPlayerChatEvent e) {
        // 맵에 없으면 빠르게 리턴 (Fast-Path)
        if (waiting.isEmpty()) return;

        Player player = e.getPlayer();
        UUID uid = player.getUniqueId();

        // containsKey 체크보다 remove가 원자적(Atomic)이라 더 안전함
        BiConsumer<Player, String> handler = waiting.remove(uid);
        if (handler == null) return;

        e.setCancelled(true);
        String msg = e.getMessage();

        // 로직은 메인 스레드에서 실행 (스레드 안전성 보장)
        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                handler.accept(player, msg);
            } catch (Throwable t) {
                plugin.getLogger().warning("[ChatInput] Callback error: " + t.getMessage());
                t.printStackTrace();
            }
        });
    }

    // [중요] 플레이어가 나가면 대기열에서 삭제해야 메모리 누수가 없음
    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        waiting.remove(e.getPlayer().getUniqueId());
    }

    public static boolean isWaiting(Player p) {
        return p != null && get().waiting.containsKey(p.getUniqueId());
    }

    public static void cancel(Player p) {
        if (p != null) get().waiting.remove(p.getUniqueId());
    }

    public static void clearAll() {
        get().waiting.clear();
    }
}