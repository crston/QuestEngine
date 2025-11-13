package com.gmail.bobason01.questengine.runtime;

import com.gmail.bobason01.questengine.QuestEnginePlugin;
import com.gmail.bobason01.questengine.action.ActionExecutor;
import com.gmail.bobason01.questengine.progress.PlayerData;
import com.gmail.bobason01.questengine.progress.ProgressRepository;
import com.gmail.bobason01.questengine.quest.QuestDef;
import com.gmail.bobason01.questengine.quest.QuestRepository;
import com.gmail.bobason01.questengine.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;

/**
 * Engine v2
 * 단일 이벤트 파이프라인 기반 퀘스트 엔진
 * 이벤트 처리, 조건, 타겟 매칭, 체인, 반복, 보드 퀘스트, NPC 상호 작용을 하나의 흐름으로 통합
 */
public final class Engine {

    private final QuestEnginePlugin plugin;
    private final QuestRepository quests;
    private final ProgressRepository progress;
    private final ActionExecutor actions;
    private final Msg msg;
    private final ExecutorService worker;

    private final Map<UUID, Object> playerLocks = new ConcurrentHashMap<UUID, Object>();
    private final Map<UUID, Map<String, Long>> recentEventWindow = new ConcurrentHashMap<UUID, Map<String, Long>>();
    private final Map<String, TargetMatcher> matchers = new ConcurrentHashMap<String, TargetMatcher>();

    private final Map<String, BoolCacheEntry> conditionCache = new ConcurrentHashMap<String, BoolCacheEntry>();
    private final long conditionTtlNanos;
    private final long dedupWindowNanos;

    private static final long NPC_ARM_WINDOW_NANOS = 2_000_000_000L;

    private static final class BoolCacheEntry {
        final boolean value;
        final long expireAt;
        BoolCacheEntry(boolean value, long expireAt) {
            this.value = value;
            this.expireAt = expireAt;
        }
    }

    private static final class NpcArmState {
        final String questId;
        final long until;
        NpcArmState(String questId, long until) {
            this.questId = questId;
            this.until = until;
        }
    }

    private final Map<UUID, NpcArmState> npcArm = new ConcurrentHashMap<UUID, NpcArmState>();

    @FunctionalInterface
    public interface TargetMatcher {
        boolean test(Player player, Event event, String target);
    }

    public Engine(
            QuestEnginePlugin plugin,
            QuestRepository quests,
            ProgressRepository progress,
            ActionExecutor actions,
            Msg msg,
            ExecutorService worker
    ) {
        this.plugin = plugin;
        this.quests = quests;
        this.progress = progress;
        this.actions = actions;
        this.msg = msg;
        this.worker = worker;

        long ttlMs = Math.max(50L, plugin.getConfig().getLong("performance.condition-cache-ttl-ms", 300L));
        this.conditionTtlNanos = ttlMs * 1_000_000L;

        long dedupMs = Math.max(3L, plugin.getConfig().getLong("performance.event-dedup-window-ms", 10L));
        this.dedupWindowNanos = dedupMs * 1_000_000L;

        installDefaultMatchers();
        scheduleDailyResets();
        preloadInternalQuests();
    }

    public QuestRepository quests() {
        return quests;
    }

    public ProgressRepository progress() {
        return progress;
    }

    public ActionExecutor actions() {
        return actions;
    }

    public Msg msg() {
        return msg;
    }

    public ExecutorService asyncPool() {
        return worker;
    }

    public void refreshEventCache() {
        quests.reload();
        quests.rebuildEventMap();
    }

    public void shutdown() {
        try {
            worker.shutdownNow();
        } catch (Throwable ignored) {
        }
        conditionCache.clear();
        playerLocks.clear();
        recentEventWindow.clear();
        matchers.clear();
        npcArm.clear();
    }

    public void startQuest(Player p, String questId) {
        if (p == null || questId == null) return;
        String id = questId.toLowerCase(Locale.ROOT);
        QuestDef q = quests.get(id);
        if (q == null) {
            p.sendMessage(msg.pref("invalid_args"));
            return;
        }
        startQuest(p, q);
    }


    public void startQuest(Player player, QuestDef def) {
        if (player == null || def == null) return;

        UUID uid = player.getUniqueId();
        String name = player.getName();

        if (progress.isCompleted(uid, name, def.id)) {
            player.sendMessage(msg.pref("quest_no_repeat").replace("%quest_name%", def.name));
            return;
        }

        if (progress.isActive(uid, name, def.id)) {
            player.sendMessage(msg.pref("quest_already_active"));
            return;
        }

        if (isBoardQuest(def) && !allowBoardStartContext(player)) {
            player.sendMessage(msg.pref("quest_board_only"));
            return;
        }

        progress.start(uid, name, def.id);
        actions.runAll(def, "accept", player);
        actions.runAll(def, "start", player);
        player.sendMessage(msg.pref("quest_started").replace("%quest_name%", def.name));
    }

    public void cancelQuest(Player p, String questId) {
        if (p == null || questId == null) return;
        String id = questId.toLowerCase(Locale.ROOT);
        QuestDef q = quests.get(id);
        cancelQuest(p, q);
    }

    public void cancelQuest(Player player, QuestDef def) {
        if (player == null || def == null) return;

        UUID uid = player.getUniqueId();
        String name = player.getName();

        if (!progress.isActive(uid, name, def.id)) {
            player.sendMessage(msg.pref("quest_not_active"));
            return;
        }

        progress.cancel(uid, name, def.id);
        actions.runAll(def, "cancel", player);
        player.sendMessage(msg.pref("quest_canceled").replace("%quest_name%", def.name));
    }

    public void stopQuest(UUID uuid, String playerName, String questId) {
        if (uuid == null || playerName == null || questId == null) return;
        String id = questId.toLowerCase(Locale.ROOT);
        progress.cancel(uuid, playerName, id);
        Player p = Bukkit.getPlayer(uuid);
        if (p != null && p.isOnline()) {
            QuestDef q = quests.get(id);
            if (q != null) actions.runAll(q, "cancel", p);
            p.sendMessage(msg.pref("quest_stopped").replace("%quest_name%", q != null ? q.name : id));
        }
    }

    public void stopQuest(Player player, QuestDef def) {
        if (player == null || def == null) return;
        UUID uid = player.getUniqueId();
        String name = player.getName();
        if (!progress.isActive(uid, name, def.id)) return;
        progress.cancel(uid, name, def.id);
        player.sendMessage(msg.pref("quest_stopped").replace("%quest_name%", def.name));
    }

    public void forceComplete(UUID uuid, String playerName, String questId) {
        if (uuid == null || playerName == null || questId == null) return;
        String id = questId.toLowerCase(Locale.ROOT);
        Player p = Bukkit.getPlayer(uuid);
        QuestDef q = quests.get(id);
        int pts = q != null ? q.points : 0;
        progress.complete(uuid, playerName, id, pts);
        if (p != null && q != null) {
            actions.runAll(q, "success", p);
            p.sendMessage(msg.pref("quest_completed").replace("%quest_name%", q.name));
            runCompletionFlow(p, q);
        }
    }

    public void forceComplete(Player player, QuestDef def) {
        if (player == null || def == null) return;

        UUID uid = player.getUniqueId();
        String name = player.getName();

        progress.complete(uid, name, def.id, def.points);
        actions.runAll(def, "success", player);
        player.sendMessage(msg.pref("quest_completed").replace("%quest_name%", def.name));
        runCompletionFlow(player, def);
    }

    public void abandonAll(Player player) {
        if (player == null) return;
        progress.cancelAll(player.getUniqueId(), player.getName());
        player.sendMessage(msg.pref("abandon_all_done"));
    }

    public void listActiveTo(Player player) {
        if (player == null) return;
        UUID uid = player.getUniqueId();
        String name = player.getName();

        List<String> active = progress.activeOf(uid, name);
        if (active == null || active.isEmpty()) {
            player.sendMessage(msg.pref("list_empty"));
            return;
        }

        player.sendMessage(msg.pref("list_header"));
        StringBuilder sb = new StringBuilder(64);

        for (String id : active) {
            QuestDef def = quests.byId(id);
            if (def == null) continue;

            int value = progress.value(uid, name, id);
            int target = Math.max(1, def.amount);
            double pct = Math.min(1.0, Math.max(0.0, value / (double) target));
            int filled = (int) (pct * 20);

            sb.setLength(0);
            sb.append("§f- §a").append(def.name).append(" §7(§e")
                    .append(value).append(" / ").append(target).append("§7) ");
            sb.append("§a");
            for (int i = 0; i < filled; i++) sb.append('■');
            sb.append("§7");
            for (int i = filled; i < 20; i++) sb.append('■');

            player.sendMessage(sb.toString());
        }
    }

    public void handleNpcInteract(Player player, String targetKey) {
        if (player == null || targetKey == null || targetKey.isEmpty()) return;
        Map<String, Object> ctx = new HashMap<String, Object>();
        ctx.put("target_id", targetKey);
        handleCustom(player, "ENTITY_INTERACT", ctx);
    }

    public void handle(Player player, String eventName, Event event) {
        if (player == null || eventName == null) return;

        String key = normalizeEventKey(eventName);
        QuestDef[] list = quests.byEvent(key);
        if (list.length == 0) return;

        UUID uid = player.getUniqueId();
        if (isDedup(uid, key)) return;

        Map<String, Object> ctx = EventContextMapper.map(event);
        Object lock = playerLocks.computeIfAbsent(uid, k -> new Object());

        worker.execute(() -> {
            synchronized (lock) {
                processEventInternal(player, key, event, ctx, list);
            }
        });
    }

    public void handleCustom(Player player, String eventKey, Map<String, Object> ctx) {
        if (player == null || eventKey == null) return;

        String key = normalizeEventKey(eventKey);
        QuestDef[] list = quests.byEvent(key);
        if (list.length == 0) return;

        UUID uid = player.getUniqueId();
        if (isDedup(uid, key)) return;

        if (ctx == null) ctx = Collections.emptyMap();
        Map<String, Object> finalCtx = ctx;
        Object lock = playerLocks.computeIfAbsent(uid, k -> new Object());

        worker.execute(() -> {
            synchronized (lock) {
                if ("ENTITY_INTERACT".equalsIgnoreCase(key)) {
                    processNpcInteract(player, finalCtx, list);
                } else {
                    processCustomInternal(player, key, finalCtx, list);
                }
            }
        });
    }

    public void handleDynamic(Player player, String key, Object value) {
        if (player == null || key == null) return;
        Map<String, Object> ctx = new HashMap<String, Object>();
        if (value != null) ctx.put("value", value);
        handleCustom(player, key, ctx);
    }

    public void handleDynamic(Event event) {
        if (event == null) return;

        Player player = EventContextMapper.extractPlayer(event);
        if (player == null) {
            if (event instanceof EntityDeathEvent) {
                EntityDeathEvent de = (EntityDeathEvent) event;
                if (de.getEntity().getKiller() != null) {
                    player = de.getEntity().getKiller();
                }
            }
        }
        if (player == null) return;

        String key = guessEventKeyFromClass(event.getClass().getSimpleName());
        QuestDef[] list = quests.byEvent(key);
        if (list.length == 0) return;

        UUID uid = player.getUniqueId();
        if (isDedup(uid, key)) return;

        Map<String, Object> ctx = EventContextMapper.map(event);
        Object lock = playerLocks.computeIfAbsent(uid, k -> new Object());

        Player finalPlayer = player;
        worker.execute(() -> {
            synchronized (lock) {
                processEventInternal(finalPlayer, key, event, ctx, list);
            }
        });
    }

    public void completeQuest(Player player, String questId) {
        if (player == null || questId == null) return;
        String id = questId.toLowerCase(Locale.ROOT);

        QuestDef def = quests.byId(id);
        if (def == null) return;

        PlayerData data = progress.get(player.getUniqueId());
        if (data == null || !data.isActive(id)) return;

        data.complete(id, def.points);
        progress.save(data);

        actions.run(def, "success", player);

        if (def.nextQuestOnComplete != null && !def.nextQuestOnComplete.isEmpty()) {
            QuestDef next = quests.byId(def.nextQuestOnComplete);
            if (next != null) startQuest(player, next.id);
        }

        player.sendMessage(msg.pref("quest_completed")
                .replace("%quest_name%", def.name));
    }

    private void processEventInternal(Player player, String eventKey, Event event, Map<String, Object> ctx, QuestDef[] list) {
        UUID uid = player.getUniqueId();
        String name = player.getName();

        TargetMatcher matcher = matchers.get(eventKey.toLowerCase(Locale.ROOT));
        if (matcher == null) matcher = matchers.get("*");

        List<Runnable> pending = new ArrayList<Runnable>();

        for (QuestDef def : list) {
            if (def == null) continue;

            boolean active = progress.isActive(uid, name, def.id);

            if (!active) {
                if (def.startMode == QuestDef.StartMode.AUTO || def.startMode == QuestDef.StartMode.PUBLIC || def.startMode == QuestDef.StartMode.NPC) {
                    if (eventKey.equalsIgnoreCase("ENTITY_INTERACT")) {
                        continue;
                    }
                    if (!checkTargetMatch(player, event, matcher, def)) {
                        continue;
                    }
                    if (!checkConditions(player, event, ctx, def.condStart)) {
                        continue;
                    }
                    progress.start(uid, name, def.id);
                    actions.runAll(def, "accept", player);
                    actions.runAll(def, "start", player);
                    player.sendMessage(msg.pref("quest_started").replace("%quest_name%", def.name));
                    active = true;
                }
            }

            if (!active) continue;

            if (!checkTargetMatch(player, event, matcher, def)) {
                continue;
            }

            if (checkAnyFail(player, event, ctx, def.condFail)) {
                final String qid = def.id;
                pending.add(() -> {
                    actions.runAll(def, "fail", player);
                    progress.cancel(uid, name, qid);
                });
                continue;
            }

            if (!checkConditions(player, event, ctx, def.condSuccess)) {
                continue;
            }

            int value = progress.addProgress(uid, name, def.id, 1);
            if (value >= def.amount) {
                pending.add(() -> handleQuestCompleteOnMain(player, def));
            }
        }

        if (!pending.isEmpty()) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                for (Runnable r : pending) {
                    try {
                        r.run();
                    } catch (Throwable ignored) {
                    }
                }
            });
        }
    }

    private void processCustomInternal(Player player, String eventKey, Map<String, Object> ctx, QuestDef[] list) {
        UUID uid = player.getUniqueId();
        String name = player.getName();

        List<Runnable> pending = new ArrayList<Runnable>();

        for (QuestDef def : list) {
            if (def == null) continue;
            if (!progress.isActive(uid, name, def.id)) continue;

            if (checkAnyFail(player, null, ctx, def.condFail)) {
                final String qid = def.id;
                pending.add(() -> {
                    actions.runAll(def, "fail", player);
                    progress.cancel(uid, name, qid);
                });
                continue;
            }

            if (!checkConditions(player, null, ctx, def.condSuccess)) {
                continue;
            }

            pending.add(() -> handleQuestCompleteOnMain(player, def));
        }

        if (!pending.isEmpty()) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                for (Runnable r : pending) {
                    try {
                        r.run();
                    } catch (Throwable ignored) {
                    }
                }
            });
        }
    }

    private void processNpcInteract(Player player, Map<String, Object> ctx, QuestDef[] list) {
        UUID uid = player.getUniqueId();
        String name = player.getName();

        String targetId = String.valueOf(ctx.get("target_id"));
        if (targetId == null) targetId = "";
        targetId = targetId.trim();
        if (targetId.isEmpty()) return;

        QuestDef candidate = null;

        for (QuestDef def : list) {
            if (def == null) continue;
            if (def.startMode != QuestDef.StartMode.PUBLIC && def.startMode != QuestDef.StartMode.NPC) continue;

            boolean matched;
            if (!def.hasTarget()) {
                matched = true;
            } else {
                matched = def.matchesTarget(targetId);
            }
            if (!matched) continue;

            candidate = def;
            break;
        }

        if (candidate == null) return;

        long now = System.nanoTime();
        NpcArmState arm = npcArm.get(uid);

        boolean active = progress.isActive(uid, name, candidate.id);
        boolean completed = progress.isCompleted(uid, name, candidate.id);

        if (arm != null && arm.questId.equalsIgnoreCase(candidate.id) && arm.until > now) {
            if (!completed) {
                if (!checkAnyFail(player, null, ctx, candidate.condFail) && checkConditions(player, null, ctx, candidate.condSuccess)) {
                    QuestDef finalCandidate = candidate;
                    Bukkit.getScheduler().runTask(plugin, () -> handleQuestCompleteOnMain(player, finalCandidate));
                }
            }
            npcArm.remove(uid);
            return;
        }

        if (!active && !completed) {
            if (!checkConditions(player, null, ctx, candidate.condStart)) {
                return;
            }
            progress.start(uid, name, candidate.id);
            actions.runAll(candidate, "accept", player);
            actions.runAll(candidate, "start", player);
            player.sendMessage(msg.pref("quest_started").replace("%quest_name%", candidate.name));
        }

        npcArm.put(uid, new NpcArmState(candidate.id, now + NPC_ARM_WINDOW_NANOS));
    }

    private void handleQuestCompleteOnMain(Player player, QuestDef def) {
        UUID uid = player.getUniqueId();
        String name = player.getName();

        actions.runAll(def, "success", player);
        progress.complete(uid, name, def.id, def.points);
        player.sendMessage(msg.pref("quest_completed").replace("%quest_name%", def.name));

        runCompletionFlow(player, def);
    }

    private void runCompletionFlow(Player player, QuestDef def) {
        String nextId = resolveNextId(def);

        if (nextId != null && !nextId.isEmpty()) {
            QuestDef next = quests.byId(nextId);
            if (next != null) {
                if (isBoardQuest(next)) {
                    player.sendMessage(
                            msg.pref("quest_chain_board")
                                    .replace("%current%", def.name)
                                    .replace("%next%", next.name)
                    );
                } else {
                    player.sendMessage(
                            msg.pref("quest_chain")
                                    .replace("%current%", def.name)
                                    .replace("%next%", next.name)
                    );
                    startQuest(player, next);
                }
            } else {
                player.sendMessage(msg.pref("quest_chain_end"));
            }
        }

        if (def.repeat < 0) {
            if (isBoardQuest(def)) {
                player.sendMessage(
                        msg.pref("quest_board_repeat").replace("%quest_name%", def.name)
                );
            } else {
                Supplier<Boolean> started = () -> {
                    if (progress.isActive(player.getUniqueId(), player.getName(), def.id)) return Boolean.FALSE;
                    progress.start(player.getUniqueId(), player.getName(), def.id);
                    actions.runAll(def, "restart", player);
                    actions.runAll(def, "repeat", player);
                    return Boolean.TRUE;
                };
                started.get();
            }
        }
    }

    private String resolveNextId(QuestDef def) {
        if (def == null) return null;

        if (def.nextQuestOnComplete != null && !def.nextQuestOnComplete.isEmpty()) {
            return def.nextQuestOnComplete;
        }

        if (def.actions != null) {
            List<String> list = def.actions.get("next");
            if (list != null && !list.isEmpty()) {
                String raw = list.get(0);
                if (raw != null) {
                    String s = raw.trim();
                    if (!s.isEmpty()) {
                        int sp = s.indexOf(' ');
                        return sp > 0 ? s.substring(0, sp) : s;
                    }
                }
            }
        }
        return null;
    }

    private boolean isBoardQuest(QuestDef def) {
        return def != null && def.isPublic;
    }

    private boolean allowBoardStartContext(Player player) {
        return true;
    }

    private boolean checkConditions(Player player, Event event, Map<String, Object> ctx, List<String> list) {
        if (list == null || list.isEmpty()) return true;
        for (String expr : list) {
            if (!cachedEval(player, event, ctx, expr)) {
                return false;
            }
        }
        return true;
    }

    private boolean checkAnyFail(Player player, Event event, Map<String, Object> ctx, List<String> list) {
        if (list == null || list.isEmpty()) return false;
        for (String expr : list) {
            if (cachedEval(player, event, ctx, expr)) {
                return true;
            }
        }
        return false;
    }

    private boolean cachedEval(Player player, Event event, Map<String, Object> ctx, String expr) {
        if (expr == null || expr.isEmpty()) return true;
        String key = player.getUniqueId() + "|" + expr;
        long now = System.nanoTime();
        BoolCacheEntry ent = conditionCache.get(key);
        if (ent != null && ent.expireAt > now) {
            return ent.value;
        }
        boolean val = ConditionEvaluator.eval(player, event, ctx, expr);
        conditionCache.put(key, new BoolCacheEntry(val, now + conditionTtlNanos));
        return val;
    }

    private boolean isDedup(UUID uid, String key) {
        long now = System.nanoTime();
        Map<String, Long> m = recentEventWindow.computeIfAbsent(uid, k -> new ConcurrentHashMap<String, Long>());
        Long last = m.get(key);
        if (last != null && now - last < dedupWindowNanos) return true;
        m.put(key, now);
        return false;
    }

    private boolean checkTargetMatch(Player player, Event event, TargetMatcher matcher, QuestDef def) {
        if (!def.hasTarget()) return true;
        if (matcher == null) return true;
        for (String t : def.targets) {
            if (matcher.test(player, event, t)) {
                return true;
            }
        }
        return false;
    }

    private void installDefaultMatchers() {
        matchers.put("*", (player, event, target) -> true);

        matchers.put("block_break", (player, event, target) -> {
            if (!(event instanceof BlockBreakEvent)) return false;
            BlockBreakEvent be = (BlockBreakEvent) event;
            if (target == null || target.isEmpty()) return true;
            return tokenAnyMatch(be.getBlock().getType().name(), target);
        });

        matchers.put("block_place", (player, event, target) -> {
            if (!(event instanceof BlockPlaceEvent)) return false;
            BlockPlaceEvent bp = (BlockPlaceEvent) event;
            if (target == null || target.isEmpty()) return true;
            return tokenAnyMatch(bp.getBlockPlaced().getType().name(), target);
        });

        matchers.put("entity_kill", (player, event, target) -> {
            if (!(event instanceof EntityDeathEvent)) return false;
            EntityDeathEvent de = (EntityDeathEvent) event;
            if (target == null || target.isEmpty()) return true;
            return tokenAnyMatch(de.getEntity().getType().name(), target);
        });

        matchers.put("entity_interact", TargetMatchers.ENTITY_INTERACT_MATCHER);

        matchers.put("player_command", (player, event, target) -> {
            if (!(event instanceof PlayerCommandPreprocessEvent)) return false;
            PlayerCommandPreprocessEvent ce = (PlayerCommandPreprocessEvent) event;
            if (target == null || target.isEmpty()) return true;
            return ce.getMessage().toLowerCase(Locale.ROOT).startsWith("/" + target.toLowerCase(Locale.ROOT));
        });

        matchers.put("player_chat", (player, event, target) -> {
            if (!(event instanceof AsyncPlayerChatEvent)) return false;
            AsyncPlayerChatEvent ce = (AsyncPlayerChatEvent) event;
            if (target == null || target.isEmpty()) return true;
            return ce.getMessage().toLowerCase(Locale.ROOT).contains(target.toLowerCase(Locale.ROOT));
        });
    }

    private static boolean tokenAnyMatch(String value, String target) {
        String v = value.toUpperCase(Locale.ROOT);
        String[] parts = target.split("\\|");
        for (String tok : parts) {
            tok = tok.trim();
            if (tok.isEmpty()) continue;
            boolean neg = tok.charAt(0) == '!';
            if (neg) tok = tok.substring(1).trim();
            String up = tok.toUpperCase(Locale.ROOT);
            boolean eq = v.equals(up);
            if (neg && eq) return false;
            if (!neg && eq) return true;
        }
        return false;
    }

    private void preloadInternalQuests() {
        try {
            quests.reload();
            quests.rebuildEventMap();
        } catch (Throwable t) {
            plugin.getLogger().warning("[QuestEngine] Internal quest load failed: " + t.getMessage());
        }
    }

    private void scheduleDailyResets() {
        Map<String, List<String>> timeToQuestIds = new HashMap<String, List<String>>();
        String defaultTime = plugin.getConfig().getString("reset.default-time", "04:00");

        for (String id : quests.ids()) {
            QuestDef def = quests.byId(id);
            if (def == null || def.reset == null) continue;
            if (!"DAILY".equalsIgnoreCase(def.reset.policy)) continue;
            String at = (def.reset.time == null || def.reset.time.isEmpty()) ? defaultTime : def.reset.time;
            List<String> list = timeToQuestIds.computeIfAbsent(at, k -> new ArrayList<String>());
            list.add(id);
        }

        for (Map.Entry<String, List<String>> e : timeToQuestIds.entrySet()) {
            String time = e.getKey();
            long delayMs = millisUntil(time);
            long periodMs = 24L * 60L * 60L * 1000L;
            List<String> copy = Collections.unmodifiableList(new ArrayList<String>(e.getValue()));

            Bukkit.getScheduler().runTaskTimerAsynchronously(
                    plugin,
                    () -> {
                        for (Player p : Bukkit.getOnlinePlayers()) {
                            UUID uid = p.getUniqueId();
                            String name = p.getName();
                            for (String qid : copy) {
                                progress.reset(uid, name, qid);
                            }
                        }
                        plugin.getLogger().info("[QuestEngine] Daily reset done at " + time);
                    },
                    delayMs / 50L,
                    periodMs / 50L
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
        } catch (Throwable ignored) {
        }
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime next = now.withHour(h).withMinute(m).withSecond(0).withNano(0);
        if (!next.isAfter(now)) next = next.plusDays(1);
        return Duration.between(now, next).toMillis();
    }

    private String normalizeEventKey(String key) {
        if (key == null) return "";
        return key.trim().toUpperCase(Locale.ROOT);
    }

    private String guessEventKeyFromClass(String simpleName) {
        if (simpleName == null) return "";
        String k = simpleName;

        if (k.endsWith("Event")) k = k.substring(0, k.length() - "Event".length());

        if (k.equalsIgnoreCase("PlayerInteractEntity")) return "ENTITY_INTERACT";
        if (k.equalsIgnoreCase("PlayerDropItem")) return "ITEM_DROP";
        if (k.equalsIgnoreCase("PlayerPickupItem")) return "ITEM_PICKUP";
        if (k.equalsIgnoreCase("EntityDeath")) return "MOBKILLING";

        k = k.replace("MythicMob", "MYTHICMOBS_")
                .replace("Player", "PLAYER_")
                .replace("Entity", "ENTITY_")
                .replace("Block", "BLOCK_")
                .replace("Inventory", "INVENTORY_")
                .replace("Item", "ITEM_");

        return k.toUpperCase(Locale.ROOT);
    }
}
