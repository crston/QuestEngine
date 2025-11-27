package com.gmail.bobason01.questengine.party;

import org.bukkit.entity.Player;
import java.util.*;

public interface PartyAdapter {

    boolean available();

    default boolean isInParty(final Player player) {
        // members가 본인 외에 더 있으면 파티라고 간주
        return members(player).size() > 1;
    }

    Collection<Player> members(final Player player);

    default Collection<Player> getOnlineMembers(final Player player) {
        return members(player);
    }

    // 상태를 가지지 않는 불변 객체 사용 (Thread-Safe)
    PartyAdapter EMPTY = new PartyAdapter() {
        @Override
        public boolean available() { return false; }

        @Override
        public Collection<Player> members(final Player p) {
            return p == null ? Collections.emptyList() : Collections.singletonList(p);
        }

        @Override
        public boolean isInParty(final Player player) { return false; }
    };
}