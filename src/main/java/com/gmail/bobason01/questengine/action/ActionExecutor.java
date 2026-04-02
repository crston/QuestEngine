package com.gmail.bobason01.questengine.action;

import com.gmail.bobason01.questengine.quest.QuestDef;
import com.gmail.bobason01.questengine.util.Msg;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

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

    // --- Reflection Handles (MMOItems) ---
    private static MethodHandle mmoGetItemMH;
    private static MethodHandle mmoGetTypeMH;
    private static Object mmoInstance; // MMOItems.plugin

    // --- Reflection Handles (ItemsAdder) ---
    private static MethodHandle iaGetInstanceMH; // CustomStack.getInstance
    private static MethodHandle iaGetItemStackMH; // CustomStack.getItemStack

    // --- Action Cache ---
    // 키: "quest_id:action_type" (ex: "tutorial_quest:start")
    private final Map<String, List<ActionEntry>> actionCache = new ConcurrentHashMap<>();

    // 정규식: 키=값 파싱 (따옴표 지원)
    private static final Pattern PARAM_PATTERN = Pattern.compile("([a-zA-Z]+)=('([^']*)'|\"([^\"]*)\"|([^\\s,]+))");

    private enum ActionType { MESSAGE, COMMAND, ITEM, UNKNOWN }
    private enum Target { SELF, SERVER }

    private static final class ActionEntry {
        final ActionType type;
        final String data;   // 메시지 내용, 명령어, 아이템 ID 등
        final int amount;    // 아이템 수량 등
        final long delay;    // 딜레이 (틱)
        final Target target; // 실행 대상

        ActionEntry(ActionType type, String data, int amount, long delay, Target target) {
            this.type = type;
            this.data = data;
            this.amount = amount;
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
                // net.Indyuce.mmoitems.MMOItems
                Class<?> mmoClass = Class.forName("net.Indyuce.mmoitems.MMOItems");
                // net.Indyuce.mmoitems.api.Type
                Class<?> typeClass = Class.forName("net.Indyuce.mmoitems.api.Type");

                // MMOItems.plugin (static field or method)
                try {
                    mmoInstance = mmoClass.getField("plugin").get(null);
                } catch (Throwable t) {
                    mmoInstance = mmoClass.getMethod("getInstance").invoke(null);
                }

                // Type.get(String) -> Type 객체 반환
                Method getType = typeClass.getMethod("get", String.class);
                mmoGetTypeMH = lookup.unreflect(getType);

                // MMOItems.getItem(Type, String) -> ItemStack
                Method getItem = mmoClass.getMethod("getItem", typeClass, String.class);
                mmoGetItemMH = lookup.unreflect(getItem).bindTo(mmoInstance);

                plugin.getLogger().info("[QuestEngine] MMOItems hook linked successfully.");
            }
        } catch (Throwable t) {
            plugin.getLogger().warning("[QuestEngine] MMOItems hook failed: " + t.getMessage());
        }

        try {
            if (ia) {
                // dev.lone.itemsadder.api.CustomStack
                Class<?> csClass = Class.forName("dev.lone.itemsadder.api.CustomStack");

                // CustomStack.getInstance(String) -> CustomStack
                Method getInstance = csClass.getMethod("getInstance", String.class);
                iaGetInstanceMH = lookup.unreflect(getInstance);

                // CustomStack.getItemStack() -> ItemStack
                Method getItemStack = csClass.getMethod("getItemStack");
                iaGetItemStackMH = lookup.unreflect(getItemStack);

                plugin.getLogger().info("[QuestEngine] ItemsAdder hook linked successfully.");
            }
        } catch (Throwable t) {
            plugin.getLogger().warning("[QuestEngine] ItemsAdder hook failed: " + t.getMessage());
        }
    }

    public void runAll(QuestDef q, String type, Player p) {
        if (q == null || q.actions == null || p == null) return;

        String cacheKey = q.id + ":" + type.toLowerCase(Locale.ROOT);

        // 캐시 조회 혹은 컴파일
        List<ActionEntry> entries = actionCache.computeIfAbsent(cacheKey, k -> compileActions(q, type));

        if (entries.isEmpty()) return;

        executeEntries(entries, q, p);
    }

    // 기존 호환성 유지
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
            if (s.toLowerCase(Locale.ROOT).endsWith("@server")) {
                target = Target.SERVER;
                s = s.substring(0, s.length() - 7).trim();
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

        if (type == ActionType.UNKNOWN) return null;

        String data = "";
        int amount = 1;

        Matcher m = PARAM_PATTERN.matcher(body);
        while (m.find()) {
            String paramName = m.group(1).toLowerCase(Locale.ROOT);
            String paramValue = m.group(3) != null ? m.group(3) : (m.group(4) != null ? m.group(4) : m.group(5));

            if (paramName.startsWith("t") || paramName.startsWith("m") || paramName.startsWith("c") || paramName.startsWith("i")) {
                data = paramValue;
            } else if (paramName.equals("a") || paramName.startsWith("amt")) {
                try { amount = Integer.parseInt(paramValue); } catch (NumberFormatException ignored) {}
            }
        }

        return new ActionEntry(type, data, amount, delay, target);
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
                String text = replace(p, e.data, q);
                if (!text.isEmpty()) {
                    p.sendMessage(ChatColor.translateAlternateColorCodes('&', text));
                }
            }
            case COMMAND -> {
                String cmd = replace(p, e.data, q);
                if (!cmd.isEmpty()) {
                    if (e.target == Target.SERVER) {
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
                    } else {
                        p.performCommand(cmd);
                    }
                }
            }
            case ITEM -> {
                // 아이템 생성 (MMOItems/ItemsAdder/Vanilla)
                ItemStack item = resolveItem(e.data, e.amount);
                if (item != null) {
                    HashMap<Integer, ItemStack> left = p.getInventory().addItem(item);
                    // 인벤토리가 꽉 찼으면 바닥에 드랍
                    if (!left.isEmpty()) {
                        for (ItemStack drop : left.values()) {
                            p.getWorld().dropItem(p.getLocation(), drop);
                        }
                    }
                } else {
                    plugin.getLogger().warning("[QuestEngine] Unknown item ID: " + e.data);
                }
            }
        }
    }

    private String replace(Player p, String s, QuestDef q) {
        if (s == null) return "";
        String t = s.replace("%player%", p.getName())
                .replace("%quest_name%", q.name);
        if (papi) {
            return PlaceholderAPI.setPlaceholders(p, t);
        }
        return t;
    }

    // --- 아이템 생성 로직 (완전 구현) ---
    private ItemStack resolveItem(String id, int amount) {
        if (id == null || id.isEmpty()) return null;

        // 1. MMOItems (Format: TYPE:ID ex: SWORD:CUTLASS)
        if (mmo && mmoGetItemMH != null && id.contains(":")) {
            try {
                String[] split = id.split(":");
                String typeStr = split[0].toUpperCase(Locale.ROOT); // ex: SWORD
                String itemId = split[1].toUpperCase(Locale.ROOT);  // ex: CUTLASS

                // Type.get("SWORD") 호출
                Object typeObj = mmoGetTypeMH.invoke(typeStr);
                if (typeObj != null) {
                    // MMOItems.plugin.getItem(typeObj, itemId) 호출
                    Object result = mmoGetItemMH.invoke(typeObj, itemId);
                    if (result instanceof ItemStack is) {
                        is.setAmount(amount);
                        return is;
                    }
                }
            } catch (Throwable ignored) {
                // MMOItems 오류 시 무시하고 다음 단계(ItemsAdder 등)로 넘어갈지 선택
                // 여기선 그냥 실패로 처리
            }
        }

        // 2. ItemsAdder (Format: namespace:id)
        if (ia && iaGetInstanceMH != null && id.contains(":")) {
            try {
                // CustomStack.getInstance("namespace:id")
                Object customStack = iaGetInstanceMH.invoke(id);
                if (customStack != null) {
                    // customStack.getItemStack()
                    Object result = iaGetItemStackMH.invoke(customStack);
                    if (result instanceof ItemStack is) {
                        is.setAmount(amount);
                        return is;
                    }
                }
            } catch (Throwable ignored) {}
        }

        // 3. Vanilla Material
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
}