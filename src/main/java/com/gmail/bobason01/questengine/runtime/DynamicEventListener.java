package com.gmail.bobason01.questengine.runtime;

import com.gmail.bobason01.questengine.QuestEnginePlugin;
import com.gmail.bobason01.questengine.quest.QuestDef;
import com.gmail.bobason01.questengine.quest.QuestRepository;
import org.bukkit.Bukkit;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.PluginManager;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * DynamicEventListener
 * - QuestEngine의 동적 이벤트 감지 시스템
 * - 커스텀 이벤트 자동 리스너 등록
 * - Class 캐시 및 핫리로드 안전 구조
 */
public final class DynamicEventListener {

    private final QuestEnginePlugin plugin;
    private final Engine engine;

    // 중복 등록 방지용 캐시
    private final Set<String> listened = ConcurrentHashMap.newKeySet();

    // 캐시된 이벤트 클래스 (성능 향상)
    private static final Map<String, Class<? extends Event>> CLASS_CACHE = new ConcurrentHashMap<>();

    // 리스너 참조 (reload 시 해제용)
    private final List<Listener> activeListeners = new ArrayList<>();

    public DynamicEventListener(QuestEnginePlugin plugin, Engine engine, QuestRepository repo) {
        this.plugin = plugin;
        this.engine = engine;

        // 서버 부팅 완료 후 비동기적으로 등록 (지연 1틱)
        Bukkit.getScheduler().runTaskLater(plugin, () -> registerAll(repo), 1L);
    }

    /**
     * 등록된 리스너 전체 해제 (핫리로드 시 안전하게 호출)
     */
    public void unregisterAll() {
        for (Listener l : activeListeners) {
            HandlerList.unregisterAll(l);
        }
        activeListeners.clear();
        listened.clear();
    }

    /**
     * 모든 커스텀 이벤트 탐색 및 리스너 등록
     */
    private void registerAll(QuestRepository repo) {
        final PluginManager pm = Bukkit.getPluginManager();

        int hooked = 0;
        for (String id : repo.ids()) {
            QuestDef def = repo.get(id);
            if (def == null) continue;

            String evt = def.event;
            if (evt == null || !evt.contains(".")) continue; // 기본 이벤트는 제외
            if (!listened.add(evt.intern())) continue; // 중복 등록 방지

            try {
                Class<? extends Event> eventClass = loadEventClass(evt);
                if (eventClass == null) continue;

                // 리스너 + 실행자 분리 (성능 향상)
                EventExecutor exec = (listener, event) -> {
                    if (eventClass.isInstance(event))
                        engine.handleDynamic(event);
                };

                Listener listener = new Listener() {};
                pm.registerEvent(eventClass, listener, org.bukkit.event.EventPriority.MONITOR, exec, plugin, true);
                activeListeners.add(listener);
                hooked++;

            } catch (Throwable t) {
                plugin.getLogger().warning("[QuestEngine] Failed to hook event " + evt + ": " + t.getClass().getSimpleName());
            }
        }

        plugin.getLogger().info("[QuestEngine] Hooked " + hooked + " custom quest events");
    }

    /**
     * 캐시 기반 이벤트 클래스 로드
     */
    @SuppressWarnings("unchecked")
    private static Class<? extends Event> loadEventClass(String name) {
        try {
            return CLASS_CACHE.computeIfAbsent(name, key -> {
                try {
                    Class<?> clz = Class.forName(key);
                    return Event.class.isAssignableFrom(clz) ? (Class<? extends Event>) clz : null;
                } catch (ClassNotFoundException ex) {
                    return null;
                }
            });
        } catch (Throwable ignored) {
            return null;
        }
    }
}
