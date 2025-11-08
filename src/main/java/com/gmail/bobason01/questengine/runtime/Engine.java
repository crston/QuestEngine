package com.gmail.bobason01.questengine.runtime;

import com.gmail.bobason01.questengine.QuestEnginePlugin;
import com.gmail.bobason01.questengine.action.ActionExecutor;
import com.gmail.bobason01.questengine.progress.ProgressRepository;
import com.gmail.bobason01.questengine.quest.CustomEventData;
import com.gmail.bobason01.questengine.quest.QuestDef;
import com.gmail.bobason01.questengine.quest.QuestRepository;
import com.gmail.bobason01.questengine.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;

import java.lang.reflect.Method;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public final class Engine {

    private final QuestEnginePlugin plugin;
    private final QuestRepository quests;
    private final ProgressRepository progress;
    private final ActionExecutor actions;
    private final Msg msg;

    private final ExecutorService worker;

    private final Map<String, List<QuestDef>> eventCache = new ConcurrentHashMap<>(128);
    private final Map<String, Method> reflectCache = new ConcurrentHashMap<>(256);

    private static final class BoolCacheEntry {
        final boolean value;
        final long expireAt;
        BoolCacheEntry(boolean v, long e) { value = v; expireAt = e; }
    }
    private final Map<String, BoolCacheEntry> conditionCache = new ConcurrentHashMap<>(1024);
    private final long conditionTtlNanos;

    private final Map<UUID, Object> playerLocks = new ConcurrentHashMap<>(256);
    private final Map<UUID, Map<String, Long>> recentEventWindow = new ConcurrentHashMap<>(256);
    private final long dedupWindowNanos;

    public Engine(QuestEnginePlugin plugin,
                  QuestRepository quests,
                  ProgressRepository progress,
                  ActionExecutor actions,
                  Msg msg) {
        this.plugin = plugin;
        this.quests = quests;
        this.progress = progress;
        this.actions = actions;
        this.msg = msg;

        int cpus = Math.max(2, Runtime.getRuntime().availableProcessors());
        this.worker = new ThreadPoolExecutor(
                Math.max(4, cpus),
                Math.max(8, cpus * 2),
                30L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(4096),
                r -> {
                    Thread t = new Thread(r, "QuestEngine-Worker");
                    t.setDaemon(true);
                    t.setPriority(Thread.NORM_PRIORITY + 1);
                    return t;
                },
                new ThreadPoolExecutor.DiscardOldestPolicy()
        );

        long ttlMs = Math.max(50, plugin.getConfig().getLong("performance.condition-cache-ttl-ms", 300));
        this.conditionTtlNanos = ttlMs * 1_000_000L;

        long dedupMs = Math.max(3, plugin.getConfig().getLong("performance.event-dedup-window-ms", 10));
        this.dedupWindowNanos = dedupMs * 1_000_000L;

        cacheEvents();
        scheduleDailyResets();
    }

    public QuestRepository quests() { return quests; }
    public ProgressRepository progress() { return progress; }
    public ActionExecutor actions() { return actions; }
    public Msg msg() { return msg; }
    public ExecutorService asyncPool() { return worker; }

    public void refreshEventCache() { cacheEvents(); }

    private void cacheEvents() {
        Map<String, List<QuestDef>> tmp = new ConcurrentHashMap<>();
        for (String id : quests.ids()) {
            QuestDef q = quests.get(id);
            if (q == null) continue;
            if (q.event == null) continue;
            String key = q.event.toLowerCase(Locale.ROOT);
            tmp.computeIfAbsent(key, k -> new ArrayList<>()).add(q);
        }
        eventCache.clear();
        eventCache.putAll(tmp);
    }

    public void startQuest(Player p, QuestDef q) {
        if (progress.isActive(p.getUniqueId(), p.getName(), q.id)) {
            p.sendMessage(msg.pref("quest_already_active"));
            return;
        }
        actions.runAll(q, "accept", p);
        progress.start(p.getUniqueId(), p.getName(), q.id);
        actions.runAll(q, "start", p);
        p.sendMessage(msg.pref("quest_started").replace("%quest_name%", q.name));
    }

    public void cancelQuest(Player p, QuestDef q) {
        if (!progress.isActive(p.getUniqueId(), p.getName(), q.id)) {
            p.sendMessage(msg.pref("quest_not_active"));
            return;
        }
        progress.cancel(p.getUniqueId(), p.getName(), q.id);
        actions.runAll(q, "cancel", p);
        p.sendMessage(msg.pref("quest_canceled").replace("%quest_name%", q.name));
    }

    public void stopQuest(Player p, QuestDef q) {
        progress.cancel(p.getUniqueId(), p.getName(), q.id);
        actions.runAll(q, "stop", p);
    }

    public void forceComplete(Player p, QuestDef q) {
        progress.complete(p.getUniqueId(), p.getName(), q.id, q.points);
        actions.runAll(q, "success", p);
    }

    public void abandonAll(Player p) {
        progress.cancelAll(p.getUniqueId(), p.getName());
        p.sendMessage(msg.pref("abandon_all_done"));
    }

    public void listActiveTo(Player p) {
        List<String> active = progress.activeQuestIds(p.getUniqueId(), p.getName());
        if (active.isEmpty()) {
            p.sendMessage(msg.pref("list.empty"));
            return;
        }

        p.sendMessage(msg.pref("list.header"));
        String lineReward = msg.get("list.reward");     // ex) "§6보상 %reward%"
        String lineProgress = msg.get("list.progress"); // ex) "§f진행도 %bar% §7(%percent%%)"
        String lineTitle = msg.get("list.title");       // ex) "§e%title% §7(%value%/%target%)"
        String lineDesc = msg.get("list.desc");         // ex) " §7- %desc%"

        StringBuilder sb = new StringBuilder(256);
        for (String id : active) {
            QuestDef q = quests.get(id);
            if (q == null) continue;

            int val = progress.value(p.getUniqueId(), p.getName(), id);
            double pct = q.amount <= 0 ? 1.0 : Math.min(1.0, val / (double) q.amount);
            int filled = (int) (pct * 20);
            if (filled < 0) filled = 0;
            if (filled > 20) filled = 20;

            // progress bar 생성 (GC 최소화)
            char[] green = new char[filled];
            char[] gray = new char[20 - filled];
            Arrays.fill(green, '■');
            Arrays.fill(gray, '■');
            String bar = "§a" + new String(green) + "§7" + new String(gray);

            String title = q.display.title;
            String reward = q.display.reward;

            p.sendMessage(" ");
            sb.setLength(0);
            p.sendMessage(lineTitle
                    .replace("%title%", ChatColor.translateAlternateColorCodes('&', title))
                    .replace("%value%", Integer.toString(val))
                    .replace("%target%", Integer.toString(q.amount)));

            for (int i = 0, size = q.display.description.size(); i < size; i++) {
                String line = q.display.description.get(i);
                p.sendMessage(lineDesc.replace("%desc%", ChatColor.translateAlternateColorCodes('&', line)));
            }

            p.sendMessage(lineProgress
                    .replace("%bar%", bar)
                    .replace("%percent%", Integer.toString((int) Math.round(pct * 100))));

            p.sendMessage(lineReward.replace("%reward%", ChatColor.translateAlternateColorCodes('&', reward)));
        }
    }


    public void handle(Player p, String eventName, Event e) {
        if (p == null) return;
        if (eventName == null) return;

        List<QuestDef> list = eventCache.get(eventName.toLowerCase(Locale.ROOT));
        if (list == null || list.isEmpty()) return;

        UUID uid = p.getUniqueId();
        if (isDedup(uid, eventName)) return;

        Object lock = playerLocks.computeIfAbsent(uid, k -> new Object());

        worker.execute(() -> {
            synchronized (lock) {
                processEventInternal(p, eventName, e, list);
            }
        });
    }

    private void processEventInternal(Player p, String eventName, Event e, List<QuestDef> list) {
        UUID uid = p.getUniqueId();
        String name = p.getName();

        List<Runnable> pending = new ArrayList<>(4);

        for (int i = 0; i < list.size(); i++) {
            QuestDef q = list.get(i);
            if (q == null) continue;
            if (!progress.isActive(uid, name, q.id)) continue;

            boolean fail = false;
            if (q.condFail != null && !q.condFail.isEmpty()) {
                for (int k = 0; k < q.condFail.size(); k++) {
                    String cond = q.condFail.get(k);
                    if (cachedEval(p, e, null, cond)) { fail = true; break; }
                }
            }
            if (fail) {
                final String qid = q.id;
                pending.add(() -> {
                    actions.runAll(q, "fail", p);
                    progress.cancel(uid, name, qid);
                });
                continue;
            }

            boolean ok = true;
            if (q.condSuccess != null && !q.condSuccess.isEmpty()) {
                for (int k = 0; k < q.condSuccess.size(); k++) {
                    String cond = q.condSuccess.get(k);
                    if (!cachedEval(p, e, null, cond)) { ok = false; break; }
                }
            }
            if (!ok) continue;

            int val = progress.addProgress(uid, name, q.id, 1);
            if (val >= q.amount) {
                pending.add(() -> {
                    actions.runAll(q, "success", p);
                    progress.complete(uid, name, q.id, q.points);
                    if (q.repeat < 0) {
                        progress.start(uid, name, q.id);
                        actions.runAll(q, "restart", p);
                        actions.runAll(q, "repeat", p);
                    }
                });
            }
        }

        if (!pending.isEmpty()) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                for (int i = 0; i < pending.size(); i++) {
                    try { pending.get(i).run(); } catch (Throwable ignored) {}
                }
            });
        }
    }

    public void handleCustom(Player p, String eventKey, Map<String, Object> ctx) {
        if (p == null) return;
        if (eventKey == null) return;

        List<QuestDef> list = eventCache.get(eventKey.toLowerCase(Locale.ROOT));
        if (list == null || list.isEmpty()) return;

        UUID uid = p.getUniqueId();
        if (isDedup(uid, eventKey)) return;

        Object lock = playerLocks.computeIfAbsent(uid, k -> new Object());

        worker.execute(() -> {
            synchronized (lock) {
                processCustomInternal(p, list, ctx);
            }
        });
    }

    private void processCustomInternal(Player p, List<QuestDef> list, Map<String, Object> ctx) {
        UUID uid = p.getUniqueId();
        String name = p.getName();

        List<Runnable> pending = new ArrayList<>(4);

        for (int i = 0; i < list.size(); i++) {
            QuestDef q = list.get(i);
            if (q == null) continue;
            if (!progress.isActive(uid, name, q.id)) continue;

            boolean fail = false;
            if (q.condFail != null && !q.condFail.isEmpty()) {
                for (int k = 0; k < q.condFail.size(); k++) {
                    String cond = q.condFail.get(k);
                    if (cachedEval(p, null, ctx, cond)) { fail = true; break; }
                }
            }
            if (fail) {
                final String qid = q.id;
                pending.add(() -> {
                    actions.runAll(q, "fail", p);
                    progress.cancel(uid, name, qid);
                });
                continue;
            }

            boolean ok = true;
            if (q.condSuccess != null && !q.condSuccess.isEmpty()) {
                for (int k = 0; k < q.condSuccess.size(); k++) {
                    String cond = q.condSuccess.get(k);
                    if (!cachedEval(p, null, ctx, cond)) { ok = false; break; }
                }
            }
            if (!ok) continue;

            pending.add(() -> {
                actions.runAll(q, "success", p);
                progress.complete(uid, name, q.id, q.points);
                if (q.repeat < 0) {
                    progress.start(uid, name, q.id);
                    actions.runAll(q, "restart", p);
                    actions.runAll(q, "repeat", p);
                }
            });
        }

        if (!pending.isEmpty()) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                for (int i = 0; i < pending.size(); i++) {
                    try { pending.get(i).run(); } catch (Throwable ignored) {}
                }
            });
        }
    }

    public void handleDynamic(Event e) {
        if (e == null) return;

        List<Runnable> pending = Collections.synchronizedList(new ArrayList<>(8));

        worker.execute(() -> {
            for (String id : quests.ids()) {
                QuestDef q = quests.get(id);
                if (q == null) continue;
                if (!"custom".equalsIgnoreCase(q.type)) continue;
                if (!"custom_event".equalsIgnoreCase(q.event)) continue;
                if (q.custom == null) continue;

                if (!isEventMatch(q.custom, e)) continue;

                Player target = extractPlayer(e, q.custom.playerGetter);
                if (target == null) continue;

                UUID uid = target.getUniqueId();
                String name = target.getName();

                if (isDedup(uid, q.event)) continue;

                Object lock = playerLocks.computeIfAbsent(uid, k -> new Object());
                synchronized (lock) {

                    if (!progress.isActive(uid, name, q.id)) continue;

                    Map<String, Object> ctx = captureContext(e, q.custom);

                    boolean fail = false;
                    if (q.condFail != null && !q.condFail.isEmpty()) {
                        for (int k = 0; k < q.condFail.size(); k++) {
                            String cond = q.condFail.get(k);
                            if (cachedEval(target, null, ctx, cond)) { fail = true; break; }
                        }
                    }
                    if (fail) {
                        final String qid = q.id;
                        pending.add(() -> {
                            actions.runAll(q, "fail", target);
                            progress.cancel(uid, name, qid);
                        });
                        continue;
                    }

                    boolean ok = true;
                    if (q.condSuccess != null && !q.condSuccess.isEmpty()) {
                        for (int k = 0; k < q.condSuccess.size(); k++) {
                            String cond = q.condSuccess.get(k);
                            if (!cachedEval(target, null, ctx, cond)) { ok = false; break; }
                        }
                    }
                    if (!ok) continue;

                    pending.add(() -> {
                        actions.runAll(q, "success", target);
                        progress.complete(uid, name, q.id, q.points);
                        if (q.repeat < 0) {
                            progress.start(uid, name, q.id);
                            actions.runAll(q, "restart", target);
                            actions.runAll(q, "repeat", target);
                        }
                    });
                }
            }

            if (!pending.isEmpty()) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    for (int i = 0; i < pending.size(); i++) {
                        try { pending.get(i).run(); } catch (Throwable ignored) {}
                    }
                });
            }
        });
    }

    private boolean isEventMatch(CustomEventData custom, Event e) {
        if (custom.eventClass == null || custom.eventClass.isEmpty()) return false;
        try {
            Class<?> cls = Class.forName(custom.eventClass);
            return cls.isAssignableFrom(e.getClass());
        } catch (Throwable t) {
            return false;
        }
    }

    private Player extractPlayer(Event e, String getterChain) {
        Object cur = e;
        String chain = getterChain == null || getterChain.isEmpty() ? "getPlayer()" : getterChain;
        String[] parts = chain.replace("()", "").split("\\.");
        try {
            for (int i = 0; i < parts.length; i++) {
                String m = parts[i];
                Method md = cur.getClass().getMethod(m);
                cur = md.invoke(cur);
                if (cur == null) return null;
            }
            if (cur instanceof Player p) return p;
        } catch (Throwable ignored) {}
        return null;
    }

    private Map<String, Object> captureContext(Event e, CustomEventData custom) {
        if (custom.captures == null || custom.captures.isEmpty()) return new HashMap<>(0);
        Map<String, Object> ctx = new HashMap<>(Math.max(4, custom.captures.size()));
        for (Map.Entry<String, String> en : custom.captures.entrySet()) {
            String key = en.getKey();
            String chain = en.getValue();
            Object val = reflectChain(e, chain);
            if (val != null) ctx.put(key, val);
        }
        return ctx;
    }

    private boolean cachedEval(Player p, Event e, Map<String, Object> ctx, String expr) {
        if (expr == null || expr.isEmpty()) return true;
        String key = p.getUniqueId() + "|" + expr;
        long now = System.nanoTime();
        BoolCacheEntry ent = conditionCache.get(key);
        if (ent != null && ent.expireAt > now) return ent.value;
        boolean val = ConditionEvaluator.eval(p, e, ctx, expr);
        conditionCache.put(key, new BoolCacheEntry(val, now + conditionTtlNanos));
        return val;
    }

    private Object reflectChain(Object base, String chain) {
        if (base == null || chain == null || chain.isEmpty()) return null;
        Object cur = base;
        String[] parts = chain.replace("()", "").split("\\.");
        try {
            for (int i = 0; i < parts.length; i++) {
                String mName = parts[i];
                String cacheKey = cur.getClass().getName() + "#" + mName;
                Object obj = cur;
                Method m = reflectCache.computeIfAbsent(cacheKey, k -> {
                    try { return obj.getClass().getMethod(mName); }
                    catch (Throwable ex) { return null; }
                });
                if (m == null) return null;
                cur = m.invoke(cur);
                if (cur == null) return null;
            }
            return cur;
        } catch (Throwable t) { return null; }
    }

    private boolean isDedup(UUID uid, String key) {
        long now = System.nanoTime();
        Map<String, Long> m = recentEventWindow.computeIfAbsent(uid, k -> new ConcurrentHashMap<>(8));
        Long last = m.get(key);
        if (last != null && now - last < dedupWindowNanos) return true;
        m.put(key, now);
        return false;
    }

    private void scheduleDailyResets() {
        Map<String, List<String>> timeToQuestIds = new HashMap<>();
        String defaultTime = plugin.getConfig().getString("reset.default-time", "04:00");

        for (String id : quests.ids()) {
            QuestDef q = quests.get(id);
            if (q == null) continue;
            if (q.reset == null) continue;
            if (!"DAILY".equalsIgnoreCase(q.reset.policy)) continue;
            String at = q.reset.time == null || q.reset.time.isBlank() ? defaultTime : q.reset.time;
            timeToQuestIds.computeIfAbsent(at, k -> new ArrayList<>()).add(id);
        }

        for (Map.Entry<String, List<String>> e : timeToQuestIds.entrySet()) {
            String time = e.getKey();
            long delayMs = millisUntil(time);
            long periodMs = 24L * 60L * 60L * 1000L;
            List<String> ids = List.copyOf(e.getValue());

            Bukkit.getScheduler().runTaskTimerAsynchronously(
                    plugin,
                    () -> {
                        for (Player p : Bukkit.getOnlinePlayers()) {
                            UUID uid = p.getUniqueId();
                            String name = p.getName();
                            for (int i = 0; i < ids.size(); i++) {
                                progress.reset(uid, name, ids.get(i));
                            }
                        }
                        plugin.getLogger().info("[QuestEngine] Daily reset done at " + time + " for " + ids.size() + " quests");
                    },
                    delayMs / 50,
                    periodMs / 50
            );
        }
    }

    private long millisUntil(String hhmm) {
        String[] parts = hhmm.split(":");
        int h = 0;
        int m = 0;
        try {
            h = Integer.parseInt(parts[0]);
            if (parts.length > 1) m = Integer.parseInt(parts[1]);
        } catch (Throwable ignored) {}
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime next = now.withHour(h).withMinute(m).withSecond(0).withNano(0);
        if (!next.isAfter(now)) next = next.plusDays(1);
        return Duration.between(now, next).toMillis();
    }

    public void shutdown() {
        try { worker.shutdownNow(); } catch (Throwable ignored) {}
        conditionCache.clear();
        eventCache.clear();
        reflectCache.clear();
        playerLocks.clear();
        recentEventWindow.clear();
    }
}
