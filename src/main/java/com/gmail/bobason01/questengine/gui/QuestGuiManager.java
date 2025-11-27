package com.gmail.bobason01.questengine.gui;

import com.gmail.bobason01.questengine.QuestEnginePlugin;
import com.gmail.bobason01.questengine.quest.QuestDef;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * QuestGuiManager
 * - Session 관리 스레드 안전성 강화
 * - GUI 오픈 헬퍼 메소드 제공
 */
public final class QuestGuiManager {

    private final QuestEnginePlugin plugin;

    // 동시성 제어가 필요한 세션 저장소
    private final Map<UUID, Map<String, Object>> sessions = new ConcurrentHashMap<>();

    // 메뉴 인스턴스 (Lazy Loading 가능하나, 여기선 미리 로드)
    private final LeaderboardMenu leaderboardMenu;
    private final QuestListMenu questListMenu;
    private final PublicQuestMenu publicQuestMenu;
    private final QuestConfirmMenu confirmMenu;

    public QuestGuiManager(QuestEnginePlugin plugin) {
        this.plugin = plugin;
        ChatInput.init(plugin); // 채팅 입력 초기화

        // 메뉴 초기화
        this.leaderboardMenu = new LeaderboardMenu(plugin);
        this.questListMenu = new QuestListMenu(plugin);
        this.publicQuestMenu = new PublicQuestMenu(plugin);
        this.confirmMenu = new QuestConfirmMenu(plugin);

        // GUI 보호 리스너 등록
        new GuiProtectionListener(plugin);

        plugin.getLogger().info("[QuestGuiManager] Initialized successfully.");
    }

    // --- GUI Open Helpers ---

    public void openLeaderboard(Player p) { if (p != null) leaderboardMenu.open(p); }
    public void openList(Player p) { if (p != null) questListMenu.open(p, 0); }
    public void openPublic(Player p) { if (p != null) publicQuestMenu.open(p, 0); }
    public void openConfirm(Player p, QuestDef quest) { if (p != null && quest != null) confirmMenu.open(p, quest); }

    // --- Session Management ---

    public void putSession(Player p, String key, Object value) {
        if (p == null || key == null) return;
        sessions.computeIfAbsent(p.getUniqueId(), k -> new ConcurrentHashMap<>()).put(key, value);
    }

    public Object getSession(Player p, String key) {
        if (p == null || key == null) return null;
        Map<String, Object> map = sessions.get(p.getUniqueId());
        return map != null ? map.get(key) : null;
    }

    public void removeSession(Player p, String key) {
        if (p == null || key == null) return;
        Map<String, Object> map = sessions.get(p.getUniqueId());
        if (map != null) map.remove(key);
    }

    public void clearSession(Player p) {
        if (p != null) sessions.remove(p.getUniqueId());
    }

    // --- Utils ---

    public void sound(Player p, String type) {
        if (p == null || type == null) return;
        Sound sound = switch (type.toLowerCase()) {
            case "open" -> Sound.UI_TOAST_IN;
            case "page", "click" -> Sound.UI_BUTTON_CLICK;
            case "cancel" -> Sound.ENTITY_VILLAGER_NO;
            case "success" -> Sound.ENTITY_PLAYER_LEVELUP;
            default -> null;
        };

        if (sound != null) {
            p.playSound(p.getLocation(), sound, 0.7f, 1.2f);
        }
    }

    public void fill(Inventory inv, ItemStack filler) {
        if (inv == null || filler == null) return;
        for (int i = 0; i < inv.getSize(); i++) {
            if (inv.getItem(i) == null) {
                inv.setItem(i, filler);
            }
        }
    }

    // --- Getters ---

    public QuestEnginePlugin plugin() { return plugin; }
    public QuestConfirmMenu confirm() { return confirmMenu; }
    public QuestListMenu list() { return questListMenu; }
    public PublicQuestMenu publicMenu() { return publicQuestMenu; }
    public LeaderboardMenu leaderboard() { return leaderboardMenu; }
}