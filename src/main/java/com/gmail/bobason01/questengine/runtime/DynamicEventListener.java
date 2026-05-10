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
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class DynamicEventListener {

    private final QuestEnginePlugin plugin;
    private final Engine engine;

    // 현재 활성화된 동적 리스너들
    private final List<Listener> activeListeners = new ArrayList<>();

    // 클래스 해석 성능 최적화용 캐시
    private final Map<String, Class<? extends Event>> classCache = new ConcurrentHashMap<>();

    public DynamicEventListener(QuestEnginePlugin plugin, Engine engine, QuestRepository repo) {
        this.plugin = plugin;
        this.engine = engine;

        // 서버 기동 시 타 플러그인의 로딩 완료를 기다리기 위해 20틱(1초) 지연 후 등록
        Bukkit.getScheduler().runTaskLater(plugin, () -> registerAll(repo), 20L);
    }

    /**
     * 모든 동적 리스너를 해제하고 초기화합니다.
     */
    public void unregisterAll() {
        for (Listener l : activeListeners) {
            HandlerList.unregisterAll(l);
        }
        activeListeners.clear();
        classCache.clear();
    }

    /**
     * 퀘스트 리포지토리를 스캔하여 필요한 모든 이벤트를 훅(Hook)합니다.
     */
    public void registerAll(QuestRepository repo) {
        // 기존 리스너가 존재할 경우 중복 방지를 위해 먼저 해제
        if (!activeListeners.isEmpty()) {
            unregisterAll();
        }

        PluginManager pm = Bukkit.getPluginManager();
        Set<String> uniqueEvents = new HashSet<>();

        // 1. 모든 퀘스트 정의를 훑으며 감시할 이벤트 클래스 수집
        for (QuestDef def : repo.all()) {
            if (def == null) continue;

            // 일반 이벤트 필드 (별칭 포함)
            if (def.event != null && !def.event.isEmpty()) {
                String evt = def.event.toUpperCase(Locale.ROOT);
                if (!evt.equals("CUSTOM") && !evt.equals("CUSTOM_EVENT") && !evt.equals("NONE")) {
                    uniqueEvents.add(evt);
                }
            }

            // 커스텀 이벤트 섹션 (전체 클래스 경로 기반)
            if (def.custom != null && def.custom.eventClass != null && !def.custom.eventClass.isEmpty()) {
                uniqueEvents.add(def.custom.eventClass.trim());
            }
        }

        if (uniqueEvents.isEmpty()) {
            plugin.getLogger().info("[QuestEngine] No dynamic events found to register.");
            return;
        }

        // 2. 수집된 이벤트를 대상으로 리스너 등록 수행
        int hookedCount = 0;
        for (String evtName : uniqueEvents) {
            Class<? extends Event> eventClass = resolveEventClass(evtName);

            if (eventClass == null) {
                plugin.getLogger().warning("[QuestEngine] Cannot resolve event class: " + evtName);
                continue;
            }

            try {
                // 이벤트 발생 시 엔진의 handleDynamic으로 토스하는 익명 실행기 생성
                EventExecutor executor = (listener, event) -> {
                    if (eventClass.isInstance(event)) {
                        engine.handleDynamic(event);
                    }
                };

                Listener listener = new Listener() {};

                // MONITOR 우선순위로 등록하여 취소된 이벤트도 감지 가능하도록 설정
                pm.registerEvent(eventClass, listener, EventPriority.MONITOR, executor, plugin, true);

                activeListeners.add(listener);
                hookedCount++;

                plugin.getLogger().info("[QuestEngine] Successfully hooked event: " + eventClass.getName());

            } catch (Throwable t) {
                plugin.getLogger().severe("[QuestEngine] Failed to hook event " + evtName + ": " + t.getMessage());
            }
        }

        plugin.getLogger().info("[QuestEngine] Dynamic registration finished. Hooked " + hookedCount + " events.");

        // 엔진의 커스텀 인덱스 강제 갱신
        engine.rebuildCustomEventIndex();
    }

    /**
     * 별칭 혹은 전체 클래스 경로를 통해 실제 Class 객체를 반환합니다.
     * 외부 플러그인의 클래스 로더를 모두 검색하여 BentoBox 등의 이벤트를 찾아냅니다.
     */
    @SuppressWarnings("unchecked")
    private Class<? extends Event> resolveEventClass(String name) {
        if (classCache.containsKey(name)) return classCache.get(name);

        Class<? extends Event> found = null;

        // 1. 내장 별칭 매핑 (자주 쓰는 이벤트 최적화)
        switch (name.toUpperCase(Locale.ROOT)) {
            case "BLOCK_BREAK": found = org.bukkit.event.block.BlockBreakEvent.class; break;
            case "BLOCK_PLACE": found = org.bukkit.event.block.BlockPlaceEvent.class; break;
            case "ENTITY_DEATH": found = org.bukkit.event.entity.EntityDeathEvent.class; break;
            case "PLAYER_CHAT": found = org.bukkit.event.player.AsyncPlayerChatEvent.class; break;
            case "PLAYER_COMMAND": found = org.bukkit.event.player.PlayerCommandPreprocessEvent.class; break;
            case "ITEM_CRAFT": found = org.bukkit.event.inventory.CraftItemEvent.class; break;
        }

        if (found != null) {
            classCache.put(name, found);
            return found;
        }

        // 2. 전체 패키지 경로를 이용한 리플렉션 시도 (자체 플러그인 로더)
        try {
            Class<?> clz = Class.forName(name);
            if (Event.class.isAssignableFrom(clz)) {
                found = (Class<? extends Event>) clz;
            }
        } catch (ClassNotFoundException e) {
            // 3. [핵심] 타 플러그인의 클래스 로더 검색 (BentoBox, MythicMobs 등 외부 플러그인 대응)
            for (Plugin p : Bukkit.getPluginManager().getPlugins()) {
                try {
                    // 외부 플러그인의 로더를 사용하여 클래스 검색
                    Class<?> externalClz = Class.forName(name, false, p.getClass().getClassLoader());
                    if (Event.class.isAssignableFrom(externalClz)) {
                        found = (Class<? extends Event>) externalClz;
                        break; // 클래스를 찾으면 루프 종료
                    }
                } catch (ClassNotFoundException ignored) {}
            }
        }

        if (found != null) {
            classCache.put(name, found);
        }

        return found;
    }
}