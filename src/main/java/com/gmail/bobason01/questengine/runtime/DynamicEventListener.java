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

    private final List<Listener> activeListeners = new ArrayList<>();

    private final Map<String, Class<? extends Event>> classCache = new ConcurrentHashMap<>();

    public DynamicEventListener(QuestEnginePlugin plugin, Engine engine, QuestRepository repo) {
        this.plugin = plugin;
        this.engine = engine;

        Bukkit.getScheduler().runTaskLater(plugin, () -> registerAll(repo), 20L);
    }

    public void unregisterAll() {
        for (Listener l : activeListeners) {
            HandlerList.unregisterAll(l);
        }
        activeListeners.clear();
        classCache.clear();
    }

    public void registerAll(QuestRepository repo) {
        if (!activeListeners.isEmpty()) {
            unregisterAll();
        }

        PluginManager pm = Bukkit.getPluginManager();
        Set<String> uniqueEvents = new HashSet<>();

        for (QuestDef def : repo.all()) {
            if (def == null) continue;

            if (def.event != null && !def.event.isEmpty()) {
                String evt = def.event.toUpperCase(Locale.ROOT);
                if (!evt.equals("CUSTOM") && !evt.equals("CUSTOM_EVENT") && !evt.equals("NONE")) {
                    uniqueEvents.add(evt);
                }
            }

            if (def.custom != null && def.custom.eventClass != null && !def.custom.eventClass.isEmpty()) {
                uniqueEvents.add(def.custom.eventClass.trim());
            }
        }

        if (uniqueEvents.isEmpty()) {
            plugin.getLogger().info("[QuestEngine] No dynamic events found to register.");
            return;
        }

        int hookedCount = 0;
        for (String evtName : uniqueEvents) {
            Class<? extends Event> eventClass = resolveEventClass(evtName);

            if (eventClass == null) {
                plugin.getLogger().warning("[QuestEngine] Cannot resolve event class: " + evtName);
                continue;
            }

            try {
                EventExecutor executor = (listener, event) -> {
                    if (eventClass.isInstance(event)) {
                        engine.handleDynamic(event);
                    }
                };

                Listener listener = new Listener() {};

                pm.registerEvent(eventClass, listener, EventPriority.MONITOR, executor, plugin, true);

                activeListeners.add(listener);
                hookedCount++;

                plugin.getLogger().info("[QuestEngine] Successfully hooked event: " + eventClass.getName());

            } catch (Throwable t) {
                plugin.getLogger().severe("[QuestEngine] Failed to hook event " + evtName + ": " + t.getMessage());
            }
        }

        plugin.getLogger().info("[QuestEngine] Dynamic registration finished. Hooked " + hookedCount + " events.");

        engine.rebuildCustomEventIndex();
    }

    @SuppressWarnings("unchecked")
    private Class<? extends Event> resolveEventClass(String name) {
        if (classCache.containsKey(name)) return classCache.get(name);

        Class<? extends Event> found = null;

        switch (name.toUpperCase(Locale.ROOT)) {
            case "BLOCK_BREAK": found = org.bukkit.event.block.BlockBreakEvent.class; break;
            case "BLOCK_PLACE": found = org.bukkit.event.block.BlockPlaceEvent.class; break;
            case "ENTITY_DEATH": found = org.bukkit.event.entity.EntityDeathEvent.class; break;
            case "PLAYER_CHAT": found = org.bukkit.event.player.AsyncPlayerChatEvent.class; break;
            case "PLAYER_COMMAND": found = org.bukkit.event.player.PlayerCommandPreprocessEvent.class; break;
            case "ITEM_CRAFT": found = org.bukkit.event.inventory.CraftItemEvent.class; break;
            case "PLAYER_TELEPORT": found = org.bukkit.event.player.PlayerTeleportEvent.class; break;

            case "CRAFTSLOT_CLICK": found = org.bukkit.event.inventory.InventoryClickEvent.class; break;
        }

        if (found != null) {
            classCache.put(name, found);
            return found;
        }

        try {
            Class<?> clz = Class.forName(name);
            if (Event.class.isAssignableFrom(clz)) {
                found = (Class<? extends Event>) clz;
            }
        } catch (ClassNotFoundException e) {
            for (Plugin p : Bukkit.getPluginManager().getPlugins()) {
                try {
                    Class<?> externalClz = Class.forName(name, false, p.getClass().getClassLoader());
                    if (Event.class.isAssignableFrom(externalClz)) {
                        found = (Class<? extends Event>) externalClz;
                        break;
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