package com.gmail.bobason01.questengine.runtime;

import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class CitizensNpcInteractBridge implements Listener {

    private final Engine engine;

    // String Concatenation 방지를 위한 ID 캐시
    private final Map<Integer, String> keyCache = new ConcurrentHashMap<>();

    public CitizensNpcInteractBridge(Engine engine) {
        this.engine = engine;
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEntityEvent e) {
        // 1. 왼손(Off Hand) 이벤트는 무시 (이벤트 호출량 50% 감소)
        if (e.getHand() != EquipmentSlot.HAND) return;

        Entity clicked = e.getRightClicked();
        if (clicked == null) return;

        // 2. 메타데이터 검사 (Fast-Fail)
        // 일반 몹/엔티티 클릭 시 무거운 Citizens Registry 조회를 하지 않도록 방어
        if (!clicked.hasMetadata("NPC")) return;

        // 3. 실제 조회
        NPC npc = CitizensAPI.getNPCRegistry().getNPC(clicked);
        if (npc == null) return;

        // 4. 문자열 생성 비용 절감 (캐싱된 키 사용)
        String key = getKey(npc.getId());

        engine.handleNpcInteract(e.getPlayer(), key);
    }

    private String getKey(int id) {
        return keyCache.computeIfAbsent(id, i -> "CITIZENS:" + i);
    }
}