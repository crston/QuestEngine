package com.gmail.bobason01.questengine.runtime;

import io.lumine.mythic.bukkit.MythicBukkit;
import io.lumine.mythic.core.mobs.ActiveMob;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class MythicmobsNpcInteractBridge implements Listener {

    private final Engine engine;

    // String 객체 생성 비용 절감을 위한 캐시 (메인 스레드 전용이므로 HashMap 사용)
    private final Map<String, String> keyCache = new HashMap<>();

    public MythicmobsNpcInteractBridge(Engine engine) {
        this.engine = engine;
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEntityEvent e) {
        // 1. 최적화: 왼손(Off Hand) 이벤트 무시 (필수)
        // 이걸 안 하면 퀘스트 진행도가 2씩 올라가거나 대화가 두 번 뜹니다.
        if (e.getHand() != EquipmentSlot.HAND) return;

        Entity clicked = e.getRightClicked();
        if (clicked == null) return;

        // MythicMobs 인스턴스 로드
        MythicBukkit mythic = MythicBukkit.inst();
        // 플러그인이 언로드된 상태라면 안전하게 리턴
        if (mythic == null || mythic.getMobManager() == null) return;

        // 2. API 조회 (UUID Lookup은 O(1)이라 빠름)
        Optional<ActiveMob> opt = mythic.getMobManager().getActiveMob(clicked.getUniqueId());
        if (opt.isEmpty()) return;

        ActiveMob mob = opt.get();
        // 몹 타입 이름 (ex: SkeletonKing)
        String internalName = mob.getType().getInternalName();
        if (internalName == null) return;

        // 3. 최적화: 캐싱된 키 문자열 사용 (Zero-GC)
        String key = getKey(internalName);

        engine.handleNpcInteract(e.getPlayer(), key);
    }

    private String getKey(String internalName) {
        // computeIfAbsent: 캐시에 있으면 가져오고, 없으면 만들어서 저장 (매우 빠름)
        return keyCache.computeIfAbsent(internalName, name ->
                "MYTHICMOBS:" + name.toUpperCase(Locale.ROOT)
        );
    }
}