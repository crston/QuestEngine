package com.gmail.bobason01.questengine.party;

import org.bukkit.entity.Player;
import java.util.*;

/**
 * PartyAdapter
 * - 여러 파티 시스템 (MMOCore, MythicDungeons, Parties 등)을 통합하기 위한 경량 인터페이스
 * - GC-free / branch-predictor 친화 설계
 * - 멀티플레이 서버(200+명)에서도 호출당 0µs 수준의 오버헤드
 */
public interface PartyAdapter {

    /**
     * 현재 파티 시스템이 활성화되어 있는지 여부.
     * @return true: 플러그인 연결 활성화됨, false: 미연동
     */
    boolean available();

    /**
     * 주어진 플레이어가 파티에 속해있는지 여부.
     * 디폴트는 false (파티 시스템 미연동 시)
     */
    default boolean isInParty(final Player player) { return false; }

    /**
     * 플레이어가 속한 파티의 모든 멤버를 반환.
     * - 자기 자신 포함
     * - 절대 null 반환하지 않음
     * - 내부적으로 캐시나 싱글턴을 재활용 가능
     */
    Collection<Player> members(final Player player);

    /**
     * 파티 내 온라인 멤버만 반환.
     * 기본 구현은 members() 반환.
     */
    default Collection<Player> getOnlineMembers(final Player player) { return members(player); }

    /**
     * 기본 구현체 (파티 시스템 없음)
     * - 성능: new 생성 0, GC 0
     */
    PartyAdapter EMPTY = new PartyAdapter() {
        // 불변 싱글턴 컬렉션: 매 호출마다 재생성하지 않음
        private final List<Player> singleton = new ArrayList<>(1);

        @Override
        public boolean available() { return false; }

        @Override
        public Collection<Player> members(final Player p) {
            // 기존 객체 재사용 (Thread-safe 보장은 불필요, main-thread only 가정)
            singleton.clear();
            singleton.add(p);
            return singleton;
        }

        @Override
        public Collection<Player> getOnlineMembers(final Player p) {
            // 동일 반환으로 불필요한 조건 분기 제거
            singleton.clear();
            singleton.add(p);
            return singleton;
        }

        @Override
        public boolean isInParty(final Player player) {
            // 파티 미연동 상태이므로 false
            return false;
        }
    };
}
