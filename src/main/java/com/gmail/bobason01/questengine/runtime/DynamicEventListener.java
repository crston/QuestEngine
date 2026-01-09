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

    // 메인 스레드 전용 리스너 목록
    private final List<Listener> activeListeners = new ArrayList<>();
    // 클래스 캐시
    private final Map<String, Class<? extends Event>> classCache = new HashMap<>();

    public DynamicEventListener(QuestEnginePlugin plugin, Engine engine, QuestRepository repo) {
        this.plugin = plugin;
        this.engine = engine;
        // 1틱 딜레이: 다른 플러그인 로드 대기
        Bukkit.getScheduler().runTaskLater(plugin, () -> registerAll(repo), 1L);
    }

    public void unregisterAll() {
        for (Listener l : activeListeners) {
            HandlerList.unregisterAll(l);
        }
        activeListeners.clear();
        classCache.clear();
    }

    private void registerAll(QuestRepository repo) {
        PluginManager pm = Bukkit.getPluginManager();
        Set<String> uniqueEvents = new HashSet<>();

        // 1. 모든 퀘스트의 이벤트 이름 수집
        for (QuestDef def : repo.all()) {
            if (def == null) continue;

            // 일반 이벤트 (m1.yml의 'event' 필드)
            if (def.event != null && !def.event.isEmpty()) {
                String evt = def.event.toUpperCase(Locale.ROOT);
                // [수정] CUSTOM_EVENT 또는 CUSTOM은 클래스 이름이 아니므로 수집에서 제외
                if (!evt.equals("CUSTOM_EVENT") && !evt.equals("CUSTOM")) {
                    uniqueEvents.add(evt);
                }
            }

            // 커스텀 이벤트 (custom 섹션) - 여기가 진짜 클래스 이름
            if (def.custom != null && def.custom.eventClass != null) {
                uniqueEvents.add(def.custom.eventClass);
            }
        }

        if (uniqueEvents.isEmpty()) return;

        int hooked = 0;

        // 2. 이벤트 등록
        for (String evtName : uniqueEvents) {
            Class<? extends Event> eventClass = resolveEventClass(evtName);

            if (eventClass == null) {
                if (!evtName.equals("NONE")) {
                    plugin.getLogger().warning("[QuestEngine] Unknown event type in quest config: " + evtName);
                }
                continue;
            }

            try {
                // EventExecutor: 이벤트 발생 시 Engine으로 전달
                EventExecutor exec = (listener, event) -> {
                    if (eventClass.isInstance(event)) {
                        engine.handleDynamic(event);
                    }
                };

                Listener listener = new Listener() {};
                // MONITOR 우선순위: 다른 플러그인이 캔슬해도 감지 (ignoreCancelled=true)
                pm.registerEvent(eventClass, listener, EventPriority.MONITOR, exec, plugin, true);

                activeListeners.add(listener);
                hooked++;

                plugin.getLogger().info("[QuestEngine] Hooked event: " + evtName + " -> " + eventClass.getSimpleName());

            } catch (Throwable t) {
                plugin.getLogger().warning("[QuestEngine] Failed to hook event " + evtName + ": " + t.getMessage());
            }
        }
    }

    @SuppressWarnings("unchecked")
    private Class<? extends Event> resolveEventClass(String name) {
        if (classCache.containsKey(name)) return classCache.get(name);

        Class<? extends Event> found = null;

        // 특수 이름 매핑
        switch (name) {
            case "MYTHICMOBS_ENTITY_KILL":
            case "MYTHICMOBS_DEATH":
                if (Bukkit.getPluginManager().isPluginEnabled("MythicMobs")) {
                    try {
                        found = (Class<? extends Event>) Class.forName("io.lumine.mythic.bukkit.events.MythicMobDeathEvent");
                    } catch (ClassNotFoundException e) {
                        plugin.getLogger().warning("MythicMobs found but class missing. Check version.");
                    }
                }
                break;

            case "BLOCK_BREAK":
                found = org.bukkit.event.block.BlockBreakEvent.class;
                break;
            case "BLOCK_PLACE":
                found = org.bukkit.event.block.BlockPlaceEvent.class;
                break;
            case "ENTITY_DEATH":
            case "MOBKILLING":
            case "ENTITY_KILL":
                found = org.bukkit.event.entity.EntityDeathEvent.class;
                break;
            case "PLAYER_CHAT":
            case "ASYNC_PLAYER_CHAT":
                found = org.bukkit.event.player.AsyncPlayerChatEvent.class;
                break;
            case "PLAYER_COMMAND":
                found = org.bukkit.event.player.PlayerCommandPreprocessEvent.class;
                break;
            case "ENTITY_INTERACT":
            case "PLAYER_INTERACT_ENTITY":
                found = org.bukkit.event.player.PlayerInteractEntityEvent.class;
                break;
        }

        // 매핑에 없으면 클래스 이름 그대로 찾아봄 (Reflection)
        if (found == null) {
            try {
                Class<?> clz = Class.forName(name);
                if (Event.class.isAssignableFrom(clz)) {
                    found = (Class<? extends Event>) clz;
                }
            } catch (ClassNotFoundException ignored) {}
        }

        classCache.put(name, found);
        return found;
    }
}