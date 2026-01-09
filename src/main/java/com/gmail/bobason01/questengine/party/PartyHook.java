package com.gmail.bobason01.questengine.party;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.util.*;

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
            if (t instanceof NoClassDefFoundError && t.getMessage().contains("Citizens")) {
                plugin.getLogger().warning("[QuestEngine] MythicDungeons hook failed: Citizens plugin is missing (Required by MD).");
            } else {
                plugin.getLogger().warning("[QuestEngine] Failed to hook party provider '" + provider + "': " + t.getMessage());
            }
            adapter = PartyAdapter.EMPTY;
        }

        plugin.getLogger().info("[QuestEngine] Party provider: " + provider + " (Active: " + adapter.available() + ")");
    }

    public static boolean enabled() { return enabled && adapter.available(); }
    public static boolean isInParty(Player p) { return enabled() && adapter.isInParty(p); }

    public static Collection<Player> membersNear(Player p, int radius) {
        if (!enabled() || p == null) return p == null ? Collections.emptyList() : Collections.singletonList(p);
        Collection<Player> members = adapter.members(p);
        if (members.size() <= 1) return members;

        List<Player> out = new ArrayList<>(members.size());
        double limit = radius * radius;
        UUID worldUid = p.getWorld().getUID();

        for (Player m : members) {
            if (m != null && m.isOnline() && m.getWorld().getUID().equals(worldUid)
                    && m.getLocation().distanceSquared(p.getLocation()) <= limit) {
                out.add(m);
            }
        }
        return out;
    }

    // ========================================================================
    // ADAPTERS
    // ========================================================================

    private static class MythicDungeonsAdapter implements PartyAdapter {
        private boolean initialized = false;
        private boolean broken = false;
        private Object partyManager;
        private MethodHandle getParty, getPlayers;

        MythicDungeonsAdapter() {}

        @Override public boolean available() { return !broken; }

        private void lazyInit() {
            if (initialized || broken) return;
            initialized = true;

            try {
                MethodHandles.Lookup lookup = MethodHandles.lookup();
                Plugin plugin = Bukkit.getPluginManager().getPlugin("MythicDungeons");
                if (plugin == null) throw new RuntimeException("MythicDungeons not found");

                Object inst = plugin;
                Class<?> mainClass = plugin.getClass();

                Object pm = null;
                try {
                    Method m = mainClass.getMethod("getPartyManager");
                    pm = m.invoke(inst);
                } catch (Throwable t) {
                    java.lang.reflect.Field f = mainClass.getDeclaredField("partyManager");
                    f.setAccessible(true);
                    pm = f.get(inst);
                }
                this.partyManager = pm;
                if (this.partyManager == null) throw new RuntimeException("PartyManager is null");

                Method getPartyMethod = this.partyManager.getClass().getMethod("getParty", Player.class);
                this.getParty = lookup.unreflect(getPartyMethod);

                Class<?> partyClass = getPartyMethod.getReturnType();
                Method getPlayersMethod = partyClass.getMethod("getPlayers"); // getPlayers uses List<Player>
                this.getPlayers = lookup.unreflect(getPlayersMethod);

            } catch (Throwable t) {
                broken = true;
                // Silent catch here to avoid console spam after initial warning
            }
        }

        @Override
        public Collection<Player> members(Player p) {
            if (broken) return Collections.singletonList(p);
            if (!initialized) lazyInit();
            if (broken) return Collections.singletonList(p);

            try {
                Object party = getParty.invoke(partyManager, p);
                if (party == null) return Collections.singletonList(p);

                Collection<?> players = (Collection<?>) getPlayers.invoke(party);
                if (players == null || players.isEmpty()) return Collections.singletonList(p);

                List<Player> list = new ArrayList<>();
                for (Object obj : players) {
                    if (obj instanceof Player) {
                        Player member = (Player) obj;
                        if (member.isOnline()) list.add(member);
                    }
                }
                return list.isEmpty() ? Collections.singletonList(p) : list;

            } catch (Throwable t) {
                return Collections.singletonList(p);
            }
        }
    }

    private static class MMOCoreAdapter implements PartyAdapter {
        private final MethodHandle getPlayerData, getParty, getOnlineMembers;
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
            this.valid = true;
        }
        @Override public boolean available() { return valid; }
        @Override public Collection<Player> members(Player p) {
            if (p == null) return Collections.emptyList();
            try {
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
                    try {
                        Method mGetPlayer = m.getClass().getMethod("getPlayer");
                        Player pl = (Player) mGetPlayer.invoke(m);
                        if (pl != null && pl.isOnline()) list.add(pl);
                    } catch (Throwable ignored) {}
                }
                return list;
            } catch (Throwable t) { return Collections.singletonList(p); }
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
        @Override public Collection<Player> members(Player p) {
            if (p == null) return Collections.emptyList();
            try {
                Object pObj = getPartyPlayer.invoke(apiInstance, p.getUniqueId());
                if (pObj == null) return Collections.singletonList(p);
                if (!(boolean) isInParty.invoke(pObj)) return Collections.singletonList(p);
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
            } catch (Throwable t) { return Collections.singletonList(p); }
        }
    }
}