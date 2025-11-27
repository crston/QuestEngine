package com.gmail.bobason01.questengine.party;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.util.*;

/**
 * PartyHook (Optimized)
 * - MethodHandle을 사용한 고성능 리플렉션
 * - 불필요한 객체 생성 제거 (Zero-GC 지향)
 * - 각 플러그인 로직을 독립적인 클래스로 분리
 */
public final class PartyHook {

    private static PartyAdapter adapter = PartyAdapter.EMPTY;
    private static boolean enabled = false;

    private PartyHook() {}

    public static void init(Plugin plugin, FileConfiguration cfg) {
        enabled = cfg.getBoolean("party.enabled", true);
        if (!enabled) {
            adapter = PartyAdapter.EMPTY;
            return;
        }

        String provider = cfg.getString("party.provider", "auto");
        if (provider == null) provider = "auto";
        provider = provider.toLowerCase(Locale.ROOT);

        // Auto Detect
        if ("auto".equals(provider)) {
            if (Bukkit.getPluginManager().isPluginEnabled("MythicDungeons")) provider = "mythicdungeons";
            else if (Bukkit.getPluginManager().isPluginEnabled("MMOCore")) provider = "mmocore";
            else if (Bukkit.getPluginManager().isPluginEnabled("Parties")) provider = "parties";
            else provider = "none";
        }

        try {
            switch (provider) {
                case "mythicdungeons" -> adapter = new MythicDungeonsAdapter();
                case "mmocore" -> adapter = new MMOCoreAdapter();
                case "parties" -> adapter = new PartiesAdapter();
                default -> adapter = PartyAdapter.EMPTY;
            }
        } catch (Throwable t) {
            plugin.getLogger().warning("[QuestEngine] Failed to hook party provider '" + provider + "': " + t.getMessage());
            adapter = PartyAdapter.EMPTY;
        }

        plugin.getLogger().info("[QuestEngine] Party provider: " + provider + " (Active: " + adapter.available() + ")");
    }

    public static boolean enabled() {
        return enabled && adapter.available();
    }

    public static Collection<Player> membersNear(Player p, int radius) {
        if (!enabled() || p == null) {
            return p == null ? Collections.emptyList() : Collections.singletonList(p);
        }

        Collection<Player> members = adapter.members(p);
        // 멤버가 본인 혼자라면 굳이 리스트 새로 만들지 않고 리턴
        if (members.size() <= 1) return members;

        List<Player> out = new ArrayList<>(members.size());
        double limit = radius * radius;
        UUID worldUid = p.getWorld().getUID();

        for (Player m : members) {
            if (m != null && m.isOnline()
                    && m.getWorld().getUID().equals(worldUid)
                    && m.getLocation().distanceSquared(p.getLocation()) <= limit) {
                out.add(m);
            }
        }
        return out;
    }

    // ========================================================================
    // ADAPTERS (Inner Static Classes for Lazy Loading & Clean Structure)
    // ========================================================================

    private static class MythicDungeonsAdapter implements PartyAdapter {
        private final MethodHandle getInst, getMythicPlayer, getMythicParty, getMythicPlayers, getPlayer;
        private final boolean valid;

        MythicDungeonsAdapter() throws Throwable {
            MethodHandles.Lookup lookup = MethodHandles.lookup();
            Class<?> main = Class.forName("net.playavalon.mythicdungeons.MythicDungeons");
            Class<?> mPlayer = Class.forName("net.playavalon.mythicdungeons.player.MythicPlayer");
            Class<?> mParty = Class.forName("net.playavalon.mythicdungeons.player.party.partysystem.MythicParty");

            this.getInst = lookup.unreflect(main.getMethod("inst"));
            this.getMythicPlayer = lookup.unreflect(main.getMethod("getMythicPlayer", Player.class));
            this.getMythicParty = lookup.unreflect(mPlayer.getMethod("getMythicParty"));
            this.getMythicPlayers = lookup.unreflect(mParty.getMethod("getMythicPlayers"));
            this.getPlayer = lookup.unreflect(mPlayer.getMethod("getPlayer"));
            this.valid = true;
        }

        @Override public boolean available() { return valid; }

        @Override
        public Collection<Player> members(Player p) {
            if (p == null) return Collections.emptyList();
            try {
                Object api = getInst.invoke();
                Object mp = getMythicPlayer.invoke(api, p);
                if (mp == null) return Collections.singletonList(p);

                Object party = getMythicParty.invoke(mp);
                if (party == null) return Collections.singletonList(p);

                Collection<?> players = (Collection<?>) getMythicPlayers.invoke(party);
                if (players == null || players.isEmpty()) return Collections.singletonList(p);

                List<Player> list = new ArrayList<>(players.size());
                for (Object wrapper : players) {
                    Player pl = (Player) getPlayer.invoke(wrapper);
                    if (pl != null && pl.isOnline()) list.add(pl);
                }
                return list;
            } catch (Throwable t) {
                return Collections.singletonList(p);
            }
        }
    }

    private static class MMOCoreAdapter implements PartyAdapter {
        private final MethodHandle getPlayerData, getParty, getOnlineMembers, getPlayer;
        private final boolean valid;

        MMOCoreAdapter() throws Throwable {
            MethodHandles.Lookup lookup = MethodHandles.lookup();
            Class<?> dataClass;
            try { dataClass = Class.forName("net.Indyuce.mmocore.api.player.PlayerData"); }
            catch (ClassNotFoundException e) { dataClass = Class.forName("net.Indyuce.mmocore.api.player.MMOPlayerData"); }

            MethodHandle getPD;
            try { getPD = lookup.unreflect(dataClass.getMethod("get", Player.class)); }
            catch (Throwable t) { getPD = lookup.unreflect(dataClass.getMethod("get", UUID.class)); }
            this.getPlayerData = getPD;

            this.getParty = lookup.unreflect(dataClass.getMethod("getParty"));

            Class<?> partyClass;
            try { partyClass = Class.forName("net.Indyuce.mmocore.party.provided.Party"); }
            catch (ClassNotFoundException e) { partyClass = Class.forName("net.Indyuce.mmocore.party.Party"); }

            this.getOnlineMembers = lookup.unreflect(partyClass.getMethod("getOnlineMembers"));

            // MMOCore 멤버 객체에서 getPlayer() 찾기 (가장 첫 번째 멤버 타입 기준)
            // 여기서는 런타임에 동적으로 처리하는 대신 안전하게 Object.class 메소드 탐색은 생략하고
            // 실행 시점에 리플렉션을 최소화하는 방향으로 구성
            this.getPlayer = null; // 런타임 결정
            this.valid = true;
        }

        @Override public boolean available() { return valid; }

        @Override
        public Collection<Player> members(Player p) {
            if (p == null) return Collections.emptyList();
            try {
                // MMOCore.get(p) or get(uuid)
                Object data;
                try { data = getPlayerData.invoke(p); }
                catch (Throwable t) { data = getPlayerData.invoke(p.getUniqueId()); }

                if (data == null) return Collections.singletonList(p);

                Object party = getParty.invoke(data);
                if (party == null) return Collections.singletonList(p);

                Collection<?> members = (Collection<?>) getOnlineMembers.invoke(party);
                if (members == null || members.isEmpty()) return Collections.singletonList(p);

                List<Player> list = new ArrayList<>(members.size());
                for (Object m : members) {
                    // 멤버 객체에서 getPlayer() 호출 (캐싱 없이 단순 invoke가 안전, MMOCore 버전 파편화 때문)
                    try {
                        Method mGetPlayer = m.getClass().getMethod("getPlayer");
                        Player pl = (Player) mGetPlayer.invoke(m);
                        if (pl != null && pl.isOnline()) list.add(pl);
                    } catch (Throwable ignored) {}
                }
                return list;
            } catch (Throwable t) {
                return Collections.singletonList(p);
            }
        }
    }

    private static class PartiesAdapter implements PartyAdapter {
        private final Object apiInstance;
        private final MethodHandle getPartyPlayer, isInParty, getPartyId, getParty, getOnlineMembers, getPlayerUUID;
        private final boolean valid;

        PartiesAdapter() throws Throwable {
            MethodHandles.Lookup lookup = MethodHandles.lookup();
            Class<?> main = Class.forName("com.alessiodp.parties.api.Parties");
            this.apiInstance = main.getMethod("getApi").invoke(null);

            Class<?> api = Class.forName("com.alessiodp.parties.api.interfaces.PartiesAPI");
            Class<?> pp = Class.forName("com.alessiodp.parties.api.interfaces.PartyPlayer");
            Class<?> party = Class.forName("com.alessiodp.parties.api.interfaces.Party");

            this.getPartyPlayer = lookup.unreflect(api.getMethod("getPartyPlayer", UUID.class));
            this.getParty = lookup.unreflect(api.getMethod("getParty", UUID.class));

            this.isInParty = lookup.unreflect(pp.getMethod("isInParty"));
            this.getPartyId = lookup.unreflect(pp.getMethod("getPartyId"));

            this.getOnlineMembers = lookup.unreflect(party.getMethod("getOnlineMembers"));

            MethodHandle uuidGetter = null;
            try { uuidGetter = lookup.unreflect(pp.getMethod("getPlayerUUID")); } catch (Throwable ignored) {}
            this.getPlayerUUID = uuidGetter;

            this.valid = (apiInstance != null);
        }

        @Override public boolean available() { return valid; }

        @Override
        public Collection<Player> members(Player p) {
            if (p == null) return Collections.emptyList();
            try {
                Object pObj = getPartyPlayer.invoke(apiInstance, p.getUniqueId());
                if (pObj == null) return Collections.singletonList(p);

                boolean in = (boolean) isInParty.invoke(pObj);
                if (!in) return Collections.singletonList(p);

                UUID partyId = (UUID) getPartyId.invoke(pObj);
                if (partyId == null) return Collections.singletonList(p);

                Object partyObj = getParty.invoke(apiInstance, partyId);
                if (partyObj == null) return Collections.singletonList(p);

                Collection<?> members = (Collection<?>) getOnlineMembers.invoke(partyObj);
                if (members == null || members.isEmpty()) return Collections.singletonList(p);

                List<Player> list = new ArrayList<>(members.size());
                for (Object m : members) {
                    if (m instanceof UUID) {
                        Player pl = Bukkit.getPlayer((UUID) m);
                        if (pl != null) list.add(pl);
                    } else if (getPlayerUUID != null) {
                        try {
                            UUID id = (UUID) getPlayerUUID.invoke(m);
                            Player pl = Bukkit.getPlayer(id);
                            if (pl != null) list.add(pl);
                        } catch (Throwable ignored) {}
                    }
                }
                return list.isEmpty() ? Collections.singletonList(p) : list;
            } catch (Throwable t) {
                return Collections.singletonList(p);
            }
        }
    }
}