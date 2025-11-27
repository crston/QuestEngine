package com.gmail.bobason01.questengine.runtime;

import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.EnumMap;
import java.util.Map;

public final class DefaultEntityInteractBridge implements Listener {

    private final Engine engine;

    // EnumMap은 HashMap보다 메모리를 적게 쓰고 속도가 훨씬 빠릅니다.
    private final Map<EntityType, String> keyCache = new EnumMap<>(EntityType.class);

    public DefaultEntityInteractBridge(Engine engine) {
        this.engine = engine;
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEntityEvent e) {
        // 1. 왼손(Off Hand) 무시 (이벤트 50% 감소)
        if (e.getHand() != EquipmentSlot.HAND) return;

        Entity clicked = e.getRightClicked();
        if (clicked == null) return;

        // 2. Citizens NPC나 ArmorStand 등 불필요한 엔티티 제외 (선택 사항)
        if (clicked.hasMetadata("NPC")) return;

        // 3. 문자열 결합 비용 제거 (EnumMap 캐싱)
        // "ENTITY:ZOMBIE" 같은 문자열을 처음에만 만들고 재사용합니다.
        String key = getKey(clicked.getType());

        engine.handleNpcInteract(e.getPlayer(), key);
    }

    private String getKey(EntityType type) {
        return keyCache.computeIfAbsent(type, t -> "ENTITY:" + t.name());
    }
}