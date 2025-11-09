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
 */
public final class DynamicEventListener {

    private final QuestEnginePlugin plugin;
    private final Engine engine;

    private final Set<String> listened = ConcurrentHashMap.newKeySet();
    private final List<Listener> activeListeners = new ArrayList<>();

    private static final Map<String, Class<? extends Event>> CLASS_CACHE = new ConcurrentHashMap<>();

    public DynamicEventListener(QuestEnginePlugin plugin, Engine engine, QuestRepository repo) {
        this.plugin = plugin;
        this.engine = engine;
        Bukkit.getScheduler().runTaskLater(plugin, () -> registerAll(repo), 1L);
    }

    public void unregisterAll() {
        for (Listener l : activeListeners) HandlerList.unregisterAll(l);
        activeListeners.clear();
        listened.clear();
    }

    private void registerAll(QuestRepository repo) {
        final PluginManager pm = Bukkit.getPluginManager();
        int hooked = 0;

        for (String id : repo.ids()) {
            QuestDef def = repo.get(id);
            if (def == null) continue;
            String evt = def.event;
            if (evt == null || !evt.contains(".")) continue;
            if (!listened.add(evt.intern())) continue;

            try {
                Class<? extends Event> eventClass = loadEventClass(evt);
                if (eventClass == null) continue;

                EventExecutor exec = (listener, event) -> {
                    if (eventClass.isInstance(event)) engine.handleDynamic(event);
                };

                Listener listener = new Listener() {};
                pm.registerEvent(eventClass, listener, org.bukkit.event.EventPriority.MONITOR, exec, plugin, true);
                activeListeners.add(listener);
                hooked++;

            } catch (Throwable t) {
                plugin.getLogger().warning("[QuestEngine] Failed to hook event " + evt + ": " + t.getMessage());
            }
        }

        plugin.getLogger().info("[QuestEngine] Hooked " + hooked + " custom quest events");
    }

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
