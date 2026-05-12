package com.gmail.bobason01.questengine.action;

import com.gmail.bobason01.questengine.quest.QuestDef;
import com.gmail.bobason01.questengine.util.Msg;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ActionExecutor {

    private final Plugin plugin;
    private final Msg msg;
    private final boolean papi;
    private final boolean mmo;
    private final boolean ia;

    private static MethodHandle mmoGetItemMH;
    private static MethodHandle mmoGetTypeMH;
    private static Object mmoInstance;

    private static MethodHandle iaGetInstanceMH;
    private static MethodHandle iaGetItemStackMH;

    private final Map<String, List<ActionEntry>> actionCache = new ConcurrentHashMap<>();

    private static final Pattern PARAM_PATTERN = Pattern.compile("([a-zA-Z]+)=('([^']*)'|\"([^\"]*)\"|([^\\s,]+))");

    private enum ActionType { MESSAGE, COMMAND, ITEM, TITLE, SOUND, PARTICLE, POTION, UNKNOWN }
    private enum Target { SELF, SERVER }

    private static final class ActionEntry {
        final ActionType type;
        final Map<String, String> params;
        final long delay;
        final Target target;

        ActionEntry(ActionType type, Map<String, String> params, long delay, Target target) {
            this.type = type;
            this.params = params;
            this.delay = delay;
            this.target = target;
        }
    }

    public ActionExecutor(Plugin plugin, Msg msg) {
        this.plugin = plugin;
        this.msg = msg;
        this.papi = Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI");
        this.mmo = Bukkit.getPluginManager().isPluginEnabled("MMOItems");
        this.ia = Bukkit.getPluginManager().isPluginEnabled("ItemsAdder");
        initHooks();
    }

    private void initHooks() {
        MethodHandles.Lookup lookup = MethodHandles.lookup();
        try {
            if (mmo) {
                Class<?> mmoClass = Class.forName("net.Indyuce.mmoitems.MMOItems");
                Class<?> typeClass = Class.forName("net.Indyuce.mmoitems.api.Type");
                try {
                    mmoInstance = mmoClass.getField("plugin").get(null);
                } catch (Throwable t) {
                    mmoInstance = mmoClass.getMethod("getInstance").invoke(null);
                }
                Method getType = typeClass.getMethod("get", String.class);
                mmoGetTypeMH = lookup.unreflect(getType);
                Method getItem = mmoClass.getMethod("getItem", typeClass, String.class);
                mmoGetItemMH = lookup.unreflect(getItem).bindTo(mmoInstance);
            }
        } catch (Throwable t) {
        }

        try {
            if (ia) {
                Class<?> csClass = Class.forName("dev.lone.itemsadder.api.CustomStack");
                Method getInstance = csClass.getMethod("getInstance", String.class);
                iaGetInstanceMH = lookup.unreflect(getInstance);
                Method getItemStack = csClass.getMethod("getItemStack");
                iaGetItemStackMH = lookup.unreflect(getItemStack);
            }
        } catch (Throwable t) {
        }
    }

    public void runAll(QuestDef q, String type, Player p) {
        if (q == null || q.actions == null || p == null) return;
        String cacheKey = q.id + type.toLowerCase(Locale.ROOT);
        List<ActionEntry> entries = actionCache.computeIfAbsent(cacheKey, k -> compileActions(q, type));
        if (entries.isEmpty()) return;
        executeEntries(entries, q, p);
    }

    public void run(QuestDef q, String type, Player p) {
        runAll(q, type, p);
    }

    private List<ActionEntry> compileActions(QuestDef q, String type) {
        List<String> rawList = null;
        for (Map.Entry<String, List<String>> e : q.actions.entrySet()) {
            if (e.getKey().equalsIgnoreCase(type)) {
                rawList = e.getValue();
                break;
            }
        }

        if (rawList == null || rawList.isEmpty()) return Collections.emptyList();

        List<ActionEntry> compiled = new ArrayList<>(rawList.size());
        long currentDelay = 0;

        for (String line : rawList) {
            if (line == null || line.isBlank()) continue;
            String s = line.trim();

            Target target = Target.SELF;
            String lowerS = s.toLowerCase(Locale.ROOT);

            if (lowerS.endsWith("server") || lowerS.endsWith("@server")) {
                target = Target.SERVER;
                s = s.replaceAll("(?i)\\s*server$|\\s*@server$", "").trim();
            } else if (lowerS.endsWith("@self")) {
                target = Target.SELF;
                s = s.replaceAll("(?i)\\s*@self$", "").trim();
            }

            if (s.toLowerCase(Locale.ROOT).startsWith("delay ")) {
                currentDelay += parseDelay(s);
                continue;
            }

            ActionEntry entry = parseEntry(s, currentDelay, target);
            if (entry != null) {
                compiled.add(entry);
            }
        }
        return compiled;
    }

    private ActionEntry parseEntry(String line, long delay, Target target) {
        int braceStart = line.indexOf('{');
        int braceEnd = line.lastIndexOf('}');

        if (braceStart == -1 || braceEnd == -1) return null;

        String key = line.substring(0, braceStart).toLowerCase(Locale.ROOT).trim();
        String body = line.substring(braceStart + 1, braceEnd);

        ActionType type = ActionType.UNKNOWN;
        if (key.equals("msg") || key.equals("message")) type = ActionType.MESSAGE;
        else if (key.equals("cmd") || key.equals("command")) type = ActionType.COMMAND;
        else if (key.equals("item")) type = ActionType.ITEM;
        else if (key.equals("title")) type = ActionType.TITLE;
        else if (key.equals("sound")) type = ActionType.SOUND;
        else if (key.equals("particle")) type = ActionType.PARTICLE;
        else if (key.equals("potion")) type = ActionType.POTION;

        if (type == ActionType.UNKNOWN) return null;

        Map<String, String> params = new HashMap<>();
        Matcher m = PARAM_PATTERN.matcher(body);
        while (m.find()) {
            String paramName = m.group(1).toLowerCase(Locale.ROOT);
            String paramValue = m.group(3) != null ? m.group(3) : m.group(4) != null ? m.group(4) : m.group(5);
            params.put(paramName, paramValue);
        }

        return new ActionEntry(type, params, delay, target);
    }

    private void executeEntries(List<ActionEntry> list, QuestDef q, Player p) {
        if (list.isEmpty()) return;

        int index = 0;
        while (index < list.size()) {
            ActionEntry e = list.get(index);
            if (e.delay > 0) break;
            executeOne(e, q, p);
            index++;
        }

        if (index < list.size()) {
            scheduleNext(list, index, 0, q, p);
        }
    }

    private void scheduleNext(List<ActionEntry> list, int index, long elapsed, QuestDef q, Player p) {
        if (index >= list.size()) return;

        ActionEntry next = list.get(index);
        long wait = Math.max(1L, next.delay - elapsed);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!p.isOnline()) return;

            int i = index;
            long currentTargetDelay = next.delay;

            while (i < list.size()) {
                ActionEntry e = list.get(i);
                if (e.delay > currentTargetDelay) break;
                executeOne(e, q, p);
                i++;
            }

            if (i < list.size()) {
                scheduleNext(list, i, currentTargetDelay, q, p);
            }
        }, wait);
    }

    private void executeOne(ActionEntry e, QuestDef q, Player p) {
        switch (e.type) {
            case MESSAGE -> {
                String text = e.params.getOrDefault("m", e.params.getOrDefault("t", e.params.get("text")));
                text = replace(p, text, q);
                if (text != null && !text.isEmpty()) {
                    p.sendMessage(ChatColor.translateAlternateColorCodes('&', text));
                }
            }
            case COMMAND -> {
                String cmd = e.params.getOrDefault("c", e.params.get("cmd"));
                cmd = replace(p, cmd, q);
                if (cmd != null && !cmd.isEmpty()) {
                    if (e.target == Target.SERVER) {
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
                    } else {
                        p.performCommand(cmd);
                    }
                }
            }
            case ITEM -> {
                String id = e.params.getOrDefault("i", e.params.get("id"));
                int amount = parseInt(e.params.getOrDefault("a", e.params.get("amt")), 1);
                ItemStack item = resolveItem(id, amount);
                if (item != null) {
                    HashMap<Integer, ItemStack> left = p.getInventory().addItem(item);
                    if (!left.isEmpty()) {
                        for (ItemStack drop : left.values()) {
                            p.getWorld().dropItem(p.getLocation(), drop);
                        }
                    }
                }
            }
            case TITLE -> {
                String title = replace(p, e.params.getOrDefault("t", e.params.getOrDefault("title", "")), q);
                String subtitle = replace(p, e.params.getOrDefault("s", e.params.getOrDefault("subtitle", "")), q);
                title = ChatColor.translateAlternateColorCodes('&', title);
                subtitle = ChatColor.translateAlternateColorCodes('&', subtitle);

                int in = parseInt(e.params.get("in"), 10);
                int stay = parseInt(e.params.get("stay"), 70);
                int out = parseInt(e.params.get("out"), 20);

                p.sendTitle(title, subtitle, in, stay, out);
            }
            case SOUND -> {
                String soundName = e.params.getOrDefault("s", e.params.get("sound"));
                if (soundName == null || soundName.isEmpty()) return;
                float vol = parseFloat(e.params.get("v"), 1.0f);
                float pitch = parseFloat(e.params.get("p"), 1.0f);
                try {
                    Sound sound = Sound.valueOf(soundName.toUpperCase(Locale.ROOT));
                    p.playSound(p.getLocation(), sound, vol, pitch);
                } catch (Exception ex) {
                    p.playSound(p.getLocation(), soundName, vol, pitch);
                }
            }
            case PARTICLE -> {
                String pName = e.params.getOrDefault("p", e.params.get("particle"));
                if (pName == null || pName.isEmpty()) return;
                try {
                    Particle particle = Particle.valueOf(pName.toUpperCase(Locale.ROOT));
                    int count = parseInt(e.params.get("a"), 10);
                    p.spawnParticle(particle, p.getLocation().add(0, 1, 0), count, 0.5, 0.5, 0.5, 0.01);
                } catch (Exception ex) {
                }
            }
            case POTION -> {
                String typeStr = e.params.getOrDefault("t", e.params.get("type"));
                if (typeStr == null || typeStr.isEmpty()) return;
                try {
                    PotionEffectType pType = PotionEffectType.getByName(typeStr.toUpperCase(Locale.ROOT));
                    if (pType != null) {
                        int duration = parseInt(e.params.get("d"), 100);
                        int level = parseInt(e.params.get("l"), 1) - 1;
                        p.addPotionEffect(new PotionEffect(pType, duration, Math.max(0, level)));
                    }
                } catch (Exception ex) {
                }
            }
        }
    }

    private String replace(Player p, String s, QuestDef q) {
        if (s == null) return "";
        String t = s.replace("%player%", p.getName())
                .replace("%player_name%", p.getName())
                .replace("player", p.getName())
                .replace("%questname%", q.name)
                .replace("questname", q.name);

        if (papi) {
            return PlaceholderAPI.setPlaceholders(p, t);
        }
        return t;
    }

    private ItemStack resolveItem(String id, int amount) {
        if (id == null || id.isEmpty()) return null;
        if (mmo && mmoGetItemMH != null && id.contains(":")) {
            try {
                String[] split = id.split(":");
                String typeStr = split[0].toUpperCase(Locale.ROOT);
                String itemId = split[1].toUpperCase(Locale.ROOT);
                Object typeObj = mmoGetTypeMH.invoke(typeStr);
                if (typeObj != null) {
                    Object result = mmoGetItemMH.invoke(typeObj, itemId);
                    if (result instanceof ItemStack is) {
                        is.setAmount(amount);
                        return is;
                    }
                }
            } catch (Throwable ignored) {}
        }
        if (ia && iaGetInstanceMH != null && id.contains(":")) {
            try {
                Object customStack = iaGetInstanceMH.invoke(id);
                if (customStack != null) {
                    Object result = iaGetItemStackMH.invoke(customStack);
                    if (result instanceof ItemStack is) {
                        is.setAmount(amount);
                        return is;
                    }
                }
            } catch (Throwable ignored) {}
        }
        try {
            Material mat = Material.matchMaterial(id.toUpperCase(Locale.ROOT));
            if (mat != null) {
                return new ItemStack(mat, amount);
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private long parseDelay(String s) {
        try {
            String[] parts = s.split(" ");
            if (parts.length < 2) return 0;
            String val = parts[1].toLowerCase(Locale.ROOT);
            if (val.endsWith("t")) {
                return Long.parseLong(val.replace("t", ""));
            } else {
                return Long.parseLong(val) * 20L;
            }
        } catch (Throwable t) {
            return 0;
        }
    }

    private int parseInt(String s, int def) {
        if (s == null) return def;
        try { return Integer.parseInt(s); } catch (Exception e) { return def; }
    }

    private float parseFloat(String s, float def) {
        if (s == null) return def;
        try { return Float.parseFloat(s); } catch (Exception e) { return def; }
    }
}