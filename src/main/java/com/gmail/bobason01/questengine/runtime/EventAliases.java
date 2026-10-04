package com.gmail.bobason01.questengine.runtime;

import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Canonical event-name normalization (docs aliases → internal keys).
 * Hot-path safe: static map, no allocations beyond uppercasing.
 */
public final class EventAliases {

    private static final Map<String, String> CANONICAL;

    static {
        Map<String, String> m = new HashMap<>(32);
        m.put("MYTHICMOBS_KILL", "MYTHICMOBS_ENTITY_KILL");
        m.put("CHUNK_LOAD", "WORLD_CHUNK_LOAD");
        m.put("BLOCK_ITEM_DROPPING", "ITEM_DROP");
        m.put("ITEM_SELECT", "ITEM_HELD");
        m.put("BREEDNG", "BREEDING");
        m.put("MOBKILLING", "ENTITY_KILL");
        m.put("ENTITY_DEATH", "ENTITY_KILL");
        m.put("BLOCK_FERTILIZING", "BLOCK_FERTILIZING");
        m.put("FARMING", "FARMING");
        CANONICAL = Collections.unmodifiableMap(m);
    }

    private EventAliases() {}

    public static String canonicalize(String eventName) {
        if (eventName == null || eventName.isEmpty()) return "";
        String key = eventName.trim().toUpperCase(Locale.ROOT);
        return CANONICAL.getOrDefault(key, key);
    }
}
