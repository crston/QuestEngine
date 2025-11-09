package com.gmail.bobason01.questengine.gui;

import com.gmail.bobason01.questengine.QuestEnginePlugin;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * QuestGuiManager
 * - QuestEngine GUI 총괄 매니저
 * - 모든 GUI(리스트, 공개, 리더보드) 진입점
 * - 세션, 사운드, 채우기 유틸 포함
 * - 생략, 누락 없이 완전 구현
 */
public final class QuestGuiManager {

    // =============================================================
    // 필드
    // =============================================================
    private final QuestEnginePlugin plugin;

    // 플레이어별 세션
    private final Map<UUID, Map<String, Object>> sessions = new ConcurrentHashMap<>();

    // 서브 GUI
    private final LeaderboardMenu leaderboardMenu;
    private final QuestListMenu questListMenu;
    private final PublicQuestMenu publicQuestMenu;

    // =============================================================
    // 생성자
    // =============================================================
    public QuestGuiManager(QuestEnginePlugin plugin) {
        this.plugin = plugin;

        // 채팅 입력 초기화 (검색창 등)
        ChatInput.init(plugin);

        // GUI 초기화 및 이벤트 등록
        this.leaderboardMenu = new LeaderboardMenu(plugin);
        this.questListMenu = new QuestListMenu(plugin);
        this.publicQuestMenu = new PublicQuestMenu(plugin);

        Bukkit.getLogger().info("[QuestGuiManager] GUI 시스템 초기화 완료");
    }

    // =============================================================
    // GUI 열기
    // =============================================================

    /** 리더보드 GUI 열기 */
    public void openLeaderboard(Player p) {
        if (p == null) return;
        leaderboardMenu.open(p);
    }

    /** 퀘스트 목록 GUI 열기 */
    public void openList(Player p) {
        if (p == null) return;
        questListMenu.open(p, 0);
    }

    /** 공개 퀘스트 GUI 열기 */
    public void openPublic(Player p) {
        if (p == null) return;
        publicQuestMenu.open(p, 0);
    }

    // =============================================================
    // 세션 관리
    // =============================================================

    /**
     * 세션 값 저장
     */
    public void putSession(Player p, String key, Object value) {
        if (p == null || key == null) return;
        sessions.computeIfAbsent(p.getUniqueId(), k -> new ConcurrentHashMap<>()).put(key, value);
    }

    /**
     * 세션 값 가져오기
     */
    public Object getSession(Player p, String key) {
        if (p == null || key == null) return null;
        Map<String, Object> map = sessions.get(p.getUniqueId());
        return map == null ? null : map.get(key);
    }

    /**
     * 세션 전체 초기화
     */
    public void clearSession(Player p) {
        if (p == null) return;
        sessions.remove(p.getUniqueId());
    }

    // =============================================================
    // 사운드 유틸
    // =============================================================

    /**
     * GUI 관련 사운드 재생
     * open, page, click, cancel, success
     */
    public void sound(Player p, String type) {
        if (p == null || type == null) return;
        try {
            switch (type.toLowerCase()) {
                case "open" -> p.playSound(p.getLocation(), Sound.UI_TOAST_IN, 0.7f, 1.2f);
                case "page" -> p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);
                case "click" -> p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, 0.8f);
                case "cancel" -> p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.7f, 0.9f);
                case "success" -> p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.4f);
                default -> {} // 정의되지 않은 사운드는 무시
            }
        } catch (Throwable t) {
            // 예외 무시
        }
    }

    // =============================================================
    // 인벤토리 채우기 유틸
    // =============================================================

    /**
     * 인벤토리 빈칸을 지정 아이템으로 채우기
     */
    public void fill(Inventory inv, ItemStack filler) {
        if (inv == null || filler == null) return;
        try {
            ItemStack item = filler.clone();
            for (int i = 0; i < inv.getSize(); i++) {
                if (inv.getItem(i) == null) inv.setItem(i, item);
            }
        } catch (Throwable t) {
            // 무시
        }
    }

    // =============================================================
    // 정적 아이템 (config 기반 확장용)
    // =============================================================

    /**
     * getStatic()
     * GUI 장식 아이템을 config에서 가져올 때 사용
     * 현재는 기본값으로 null을 반환함.
     * 필요 시 QuestEngine/config.yml에 "gui.decor" 섹션 추가 후 구현
     */
    public ItemStack getStatic(String key) {
        // 예: plugin.getConfig().getItemStack("gui." + key);
        return null;
    }

    // =============================================================
    // Getter
    // =============================================================

    public QuestEnginePlugin plugin() {
        return plugin;
    }

    public Map<UUID, Map<String, Object>> sessions() {
        return sessions;
    }

    public LeaderboardMenu leaderboard() {
        return leaderboardMenu;
    }

    public QuestListMenu list() {
        return questListMenu;
    }

    public PublicQuestMenu publicMenu() {
        return publicQuestMenu;
    }
}
