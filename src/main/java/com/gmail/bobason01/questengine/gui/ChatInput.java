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
import java.util.function.Consumer;

/**
 * ChatInput
 * - 전역 채팅 입력 유틸리티
 * - GUI와 연동 가능한 비동기 안전 입력 시스템
 * - BiConsumer<Player, String> / Consumer<String> 모두 지원
 */
public final class ChatInput implements Listener {

    private static ChatInput INSTANCE;
    private static QuestEnginePlugin plugin;

    // 대기 중인 입력자 목록
    private final Map<UUID, BiConsumer<Player, String>> waiting = new ConcurrentHashMap<>();

    private ChatInput(QuestEnginePlugin pl) {
        plugin = pl;
        Bukkit.getPluginManager().registerEvents(this, pl);
    }

    /** 초기화 (onEnable에서 한 번만 호출) */
    public static void init(QuestEnginePlugin pl) {
        if (INSTANCE == null) {
            INSTANCE = new ChatInput(pl);
            Bukkit.getLogger().info("[ChatInput] Registered global chat input listener.");
        }
    }

    /** 내부 인스턴스 반환 */
    static ChatInput get() {
        if (INSTANCE == null) {
            throw new IllegalStateException("ChatInput not initialized. Call ChatInput.init(plugin) in onEnable().");
        }
        return INSTANCE;
    }

    /** 플레이어 입력 대기 시작 (메시지 안내 포함) */
    public static void await(Player p, String prompt, BiConsumer<Player, String> handler) {
        if (p == null || handler == null) return;
        if (prompt != null && !prompt.isEmpty()) {
            try {
                p.sendMessage(prompt);
            } catch (Throwable ignored) {}
        }
        get().waiting.put(p.getUniqueId(), handler);
    }

    /** 플레이어 입력 대기 시작 (간단형 Consumer<String> 지원) */
    public static void await(Player p, Consumer<String> handler) {
        await(p, null, (pp, msg) -> handler.accept(msg));
    }

    /** 채팅 감지 이벤트 */
    @EventHandler
    public void onChat(AsyncPlayerChatEvent e) {
        Player player = e.getPlayer();
        BiConsumer<Player, String> handler = waiting.remove(player.getUniqueId());
        if (handler == null) return;

        // GUI 충돌 방지: 채팅 차단
        e.setCancelled(true);

        String msg = e.getMessage();
        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                handler.accept(player, msg);
            } catch (Throwable t) {
                plugin.getLogger().warning("[ChatInput] Error processing input from " + player.getName() + ": " + t.getMessage());
            }
        });
    }

    /** 특정 플레이어 입력 대기 중인지 확인 */
    public static boolean isWaiting(Player p) {
        if (p == null) return false;
        return get().waiting.containsKey(p.getUniqueId());
    }

    /** 특정 플레이어 입력 취소 */
    public static void cancel(Player p) {
        if (p == null) return;
        get().waiting.remove(p.getUniqueId());
    }

    /** 모든 입력 취소 */
    public static void clearAll() {
        get().waiting.clear();
    }
}
