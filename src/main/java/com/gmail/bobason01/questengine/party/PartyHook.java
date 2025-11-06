package com.gmail.bobason01.questengine.party;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.*;

/**
 * PartyHook
 * - MMOCore, MythicDungeons, Parties 플러그인 자동 감지 및 통합
 * - 리플렉션 호출 전부 캐시화 (init() 1회만)
 * - GC-free, main-thread 성능 중심 설계
 */
public final class PartyHook {

    private static PartyAdapter adapter = PartyAdapter.EMPTY;
    private static boolean enabled = false;

    private PartyHook() {}

    // --- 리플렉션 캐시용 ---
    private static Method mythic_getParty, mythic_getMembers;
    private static Method mmo_get, mmo_getParty, mmo_getOnline;
    private static Method parties_getApi, parties_getPlayer, parties_getPid, parties_getOnline;
    private static Object parties_api;

    public static void init(Plugin plugin, FileConfiguration cfg) {
        enabled = cfg.getBoolean("party.enabled", true);
        if (!enabled) {
            adapter = PartyAdapter.EMPTY;
            return;
        }

        String provider = cfg.getString("party.provider", "auto").toLowerCase(Locale.ROOT);
        if ("auto".equals(provider)) {
            if (Bukkit.getPluginManager().isPluginEnabled("MythicDungeons")) provider = "mythicdungeons";
            else if (Bukkit.getPluginManager().isPluginEnabled("MMOCore")) provider = "mmocore";
            else if (Bukkit.getPluginManager().isPluginEnabled("Parties")) provider = "parties";
            else provider = "none";
        }

        switch (provider) {
            case "mythicdungeons" -> adapter = mythicDungeons();
            case "mmocore" -> adapter = mmoCore();
            case "parties" -> adapter = parties();
            default -> adapter = PartyAdapter.EMPTY;
        }

        plugin.getLogger().info("[QuestEngine] Party provider " + provider + " available=" + adapter.available());
    }

    public static boolean enabled() {
        return enabled && adapter.available();
    }

    /**
     * 특정 플레이어 기준 반경 내 파티 멤버 탐색
     */
    public static Collection<Player> membersNear(Player p, int radius) {
        if (!enabled()) return Collections.singletonList(p);
        List<Player> out = new ArrayList<>(8);
        double limit = radius * (double) radius;
        for (Player m : adapter.members(p)) {
            if (m == null || !m.isOnline()) continue;
            if (m.getWorld() != p.getWorld()) continue;
            if (m.getLocation().distanceSquared(p.getLocation()) <= limit)
                out.add(m);
        }
        if (out.isEmpty()) out.add(p);
        return out;
    }

    // ==============================
    // MythicDungeons
    // ==============================
    private static PartyAdapter mythicDungeons() {
        try {
            Class<?> api = Class.forName("net.elseland.xikage.mythicdungeons.api.MythicDungeonsAPI");
            mythic_getParty = api.getMethod("getParty", UUID.class);
            Class<?> partyCls = Class.forName("net.elseland.xikage.mythicdungeons.api.model.Party");
            mythic_getMembers = partyCls.getMethod("getOnlineMembers");
            return new PartyAdapter() {
                @Override
                public boolean available() { return true; }

                @SuppressWarnings("unchecked")
                @Override
                public Collection<Player> members(Player p) {
                    try {
                        Object party = mythic_getParty.invoke(null, p.getUniqueId());
                        if (party == null) return Collections.singletonList(p);
                        Collection<UUID> uuids = (Collection<UUID>) mythic_getMembers.invoke(party);
                        List<Player> list = new ArrayList<>(uuids.size());
                        for (UUID id : uuids) {
                            Player pl = Bukkit.getPlayer(id);
                            if (pl != null && pl.isOnline()) list.add(pl);
                        }
                        return list.isEmpty() ? Collections.singletonList(p) : list;
                    } catch (Throwable t) {
                        return Collections.singletonList(p);
                    }
                }
            };
        } catch (Throwable t) {
            Bukkit.getLogger().warning("[QuestEngine] MythicDungeons hook failed: " + t.getMessage());
            return PartyAdapter.EMPTY;
        }
    }

    // ==============================
    // MMOCore
    // ==============================
    private static PartyAdapter mmoCore() {
        try {
            Class<?> dataCls = Class.forName("net.Indyuce.mmocore.api.player.MMOPlayerData");
            mmo_get = dataCls.getMethod("get", UUID.class);
            mmo_getParty = dataCls.getMethod("getParty");
            Class<?> partyCls = Class.forName("net.Indyuce.mmocore.party.Party");
            mmo_getOnline = partyCls.getMethod("getOnlineMembers");

            return new PartyAdapter() {
                @Override
                public boolean available() { return true; }

                @SuppressWarnings("unchecked")
                @Override
                public Collection<Player> members(Player p) {
                    try {
                        Object data = mmo_get.invoke(null, p.getUniqueId());
                        if (data == null) return Collections.singletonList(p);
                        Object party = mmo_getParty.invoke(data);
                        if (party == null) return Collections.singletonList(p);
                        Collection<Player> members = (Collection<Player>) mmo_getOnline.invoke(party);
                        return (members == null || members.isEmpty()) ? Collections.singletonList(p) : members;
                    } catch (Throwable t) {
                        return Collections.singletonList(p);
                    }
                }
            };
        } catch (Throwable t) {
            Bukkit.getLogger().warning("[QuestEngine] MMOCore hook failed: " + t.getMessage());
            return PartyAdapter.EMPTY;
        }
    }

    // ==============================
    // Parties
    // ==============================
    private static PartyAdapter parties() {
        try {
            Class<?> api = Class.forName("com.alessiodp.parties.api.Parties");
            parties_getApi = api.getMethod("getApi");
            parties_api = parties_getApi.invoke(null);

            Class<?> apiCls = Class.forName("com.alessiodp.parties.api.interfaces.PartiesAPI");
            parties_getPlayer = apiCls.getMethod("getPartyPlayer", UUID.class);
            Class<?> partyPlayerCls = Class.forName("com.alessiodp.parties.api.interfaces.PartyPlayer");
            parties_getPid = partyPlayerCls.getMethod("getPartyId");
            parties_getOnline = apiCls.getMethod("getOnlineMembers", UUID.class);

            return new PartyAdapter() {
                @Override
                public boolean available() { return true; }

                @SuppressWarnings("unchecked")
                @Override
                public Collection<Player> members(Player p) {
                    try {
                        Object pp = parties_getPlayer.invoke(parties_api, p.getUniqueId());
                        if (pp == null) return Collections.singletonList(p);
                        UUID pid = (UUID) parties_getPid.invoke(pp);
                        if (pid == null) return Collections.singletonList(p);
                        Collection<UUID> uuids = (Collection<UUID>) parties_getOnline.invoke(parties_api, pid);
                        List<Player> list = new ArrayList<>(uuids.size());
                        for (UUID id : uuids) {
                            Player m = Bukkit.getPlayer(id);
                            if (m != null && m.isOnline()) list.add(m);
                        }
                        return list.isEmpty() ? Collections.singletonList(p) : list;
                    } catch (Throwable t) {
                        return Collections.singletonList(p);
                    }
                }
            };
        } catch (Throwable t) {
            Bukkit.getLogger().warning("[QuestEngine] Parties hook failed: " + t.getMessage());
            return PartyAdapter.EMPTY;
        }
    }
}
