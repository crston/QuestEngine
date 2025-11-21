package com.gmail.bobason01.questengine.party;

import org.bukkit.entity.Player;
import java.util.*;

public interface PartyAdapter {

    boolean available();

    default boolean isInParty(final Player player) { return false; }

    Collection<Player> members(final Player player);

    default Collection<Player> getOnlineMembers(final Player player) { return members(player); }

    PartyAdapter EMPTY = new PartyAdapter() {
        private final List<Player> singleton = new ArrayList<>(1);

        @Override
        public boolean available() { return false; }

        @Override
        public Collection<Player> members(final Player p) {
            singleton.clear();
            if (p != null) {
                singleton.add(p);
            }
            return singleton;
        }

        @Override
        public Collection<Player> getOnlineMembers(final Player p) {
            singleton.clear();
            if (p != null) {
                singleton.add(p);
            }
            return singleton;
        }

        @Override
        public boolean isInParty(final Player player) {
            return false;
        }
    };
}

