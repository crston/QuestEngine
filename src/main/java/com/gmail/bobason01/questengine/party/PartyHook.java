package com.gmail.bobason01.questengine.party;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.*;

public final class PartyHook {

    private static PartyAdapter adapter = PartyAdapter.EMPTY;
    private static boolean enabled = false;

    private PartyHook() {}

    private static Method mmo_get;
    private static Method mmo_getParty;
    private static Method mmo_getOnline;

    private static Object parties_api;

    public static void init(Plugin plugin, FileConfiguration cfg) {
        enabled = cfg.getBoolean("party.enabled", true);
        if (!enabled) {
            adapter = PartyAdapter.EMPTY;
            return;
        }

        String provider = cfg.getString("party.provider", "auto");
        if (provider == null) {
            provider = "auto";
        }
        provider = provider.toLowerCase(Locale.ROOT);

        if ("auto".equals(provider)) {
            if (Bukkit.getPluginManager().isPluginEnabled("MythicDungeons")) {
                provider = "mythicdungeons";
            } else if (Bukkit.getPluginManager().isPluginEnabled("MMOCore")) {
                provider = "mmocore";
            } else if (Bukkit.getPluginManager().isPluginEnabled("Parties")) {
                provider = "parties";
            } else {
                provider = "none";
            }
        }

        switch (provider) {
            case "mythicdungeons":
                adapter = mythicDungeons();
                break;
            case "mmocore":
                adapter = mmoCore();
                break;
            case "parties":
                adapter = parties();
                break;
            default:
                adapter = PartyAdapter.EMPTY;
                break;
        }

        plugin.getLogger().info("[QuestEngine] Party provider " + provider + " available=" + adapter.available());
    }

    public static boolean enabled() {
        return enabled && adapter.available();
    }

    public static Collection<Player> membersNear(Player p, int radius) {
        if (!enabled() || p == null) {
            return p == null ? Collections.emptyList() : Collections.singletonList(p);
        }
        List<Player> out = new ArrayList<>(8);
        double limit = radius * (double) radius;
        for (Player m : adapter.members(p)) {
            if (m == null || !m.isOnline()) {
                continue;
            }
            if (m.getWorld() != p.getWorld()) {
                continue;
            }
            if (m.getLocation().distanceSquared(p.getLocation()) <= limit) {
                out.add(m);
            }
        }
        if (out.isEmpty() && p != null) {
            out.add(p);
        }
        return out;
    }

    private static PartyAdapter mythicDungeons() {
        try {
            Class<?> mythicDungeonsCls = Class.forName("net.playavalon.mythicdungeons.MythicDungeons");
            Class<?> mythicPlayerCls = Class.forName("net.playavalon.mythicdungeons.player.MythicPlayer");
            Class<?> mythicPartyCls = Class.forName("net.playavalon.mythicdungeons.player.party.partysystem.MythicParty");

            Method inst = mythicDungeonsCls.getMethod("inst");
            Method getMythicPlayer = mythicDungeonsCls.getMethod("getMythicPlayer", org.bukkit.entity.Player.class);
            Method getMythicParty = mythicPlayerCls.getMethod("getMythicParty");
            Method getMythicPlayers = mythicPartyCls.getMethod("getMythicPlayers");
            Method getPlayer = mythicPlayerCls.getMethod("getPlayer");

            return new PartyAdapter() {
                @Override
                public boolean available() {
                    return true;
                }

                @SuppressWarnings("unchecked")
                @Override
                public Collection<Player> members(Player p) {
                    if (p == null) {
                        return Collections.emptyList();
                    }
                    try {
                        Object api = inst.invoke(null);
                        Object mythicPlayer = getMythicPlayer.invoke(api, p);
                        if (mythicPlayer == null) {
                            return Collections.singletonList(p);
                        }

                        Object party = getMythicParty.invoke(mythicPlayer);
                        if (party == null) {
                            return Collections.singletonList(p);
                        }

                        Collection<Object> mythicPlayers = (Collection<Object>) getMythicPlayers.invoke(party);
                        if (mythicPlayers == null || mythicPlayers.isEmpty()) {
                            return Collections.singletonList(p);
                        }

                        List<Player> list = new ArrayList<>(mythicPlayers.size());
                        for (Object mp : mythicPlayers) {
                            Player pl = (Player) getPlayer.invoke(mp);
                            if (pl != null && pl.isOnline()) {
                                list.add(pl);
                            }
                        }
                        return list.isEmpty() ? Collections.singletonList(p) : list;
                    } catch (Throwable ex) {
                        ex.printStackTrace();
                        return Collections.singletonList(p);
                    }
                }
            };
        } catch (Throwable t) {
            Bukkit.getLogger().warning("[QuestEngine] MythicDungeons hook failed: " + t.getMessage());
            return PartyAdapter.EMPTY;
        }
    }

    private static PartyAdapter mmoCore() {
        try {
            Class<?> playerDataCls;
            mmo_get = null;

            try {
                playerDataCls = Class.forName("net.Indyuce.mmocore.api.player.PlayerData");
            } catch (ClassNotFoundException e) {
                playerDataCls = Class.forName("net.Indyuce.mmocore.api.player.MMOPlayerData");
            }

            try {
                mmo_get = playerDataCls.getMethod("get", Player.class);
            } catch (Throwable ignored) {
            }
            if (mmo_get == null) {
                try {
                    mmo_get = playerDataCls.getMethod("get", UUID.class);
                } catch (Throwable ignored) {
                }
            }
            if (mmo_get == null) {
                try {
                    mmo_get = playerDataCls.getMethod("get", org.bukkit.OfflinePlayer.class);
                } catch (Throwable ignored) {
                }
            }

            if (mmo_get == null) {
                Bukkit.getLogger().info("[QuestEngine] MMOCore hook skipped, get() not found");
                return PartyAdapter.EMPTY;
            }

            mmo_getParty = playerDataCls.getMethod("getParty");

            Class<?> partyCls;
            try {
                partyCls = Class.forName("net.Indyuce.mmocore.party.provided.Party");
            } catch (ClassNotFoundException e) {
                partyCls = Class.forName("net.Indyuce.mmocore.party.Party");
            }
            mmo_getOnline = partyCls.getMethod("getOnlineMembers");

            Bukkit.getLogger().info("[QuestEngine] MMOCore hook successful");

            return new PartyAdapter() {
                @Override
                public boolean available() {
                    return true;
                }

                @SuppressWarnings("unchecked")
                @Override
                public Collection<Player> members(Player p) {
                    if (p == null) {
                        return Collections.emptyList();
                    }
                    try {
                        Object data = null;
                        try {
                            data = mmo_get.invoke(null, p);
                        } catch (Throwable ignored) {
                        }
                        if (data == null) {
                            try {
                                data = mmo_get.invoke(null, p.getUniqueId());
                            } catch (Throwable ignored) {
                            }
                        }
                        if (data == null) {
                            return Collections.singletonList(p);
                        }

                        Object party = mmo_getParty.invoke(data);
                        if (party == null) {
                            return Collections.singletonList(p);
                        }

                        Collection<?> members = (Collection<?>) mmo_getOnline.invoke(party);
                        if (members == null || members.isEmpty()) {
                            return Collections.singletonList(p);
                        }

                        List<Player> list = new ArrayList<>(members.size());
                        for (Object o : members) {
                            try {
                                Method getPlayer = o.getClass().getMethod("getPlayer");
                                Player pl = (Player) getPlayer.invoke(o);
                                if (pl != null && pl.isOnline()) {
                                    list.add(pl);
                                }
                            } catch (Throwable ignored) {
                            }
                        }
                        return list.isEmpty() ? Collections.singletonList(p) : list;
                    } catch (Throwable ignored) {
                        return Collections.singletonList(p);
                    }
                }
            };

        } catch (Throwable ignored) {
            Bukkit.getLogger().info("[QuestEngine] MMOCore not compatible, skipping hook");
            return PartyAdapter.EMPTY;
        }
    }

    private static PartyAdapter parties() {
        try {
            Class<?> partiesCls = Class.forName("com.alessiodp.parties.api.Parties");
            Method getApiMethod = partiesCls.getMethod("getApi");
            parties_api = getApiMethod.invoke(null);

            Class<?> apiCls = Class.forName("com.alessiodp.parties.api.interfaces.PartiesAPI");
            Class<?> partyPlayerCls = Class.forName("com.alessiodp.parties.api.interfaces.PartyPlayer");
            Class<?> partyCls = Class.forName("com.alessiodp.parties.api.interfaces.Party");

            Method api_getPartyPlayer = apiCls.getMethod("getPartyPlayer", UUID.class);
            Method api_getPartyById = apiCls.getMethod("getParty", UUID.class);

            Method pp_isInParty = partyPlayerCls.getMethod("isInParty");
            Method pp_getPartyId = partyPlayerCls.getMethod("getPartyId");

            Method party_getOnlineMembers = partyCls.getMethod("getOnlineMembers");

            Method pp_getPlayerUUID = null;
            try {
                pp_getPlayerUUID = partyPlayerCls.getMethod("getPlayerUUID");
            } catch (Throwable ignored) {
            }

            Method pp_getPlayer = null;
            try {
                pp_getPlayer = partyPlayerCls.getMethod("getPlayer");
            } catch (Throwable ignored) {
            }

            Method finalPpGetPlayerUUID = pp_getPlayerUUID;
            Method finalPpGetPlayer = pp_getPlayer;

            return new PartyAdapter() {
                @Override
                public boolean available() {
                    return parties_api != null;
                }

                @SuppressWarnings("unchecked")
                @Override
                public Collection<Player> members(Player p) {
                    if (p == null) {
                        return Collections.emptyList();
                    }
                    try {
                        Object pp = api_getPartyPlayer.invoke(parties_api, p.getUniqueId());
                        if (pp == null) {
                            return Collections.singletonList(p);
                        }

                        boolean inParty = false;
                        try {
                            Object v = pp_isInParty.invoke(pp);
                            if (v instanceof Boolean) {
                                inParty = (Boolean) v;
                            }
                        } catch (Throwable ignored) {
                        }
                        if (!inParty) {
                            return Collections.singletonList(p);
                        }

                        UUID partyId = (UUID) pp_getPartyId.invoke(pp);
                        if (partyId == null) {
                            return Collections.singletonList(p);
                        }

                        Object party = api_getPartyById.invoke(parties_api, partyId);
                        if (party == null) {
                            return Collections.singletonList(p);
                        }

                        Collection<?> raw = (Collection<?>) party_getOnlineMembers.invoke(party);
                        if (raw == null || raw.isEmpty()) {
                            return Collections.singletonList(p);
                        }

                        List<Player> list = new ArrayList<>(raw.size());

                        Object first = raw.iterator().next();

                        if (first instanceof UUID) {
                            for (Object o : raw) {
                                UUID id = (UUID) o;
                                Player pl = Bukkit.getPlayer(id);
                                if (pl != null && pl.isOnline()) {
                                    list.add(pl);
                                }
                            }
                        } else if (partyPlayerCls.isInstance(first)) {
                            for (Object o : raw) {
                                UUID id = null;

                                if (finalPpGetPlayerUUID != null) {
                                    try {
                                        Object res = finalPpGetPlayerUUID.invoke(o);
                                        if (res instanceof UUID) {
                                            id = (UUID) res;
                                        }
                                    } catch (Throwable ignored) {
                                    }
                                }

                                if (id == null && finalPpGetPlayer != null) {
                                    try {
                                        Object obj = finalPpGetPlayer.invoke(o);
                                        if (obj instanceof Player) {
                                            Player pl = (Player) obj;
                                            if (pl.isOnline()) {
                                                list.add(pl);
                                            }
                                            continue;
                                        }
                                    } catch (Throwable ignored) {
                                    }
                                }

                                if (id != null) {
                                    Player pl = Bukkit.getPlayer(id);
                                    if (pl != null && pl.isOnline()) {
                                        list.add(pl);
                                    }
                                }
                            }
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
