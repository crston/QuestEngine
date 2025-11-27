package com.gmail.bobason01.questengine.runtime;

import com.gmail.bobason01.questengine.QuestEnginePlugin;
import com.gmail.bobason01.questengine.quest.QuestDef;
import com.gmail.bobason01.questengine.quest.QuestRepository;
import org.bukkit.Bukkit;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.PluginManager;

import java.util.*;

public final class DynamicEventListener {

    private final QuestEnginePlugin plugin;
    private final Engine engine;

    // 메인 스레드 전용이므로 동기화 불필요 (ArrayList/HashSet 사용으로 속도 향상)
    private final List<Listener> activeListeners = new ArrayList<>();

    // 클래스 로딩 캐시 (메모리 누수 방지를 위해 인스턴스 변수로 변경)
    private final Map<String, Class<? extends Event>> classCache = new HashMap<>();

    public DynamicEventListener(QuestEnginePlugin plugin, Engine engine, QuestRepository repo) {
        this.plugin = plugin;
        this.engine = engine;
        // 1틱 딜레이는 다른 플러그인 로드 대기를 위해 유지
        Bukkit.getScheduler().runTaskLater(plugin, () -> registerAll(repo), 1L);
    }

    public void unregisterAll() {
        for (Listener l : activeListeners) {
            HandlerList.unregisterAll(l);
        }
        activeListeners.clear();
        classCache.clear(); // 캐시 정리로 메모리 확보
    }

    private void registerAll(QuestRepository repo) {
        PluginManager pm = Bukkit.getPluginManager();

        // 1. 최적화: 퀘스트 목록을 순회하며 중복된 이벤트 이름을 먼저 Set으로 추출 (Deduplication)
        // 1000개의 퀘스트가 같은 이벤트를 써도, 등록 루프는 1번만 돕니다.
        Set<String> uniqueEvents = new HashSet<>();

        for (QuestDef def : repo.all()) {
            if (def == null || def.custom == null) continue;
            String evt = def.custom.eventClass;
            if (evt != null && !evt.isEmpty()) {
                uniqueEvents.add(evt.trim());
            }
        }

        if (uniqueEvents.isEmpty()) return;

        int hooked = 0;

        // 2. 최적화: 유니크한 이벤트 목록에 대해서만 리플렉션 및 등록 수행
        for (String evtName : uniqueEvents) {
            Class<? extends Event> eventClass = loadEventClass(evtName);
            if (eventClass == null) {
                // 경고는 한 번만 출력 (로그 스팸 방지)
                plugin.getLogger().warning("[QuestEngine] Cannot find custom event class: " + evtName);
                continue;
            }

            try {
                // EventExecutor: 람다 최적화
                EventExecutor exec = (listener, event) -> {
                    // isInstance 체크는 Bukkit 내부에서 보장하지만 안전장치로 유지
                    if (eventClass.isInstance(event)) {
                        engine.handleDynamic(event);
                    }
                };

                // 익명 클래스 대신 빈 리스너 객체 생성 (가벼움)
                Listener listener = new Listener() {};

                // MONITOR 우선순위: 다른 플러그인이 캔슬한 것은 무시하고(ignoreCancelled=true), 최종 결과만 확인
                pm.registerEvent(eventClass, listener, EventPriority.MONITOR, exec, plugin, true);

                activeListeners.add(listener);
                hooked++;

            } catch (Throwable t) {
                plugin.getLogger().warning("[QuestEngine] Failed to hook event " + evtName + ": " + t.getMessage());
            }
        }

        if (hooked > 0) {
            plugin.getLogger().info("[QuestEngine] Hooked " + hooked + " custom event types");
        }
    }

    @SuppressWarnings("unchecked")
    private Class<? extends Event> loadEventClass(String name) {
        // computeIfAbsent는 람다 객체를 생성하므로, 단순 반복문에서는 get/put 패턴이 더 빠를 수 있으나
        // 여기서는 클래스 로딩(Class.forName) 비용이 훨씬 크므로 가독성을 위해 computeIfAbsent 유지
        return classCache.computeIfAbsent(name, key -> {
            try {
                Class<?> clz = Class.forName(key);
                if (Event.class.isAssignableFrom(clz)) {
                    return (Class<? extends Event>) clz;
                }
                return null;
            } catch (Throwable ignored) {
                return null;
            }
        });
    }
}