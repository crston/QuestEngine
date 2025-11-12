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
import org.bukkit.scheduler.BukkitTask;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ActionExecutor
 * 고성능 퀘스트 액션 실행기
 * - 색상 코드(& → §) 자동 지원
 * - PlaceholderAPI 조건부 적용
 * - MMOItems / ItemsAdder 지원
 * - 딜레이 기반 액션 순차 실행
 * - 캐시 기반 성능 최적화
 */
public final class ActionExecutor {

    private final Plugin plugin;
    private final Msg msg;
    private final boolean papi;
    private final boolean mmo;
    private final boolean ia;

    private static final Map<String, Method> methodCache = new ConcurrentHashMap<>();
    private static Class<?> mmoItemsClass;
    private static Class<?> mmoTypeClass;
    private static Class<?> iaCustomStackClass;
    private static MethodHandle mmoGetItemMH;
    private static MethodHandle iaGetInstanceMH;
    private static MethodHandle iaGetItemStackMH;

    private enum ActionType { MESSAGE, COMMAND, ITEM, DELAY }
    private enum Target { SELF, SERVER }

    private static final class ActionEntry {
        final ActionType type;
        final String value;
        final int amount;
        final long delayTicks;
        final Target target;

        ActionEntry(ActionType type, String value, int amount, long delayTicks, Target target) {
            this.type = type;
            this.value = value;
            this.amount = amount;
            this.delayTicks = delayTicks;
            this.target = target;
        }
    }

    private record CacheKey(String questId, String key) {}

    private final Map<CacheKey, List<ActionEntry>> compiledCache = new ConcurrentHashMap<>();

    public ActionExecutor(Plugin plugin, Msg msg) {
        this.plugin = plugin;
        this.msg = msg;
        this.papi = Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI");
        this.mmo = Bukkit.getPluginManager().isPluginEnabled("MMOItems");
        this.ia = Bukkit.getPluginManager().isPluginEnabled("ItemsAdder");
        initHooks();
    }

    /** MMOItems / ItemsAdder 리플렉션 초기화 */
    private void initHooks() {
        MethodHandles.Lookup lookup = MethodHandles.lookup();
        if (mmo) {
            try {
                mmoItemsClass = Class.forName("net.Indyuce.mmoitems.MMOItems");
                mmoTypeClass = Class.forName("net.Indyuce.mmoitems.api.Type");
                Method getInstance = mmoItemsClass.getMethod("getInstance");
                Object inst = getInstance.invoke(null);
                Method getItem = mmoItemsClass.getMethod("getItem", mmoTypeClass, String.class);
                mmoGetItemMH = lookup.unreflect(getItem).bindTo(inst);
                plugin.getLogger().info("[QuestEngine] MMOItems hook active");
            } catch (Throwable t) {
                mmoGetItemMH = null;
            }
        }
        if (ia) {
            try {
                iaCustomStackClass = Class.forName("dev.lone.itemsadder.api.CustomStack");
                Method getInstance = iaCustomStackClass.getMethod("getInstance", String.class);
                Method getItemStack = iaCustomStackClass.getMethod("getItemStack");
                iaGetInstanceMH = lookup.unreflect(getInstance);
                iaGetItemStackMH = lookup.unreflect(getItemStack);
                plugin.getLogger().info("[QuestEngine] ItemsAdder hook active");
            } catch (Throwable t) {
                iaGetInstanceMH = null;
                iaGetItemStackMH = null;
            }
        }
    }

    /** 퀘스트의 특정 액션 시퀀스를 실행 */
    public void runAll(QuestDef q, String type, Player p) {
        if (q == null || q.actions == null) return;

        List<String> list = null;
        for (Map.Entry<String, List<String>> e : q.actions.entrySet()) {
            if (e.getKey().equalsIgnoreCase(type)) {
                list = e.getValue();
                break;
            }
        }

        // 아무 액션이 없으면 그냥 조용히 무시
        if (list == null || list.isEmpty()) return;

        for (String s : list) {
            try {
                executeLine(q, s, p);
            } catch (Throwable t) {
                plugin.getLogger().warning("[QuestEngine] Action failed (" + type + "): " + s + " - " + t.getMessage());
            }
        }
    }

    /** 문자열을 ActionEntry 리스트로 컴파일 */
    private List<ActionEntry> compile(List<String> list) {
        List<ActionEntry> out = new ArrayList<>(list.size());
        long delay = 0L;
        for (String line : list) {
            if (line == null || line.isBlank()) continue;
            String s = line.trim();
            Target target = s.toLowerCase(Locale.ROOT).endsWith("@server") ? Target.SERVER : Target.SELF;
            if (target == Target.SERVER) s = s.substring(0, s.length() - 7).trim();

            if (s.toLowerCase(Locale.ROOT).startsWith("delay ")) {
                delay += parseDelay(s);
                out.add(new ActionEntry(ActionType.DELAY, "", 0, delay, target));
                continue;
            }
            if (s.startsWith("msg{") || s.startsWith("message{")) {
                String t = extract(s, "t=");
                out.add(new ActionEntry(ActionType.MESSAGE, t, 0, delay, target));
                continue;
            }
            if (s.startsWith("cmd{") || s.startsWith("command{")) {
                String c = extract(s, "c=");
                out.add(new ActionEntry(ActionType.COMMAND, c, 0, delay, target));
                continue;
            }
            if (s.startsWith("item{")) {
                String t = extract(s, "t=");
                int a = parseIntSafe(extract(s, "a="), 1);
                out.add(new ActionEntry(ActionType.ITEM, t, a, delay, target));
                continue;
            }
        }
        return out;
    }

    /** 실행 */
    private void runEntries(List<ActionEntry> entries, QuestDef q, Player p) {
        if (entries.isEmpty()) return;
        final int[] idx = {0};
        scheduleNext(entries, idx, 0L, q, p);
    }

    /** 딜레이 기반 순차 실행 스케줄링 */
    private void scheduleNext(List<ActionEntry> entries, int[] idx, long prevDelay, QuestDef q, Player p) {
        if (idx[0] >= entries.size()) return;
        ActionEntry current = entries.get(idx[0]);
        long delta = Math.max(0L, current.delayTicks - prevDelay);
        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            long nowDelay = current.delayTicks;
            int start = idx[0];
            while (idx[0] < entries.size() && entries.get(idx[0]).delayTicks == nowDelay) {
                execute(entries.get(idx[0]), q, p);
                idx[0]++;
            }
            scheduleNext(entries, idx, nowDelay, q, p);
        }, delta);
    }

    /** 액션 실행 본체 */
    private void execute(ActionEntry e, QuestDef q, Player p) {
        switch (e.type) {
            case DELAY -> {
                // no-op
            }
            case MESSAGE -> {
                String txt = applyPlaceholders(p, e.value, q);
                if (txt.isEmpty()) return;

                // 따옴표 자동 제거
                if ((txt.startsWith("\"") && txt.endsWith("\"")) || (txt.startsWith("'") && txt.endsWith("'"))) {
                    txt = txt.substring(1, txt.length() - 1);
                }

                // 색상 코드 변환
                txt = ChatColor.translateAlternateColorCodes('&', txt);

                if (e.target == Target.SELF) {
                    p.sendMessage(txt);
                } else {
                    String finalTxt = txt;
                    Bukkit.getOnlinePlayers().forEach(pl -> pl.sendMessage(finalTxt));
                }
            }
            case COMMAND -> {
                String cmd = applyPlaceholders(p, e.value, q);
                if (cmd == null || cmd.trim().isEmpty()) {
                    plugin.getLogger().warning("[QuestEngine] Skipped empty command in quest '" + q.id + "'");
                    return;
                }
                cmd = cmd.trim();

                try {
                    if (e.target == Target.SELF) {
                        p.performCommand(cmd);
                    } else {
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
                    }
                } catch (Throwable ex) {
                    plugin.getLogger().warning("[QuestEngine] Failed to execute command: " + cmd);
                    ex.printStackTrace();
                }
            }
            case ITEM -> {
                if (e.value == null || e.value.isEmpty()) {
                    plugin.getLogger().warning("[QuestEngine] Empty item id in quest '" + q.id + "'");
                    return;
                }
                ItemStack is = createItemFast(e.value, e.amount);
                if (is != null) {
                    p.getInventory().addItem(is);
                } else {
                    plugin.getLogger().warning("[QuestEngine] Unknown item id '" + e.value + "' in quest '" + q.id + "'");
                }
            }
        }
    }

    /** PlaceholderAPI 및 변환 적용 */
    private String applyPlaceholders(Player p, String text, QuestDef q) {
        if (text == null || text.isEmpty()) return "";
        String result = text
                .replace("%player%", p.getName())
                .replace("%quest_name%", q.name);

        if (papi && result.contains("%")) {
            try { result = PlaceholderAPI.setPlaceholders(p, result); } catch (Throwable ignored) {}
        }
        return result;
    }

    private long parseDelay(String s) {
        try {
            String[] parts = s.split(" ");
            return Integer.parseInt(parts[1]) * 20L;
        } catch (Throwable t) {
            return 0L;
        }
    }

    private int parseIntSafe(String s, int def) {
        try { return Integer.parseInt(s.trim()); } catch (Throwable t) { return def; }
    }

    /** 따옴표 인식 강화 버전 */
    private String extract(String s, String key) {
        int i = s.indexOf(key);
        if (i < 0) return "";
        int start = i + key.length();

        // "..." 또는 '...' 감싸진 텍스트를 감지
        int firstQuote = s.indexOf('"', start);
        int lastQuote = s.lastIndexOf('"');
        if (firstQuote >= 0 && lastQuote > firstQuote) {
            return s.substring(firstQuote + 1, lastQuote).trim();
        }

        int end = s.indexOf('}', start);
        if (end < 0) end = s.length();
        return s.substring(start, end).trim();
    }

    private ItemStack createItemFast(String id, int amount) {
        if (id == null || id.isEmpty()) return null;
        ItemStack result = null;
        if (mmo && id.contains(":")) result = createMmoItem(id, amount);
        if (result == null && ia) result = createIaItem(id, amount);
        if (result != null) return result;
        Material mat = Material.matchMaterial(id.toUpperCase(Locale.ROOT));
        return mat == null ? null : new ItemStack(mat, amount);
    }

    private ItemStack createMmoItem(String id, int amount) {
        try {
            String[] split = id.split(":");
            if (split.length < 2) return null;
            String typeStr = split[0].toUpperCase(Locale.ROOT);
            String templateId = split[1];
            Object type = mmoTypeClass.getMethod("valueOf", String.class).invoke(null, typeStr);
            Object item = mmoGetItemMH.invoke(type, templateId);
            if (item == null) return null;
            Method newBuilder = item.getClass().getMethod("newBuilder");
            Object builder = newBuilder.invoke(item);
            Method build = builder.getClass().getMethod("build");
            ItemStack result = (ItemStack) build.invoke(builder);
            result.setAmount(Math.max(1, amount));
            return result;
        } catch (Throwable t) {
            return null;
        }
    }

    private ItemStack createIaItem(String id, int amount) {
        try {
            Object custom = iaGetInstanceMH.invoke(id);
            if (custom == null) return null;
            ItemStack result = (ItemStack) iaGetItemStackMH.invoke(custom);
            result.setAmount(Math.max(1, amount));
            return result;
        } catch (Throwable t) {
            return null;
        }
    }

    /** 별도 run() 래퍼 */
    /** 액션 그룹(accept/start/success 등)을 실행 */
    public void run(QuestDef def, String key, Player p) {
        if (def == null || key == null || p == null) return;

        // 대소문자 무시하고 액션 그룹 탐색
        List<String> list = null;
        for (Map.Entry<String, List<String>> e : def.actions.entrySet()) {
            if (e.getKey().equalsIgnoreCase(key)) {
                list = e.getValue();
                break;
            }
        }

        if (list == null || list.isEmpty()) {
            plugin.getLogger().info("[QuestEngine] No actions found for type=" + key + " in quest=" + def.id);
            return;
        }

        // 여기서 직접 실행 (절대 runAll 다시 호출 금지)
        for (String line : list) {
            try {
                // line 자체가 "msg{t=...}", "command{...}" 등 액션 문자열임
                executeLine(def, line, p);
            } catch (Throwable t) {
                plugin.getLogger().warning("[QuestEngine] Action failed in quest " + def.id + ": " + line + " (" + t.getMessage() + ")");
            }
        }
    }

    /** 개별 액션 실행 (기존 runAll 안에서 쓰던 내부 로직을 분리) */
    private void executeLine(QuestDef q, String s, Player p) {
        if (s == null || s.isBlank()) return;
        String line = s.trim();

        if (line.startsWith("{") && line.endsWith("}")) {
            line = line.substring(1, line.length() - 1).trim();
        }

        ActionType type;
        Target target = line.toLowerCase(Locale.ROOT).endsWith("@server") ? Target.SERVER : Target.SELF;
        if (target == Target.SERVER) line = line.substring(0, line.length() - 7).trim();

        if (line.startsWith("msg{") || line.startsWith("message{")) {
            type = ActionType.MESSAGE;
        } else if (line.startsWith("cmd{") || line.startsWith("command{")) {
            type = ActionType.COMMAND;
        } else if (line.startsWith("item{")) {
            type = ActionType.ITEM;
        } else {
            plugin.getLogger().warning("[QuestEngine] Unknown action line: " + line);
            return;
        }

        // 여기서 기존 execute(ActionEntry) 로직을 그대로 복제해도 됨
        // 빠르게 처리하기 위해 switch(type) 분기 직접 호출
        switch (type) {
            case MESSAGE -> {
                String txt = applyPlaceholders(p, extract(line, "t="), q);
                if (!txt.isEmpty()) p.sendMessage(ChatColor.translateAlternateColorCodes('&', txt));
            }
            case COMMAND -> {
                String cmd = applyPlaceholders(p, extract(line, "c="), q);
                if (!cmd.isEmpty()) {
                    if (target == Target.SERVER) Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
                    else p.performCommand(cmd);
                }
            }
            case ITEM -> {
                String id = extract(line, "t=");
                int amt = parseIntSafe(extract(line, "a="), 1);
                ItemStack is = createItemFast(id, amt);
                if (is != null) p.getInventory().addItem(is);
            }
        }
    }
}
