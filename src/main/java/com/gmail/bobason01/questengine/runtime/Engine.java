package com.gmail.bobason01.questengine.runtime;

import com.gmail.bobason01.questengine.QuestEnginePlugin;
import com.gmail.bobason01.questengine.action.ActionExecutor;
import com.gmail.bobason01.questengine.progress.PlayerData;
import com.gmail.bobason01.questengine.progress.ProgressRepository;
import com.gmail.bobason01.questengine.quest.CustomEventData;
import com.gmail.bobason01.questengine.quest.QuestDef;
import com.gmail.bobason01.questengine.quest.QuestRepository;
import com.gmail.bobason01.questengine.util.Msg;
import io.lumine.mythic.bukkit.events.MythicMobDeathEvent; // MythicMobs 이벤트 임포트 추가
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;

public final class Engine {

    private final QuestEnginePlugin plugin;
    private final QuestRepository quests;
    private final ProgressRepository progress;
    private final ActionExecutor actions;
    private final Msg msg;
    private final ExecutorService worker;

    // --- Performance Optimizations ---
    private final Map<UUID, Object> playerLocks = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, Long>> recentEventWindow = new ConcurrentHashMap<>();

    // Parsed Target Cache (Avoids String.split on hot paths)
    private final Map<String, Set<String>> tokenCache = new ConcurrentHashMap<>();

    // MethodHandle Cache (Replaces slow reflection)
    private static final Map<String, MethodHandle[]> CHAIN_CACHE = new ConcurrentHashMap<>();
    private static final MethodHandles.Lookup LOOKUP = MethodHandles.publicLookup();

    private final Map<String, TargetMatcher> matchers = new ConcurrentHashMap<>();
    private final Map<String, BoolCacheEntry> conditionCache = new ConcurrentHashMap<>();

    private final long conditionTtlNanos;
    private final long dedupWindowNanos;
    private final boolean hasPapi;

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

    private final Map<UUID, NpcArmState> npcArm = new ConcurrentHashMap<>();
    private final Map<String, QuestDef[]> customEventIndex = new ConcurrentHashMap<>();

    @FunctionalInterface
    public interface TargetMatcher {
        boolean test(Player player, Event event, String rawTarget);
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

        // Cache Plugin Status
        this.hasPapi = Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI");

        long ttlMs = Math.max(50L, plugin.getConfig().getLong("performance.condition-cache-ttl-ms", 300L));
        this.conditionTtlNanos = ttlMs * 1_000_000L;

        long dedupMs = Math.max(3L, plugin.getConfig().getLong("performance.event-dedup-window-ms", 10L));
        this.dedupWindowNanos = dedupMs * 1_000_000L;

        installDefaultMatchers();
        scheduleDailyResets();
        preloadInternalQuests();
    }

    public QuestRepository quests() { return quests; }
    public ProgressRepository progress() { return progress; }
    public ActionExecutor actions() { return actions; }
    public Msg msg() { return msg; }
    public ExecutorService asyncPool() { return worker; }

    private String format(Player p, String raw) {
        if (raw == null) return "";
        if (hasPapi && p != null) {
            raw = PlaceholderAPI.setPlaceholders(p, raw);
        }
        return ChatColor.translateAlternateColorCodes('&', raw);
    }

    public void refreshEventCache() {
        quests.reload();
        quests.rebuildEventMap();
        tokenCache.clear();
        CHAIN_CACHE.clear();
        rebuildCustomEventIndex();
    }

    public void shutdown() {
        try { worker.shutdownNow(); } catch (Throwable ignored) {}
        conditionCache.clear();
        playerLocks.clear();
        recentEventWindow.clear();
        matchers.clear();
        npcArm.clear();
        customEventIndex.clear();
        tokenCache.clear();
        CHAIN_CACHE.clear();
    }

    public void startQuest(Player p, String questId) {
        if (p == null || questId == null) return;
        String id = questId.toLowerCase(Locale.ROOT);
        QuestDef q = quests.get(id);
        if (q == null) {
            p.sendMessage(format(p, msg.pref("invalid_args")));
            return;
        }
        startQuest(p, q);
    }

    public void startQuest(Player player, QuestDef def) {
        if (player == null || def == null) return;

        UUID uid = player.getUniqueId();
        String name = player.getName();

        if (!progress.canStart(uid, name, def)) {
            player.sendMessage(format(player, msg.pref("quest_no_repeat").replace("%quest_name%", def.name)));
            return;
        }

        if (progress.isActive(uid, name, def.id)) {
            player.sendMessage(format(player, msg.pref("quest_already_active")));
            return;
        }

        if (isBoardQuest(def) && !allowBoardStartContext(player)) {
            player.sendMessage(format(player, msg.pref("quest_board_only")));
            return;
        }

        progress.start(uid, name, def);
        actions.runAll(def, "accept", player);
        actions.runAll(def, "start", player);
        player.sendMessage(format(player, msg.pref("quest_started").replace("%quest_name%", def.name)));
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
            player.sendMessage(format(player, msg.pref("quest_not_active")));
            return;
        }

        progress.cancel(uid, name, def.id);
        actions.runAll(def, "cancel", player);
        player.sendMessage(format(player, msg.pref("quest_canceled").replace("%quest_name%", def.name)));
    }

    public void stopQuest(UUID uuid, String playerName, String questId) {
        if (uuid == null || playerName == null || questId == null) return;
        String id = questId.toLowerCase(Locale.ROOT);
        progress.cancel(uuid, playerName, id);
        Player p = Bukkit.getPlayer(uuid);
        if (p != null && p.isOnline()) {
            QuestDef q = quests.get(id);
            if (q != null) actions.runAll(q, "cancel", p);
            p.sendMessage(format(p, msg.pref("quest_stopped").replace("%quest_name%", q != null ? q.name : id)));
        }
    }

    public void stopQuest(Player player, QuestDef def) {
        if (player == null || def == null) return;
        UUID uid = player.getUniqueId();
        String name = player.getName();
        if (!progress.isActive(uid, name, def.id)) return;
        progress.cancel(uid, name, def.id);
        player.sendMessage(format(player, msg.pref("quest_stopped").replace("%quest_name%", def.name)));
    }

    public void forceComplete(UUID uuid, String playerName, String questId) {
        if (uuid == null || playerName == null || questId == null) return;
        String id = questId.toLowerCase(Locale.ROOT);
        Player p = Bukkit.getPlayer(uuid);
        QuestDef q = quests.get(id);
        if (q != null) {
            progress.complete(uuid, playerName, q);
            if (p != null) {
                actions.runAll(q, "success", p);
                p.sendMessage(format(p, msg.pref("quest_completed").replace("%quest_name%", q.name)));
                runCompletionFlow(p, q);
            }
        } else {
            progress.complete(uuid, playerName, id, 0);
        }
    }

    public void forceComplete(Player player, QuestDef def) {
        if (player == null || def == null) return;

        UUID uid = player.getUniqueId();
        String name = player.getName();

        progress.complete(uid, name, def);
        actions.runAll(def, "success", player);
        player.sendMessage(format(player, msg.pref("quest_completed").replace("%quest_name%", def.name)));
        runCompletionFlow(player, def);
    }

    public void abandonAll(Player player) {
        if (player == null) return;
        progress.cancelAll(player.getUniqueId(), player.getName());
        player.sendMessage(format(player, msg.pref("abandon_all_done")));
    }

    public void listActiveTo(Player player) {
        if (player == null) return;
        UUID uid = player.getUniqueId();
        String name = player.getName();

        List<String> active = progress.activeOf(uid, name);
        if (active == null || active.isEmpty()) {
            player.sendMessage(format(player, msg.pref("list_empty")));
            return;
        }

        player.sendMessage(format(player, msg.pref("list_header")));
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

            player.sendMessage(format(player, sb.toString()));
        }
    }

    public void handleNpcInteract(Player player, String targetKey) {
        if (player == null || targetKey == null || targetKey.isEmpty()) return;
        Map<String, Object> ctx = new HashMap<>();
        ctx.put("target_id", targetKey);
        handleCustom(player, "ENTITY_INTERACT", ctx);
    }

    // --- Core Logic Optimized ---

    public void handle(Player player, String eventName, Event event) {
        if (player == null || eventName == null) return;

        String key = normalizeEventKey(eventName);
        // Fail Fast: Check if any quest listens to this event
        QuestDef[] list = quests.byEvent(key);
        if (list == null || list.length == 0) return;

        UUID uid = player.getUniqueId();
        if (isDedup(uid, key)) return;

        // Optimization: Only create Map if we passed the check
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
        if (list == null || list.length == 0) return;

        UUID uid = player.getUniqueId();
        if (isDedup(uid, key)) return;

        Map<String, Object> finalCtx = (ctx == null) ? Collections.emptyMap() : ctx;
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
        Map<String, Object> ctx = new HashMap<>();
        if (value != null) ctx.put("value", value);
        handleCustom(player, key, ctx);
    }

    public void handleDynamic(Event event) {
        if (event == null) return;

        Class<?> clazz = event.getClass();
        String className = clazz.getName();
        QuestDef[] customDefs = customEventIndex.get(className);

        if (customDefs != null && customDefs.length > 0) {
            handleCustomDynamic(event, customDefs);
            return;
        }

        Player player = EventContextMapper.extractPlayer(event);
        if (player == null && event instanceof EntityDeathEvent de) {
            player = de.getEntity().getKiller();
        }
        if (player == null) return;

        String key = guessEventKeyFromClass(clazz.getSimpleName());
        QuestDef[] list = quests.byEvent(key);
        if (list == null || list.length == 0) return;

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

        data.complete(id, def.points, def.repeat);
        progress.save(data);

        actions.run(def, "success", player);

        if (def.nextQuestOnComplete != null && !def.nextQuestOnComplete.isEmpty()) {
            QuestDef next = quests.byId(def.nextQuestOnComplete);
            if (next != null) startQuest(player, next.id);
        }

        player.sendMessage(format(player, msg.pref("quest_completed")
                .replace("%quest_name%", def.name)));
    }

    // --- Internal Processing ---

    private void processEventInternal(Player player, String eventKey, Event event, Map<String, Object> ctx, QuestDef[] list) {
        UUID uid = player.getUniqueId();
        String name = player.getName();

        TargetMatcher matcher = matchers.getOrDefault(eventKey.toLowerCase(Locale.ROOT), matchers.get("*"));
        List<Runnable> pending = null;

        for (QuestDef def : list) {
            if (def == null) continue;

            boolean active = progress.isActive(uid, name, def.id);

            // Filter 1: Can we start it?
            if (!active) {
                if (def.startMode != QuestDef.StartMode.AUTO) continue;
                if (!progress.canStart(uid, name, def)) continue;

                if (!checkTargetMatch(player, event, matcher, def)) continue;
                if (!checkConditions(player, event, ctx, def.condStart)) continue;

                progress.start(uid, name, def);
                actions.runAll(def, "accept", player);
                actions.runAll(def, "start", player);
                player.sendMessage(format(player, msg.pref("quest_started").replace("%quest_name%", def.name)));
                active = true;
            }

            if (!active) continue;

            // Filter 2: Progress Logic
            if (!checkTargetMatch(player, event, matcher, def)) continue;

            if (checkAnyFail(player, event, ctx, def.condFail)) {
                final String qid = def.id;
                if (pending == null) pending = new ArrayList<>();
                pending.add(() -> {
                    actions.runAll(def, "fail", player);
                    progress.cancel(uid, name, qid);
                });
                continue;
            }

            if (!checkConditions(player, event, ctx, def.condSuccess)) continue;

            int value = progress.addProgress(uid, name, def.id, 1);
            if (value >= def.amount) {
                if (pending == null) pending = new ArrayList<>();
                pending.add(() -> handleQuestCompleteOnMain(player, def));
            }
        }

        if (pending != null) {
            scheduleMain(pending);
        }
    }

    private void processCustomInternal(Player player, String eventKey, Map<String, Object> ctx, QuestDef[] list) {
        UUID uid = player.getUniqueId();
        String name = player.getName();
        List<Runnable> pending = null;

        for (QuestDef def : list) {
            if (def == null) continue;
            if (!progress.isActive(uid, name, def.id)) continue;

            if (checkAnyFail(player, null, ctx, def.condFail)) {
                final String qid = def.id;
                if (pending == null) pending = new ArrayList<>();
                pending.add(() -> {
                    actions.runAll(def, "fail", player);
                    progress.cancel(uid, name, qid);
                });
                continue;
            }

            if (!checkConditions(player, null, ctx, def.condSuccess)) continue;

            if (pending == null) pending = new ArrayList<>();
            pending.add(() -> handleQuestCompleteOnMain(player, def));
        }

        if (pending != null) {
            scheduleMain(pending);
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

            if (!progress.canStart(uid, name, def) && !progress.isActive(uid, name, def.id)) {
                continue;
            }

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
            if (!progress.canStart(uid, name, candidate)) return;
            if (!checkConditions(player, null, ctx, candidate.condStart)) return;

            progress.start(uid, name, candidate);
            actions.runAll(candidate, "accept", player);
            actions.runAll(candidate, "start", player);
            player.sendMessage(format(player, msg.pref("quest_started").replace("%quest_name%", candidate.name)));
        }

        npcArm.put(uid, new NpcArmState(candidate.id, now + NPC_ARM_WINDOW_NANOS));
    }

    private void handleQuestCompleteOnMain(Player player, QuestDef def) {
        UUID uid = player.getUniqueId();
        String name = player.getName();

        actions.runAll(def, "success", player);
        progress.complete(uid, name, def);
        player.sendMessage(format(player, msg.pref("quest_completed").replace("%quest_name%", def.name)));

        runCompletionFlow(player, def);
    }

    private void runCompletionFlow(Player player, QuestDef def) {
        String nextId = resolveNextId(def);

        if (nextId != null && !nextId.isEmpty()) {
            QuestDef next = quests.byId(nextId);
            if (next != null) {
                if (isBoardQuest(next)) {
                    player.sendMessage(format(player,
                            msg.pref("quest_chain_board")
                                    .replace("%current%", def.name)
                                    .replace("%next%", next.name)
                    ));
                } else {
                    player.sendMessage(format(player,
                            msg.pref("quest_chain")
                                    .replace("%current%", def.name)
                                    .replace("%next%", next.name)
                    ));
                    startQuest(player, next);
                }
            } else {
                player.sendMessage(format(player, msg.pref("quest_chain_end")));
            }
        }

        if (def.repeat < 0) {
            if (isBoardQuest(def)) {
                player.sendMessage(format(player,
                        msg.pref("quest_board_repeat").replace("%quest_name%", def.name)
                ));
            } else {
                Supplier<Boolean> started = () -> {
                    UUID uid = player.getUniqueId();
                    String name = player.getName();
                    if (progress.isActive(uid, name, def.id)) return Boolean.FALSE;
                    if (!progress.canStart(uid, name, def)) return Boolean.FALSE;
                    progress.start(uid, name, def);
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
        Map<String, Long> m = recentEventWindow.computeIfAbsent(uid, k -> new ConcurrentHashMap<>());
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

    // --- Optimized Matchers with Token Cache ---

    private Set<String> getParsedTokens(String target) {
        return tokenCache.computeIfAbsent(target, t -> {
            Set<String> set = new HashSet<>();
            for (String s : t.split("\\|")) {
                String trim = s.trim();
                if (!trim.isEmpty()) set.add(trim.toUpperCase(Locale.ROOT));
            }
            return set;
        });
    }

    private boolean checkTokens(String value, String rawTarget) {
        if (rawTarget == null || rawTarget.isEmpty()) return true;
        Set<String> tokens = getParsedTokens(rawTarget);
        if (tokens.contains(value)) return true;

        if (rawTarget.contains("!")) {
            return tokenAnyMatchLegacy(value, rawTarget);
        }
        return false;
    }

    private static boolean tokenAnyMatchLegacy(String value, String target) {
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

    private void installDefaultMatchers() {
        matchers.put("*", (player, event, target) -> true);

        matchers.put("block_break", (player, event, target) -> {
            if (!(event instanceof BlockBreakEvent)) return false;
            String type = ((BlockBreakEvent) event).getBlock().getType().name();
            return checkTokens(type, target);
        });

        matchers.put("block_place", (player, event, target) -> {
            if (!(event instanceof BlockPlaceEvent)) return false;
            String type = ((BlockPlaceEvent) event).getBlockPlaced().getType().name();
            return checkTokens(type, target);
        });

        matchers.put("entity_kill", (player, event, target) -> {
            if (!(event instanceof EntityDeathEvent)) return false;
            String type = ((EntityDeathEvent) event).getEntity().getType().name();
            return checkTokens(type, target);
        });

        matchers.put("mythicmobs_entity_kill", (player, event, target) -> {
            if (event instanceof MythicMobDeathEvent) {
                String id = ((MythicMobDeathEvent) event).getMobType().getInternalName();
                return checkTokens(id, target);
            }
            return false;
        });

        matchers.put("player_command", (player, event, target) -> {
            if (!(event instanceof PlayerCommandPreprocessEvent)) return false;
            String msg = ((PlayerCommandPreprocessEvent) event).getMessage().toLowerCase(Locale.ROOT);
            return msg.startsWith("/" + target.toLowerCase(Locale.ROOT));
        });

        matchers.put("player_chat", (player, event, target) -> {
            if (!(event instanceof AsyncPlayerChatEvent)) return false;
            String msg = ((AsyncPlayerChatEvent) event).getMessage().toLowerCase(Locale.ROOT);
            return msg.contains(target.toLowerCase(Locale.ROOT));
        });
    }

    private void preloadInternalQuests() {
        try {
            refreshEventCache();
        } catch (Throwable t) {
            plugin.getLogger().warning("[QuestEngine] Internal quest load failed: " + t.getMessage());
        }
    }

    private void scheduleDailyResets() {
        Map<String, List<String>> timeToQuestIds = new HashMap<>();
        String defaultTime = plugin.getConfig().getString("reset.default-time", "04:00");

        for (String id : quests.ids()) {
            QuestDef def = quests.byId(id);
            if (def == null || def.reset == null) continue;
            if (!"DAILY".equalsIgnoreCase(def.reset.policy)) continue;
            String at = (def.reset.time == null || def.reset.time.isEmpty()) ? defaultTime : def.reset.time;
            List<String> list = timeToQuestIds.computeIfAbsent(at, k -> new ArrayList<>());
            list.add(id);
        }

        for (Map.Entry<String, List<String>> e : timeToQuestIds.entrySet()) {
            String time = e.getKey();
            long delayMs = millisUntil(time);
            long periodMs = 24L * 60L * 60L * 1000L;
            List<String> copy = Collections.unmodifiableList(new ArrayList<>(e.getValue()));

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
        } catch (Throwable ignored) {}
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

    private void rebuildCustomEventIndex() {
        customEventIndex.clear();
        Map<String, List<QuestDef>> tmp = new HashMap<>();
        for (QuestDef def : quests.all()) {
            if (def == null) continue;
            CustomEventData c = def.custom;
            if (c == null) continue;
            String evt = c.eventClass;
            if (evt == null) continue;
            evt = evt.trim();
            if (evt.isEmpty()) continue;
            List<QuestDef> list = tmp.computeIfAbsent(evt, k -> new ArrayList<>());
            list.add(def);
        }
        for (Map.Entry<String, List<QuestDef>> e : tmp.entrySet()) {
            List<QuestDef> list = e.getValue();
            customEventIndex.put(e.getKey(), list.toArray(new QuestDef[0]));
        }
    }

    private void handleCustomDynamic(Event event, QuestDef[] defsForClass) {
        Map<String, Object> baseCtx = null;

        for (QuestDef def : defsForClass) {
            if (def == null || def.custom == null) continue;
            Player p = resolveCustomPlayer(event, def.custom);
            if (p == null) continue;

            if (baseCtx == null) baseCtx = EventContextMapper.map(event);

            UUID uid = p.getUniqueId();
            Object lock = playerLocks.computeIfAbsent(uid, k -> new Object());
            Map<String, Object> fCtx = baseCtx;
            Player finalP = p;

            worker.execute(() -> {
                synchronized (lock) {
                    Map<String, Object> ctx = new HashMap<>(fCtx);
                    applyCustomCaptures(event, def.custom, ctx);
                    String eventKey = def.event;
                    QuestDef[] single = new QuestDef[]{def};
                    processEventInternal(finalP, eventKey, event, ctx, single);
                }
            });
        }
    }

    // --- MethodHandle Optimization ---

    private Player resolveCustomPlayer(Event event, CustomEventData data) {
        if (data == null) {
            return EventContextMapper.extractPlayer(event);
        }
        String getter = data.playerGetter;
        if (getter == null || getter.isEmpty()) {
            return EventContextMapper.extractPlayer(event);
        }
        Object o = evalChain(event, getter);
        if (o instanceof Player) {
            return (Player) o;
        }
        return EventContextMapper.extractPlayer(event);
    }

    private void applyCustomCaptures(Event event, CustomEventData data, Map<String, Object> ctx) {
        if (data == null || data.captures == null || data.captures.isEmpty()) {
            return;
        }
        for (Map.Entry<String, String> entry : data.captures.entrySet()) {
            String key = entry.getKey();
            String chain = entry.getValue();
            if (key == null || key.isEmpty() || chain == null || chain.isEmpty()) {
                continue;
            }
            Object val = evalChain(event, chain);
            if (val != null) {
                ctx.put(key, val);
            }
        }
    }

    private Object evalChain(Object root, String chain) {
        if (root == null || chain == null || chain.isEmpty()) {
            return null;
        }

        String cacheKey = root.getClass().getName() + "#" + chain;
        MethodHandle[] handles = CHAIN_CACHE.get(cacheKey);

        try {
            if (handles == null) {
                String[] parts = chain.split("\\.");
                List<MethodHandle> list = new ArrayList<>();
                Class<?> current = root.getClass();

                for (String part : parts) {
                    String p = part.trim();
                    if (p.isEmpty()) return null;

                    String name = p;
                    int idx = p.indexOf('(');
                    if (idx >= 0) {
                        name = p.substring(0, idx).trim();
                    }

                    Method m = findNoArgMethod(current, name);
                    if (m == null) return null;

                    m.setAccessible(true);
                    list.add(LOOKUP.unreflect(m));
                    current = m.getReturnType();
                }
                handles = list.toArray(new MethodHandle[0]);
                CHAIN_CACHE.put(cacheKey, handles);
            }

            Object current = root;
            for (MethodHandle mh : handles) {
                if (current == null) return null;
                current = mh.invoke(current);
            }
            return current;

        } catch (Throwable ex) {
            return null;
        }
    }

    private Method findNoArgMethod(Class<?> type, String name) {
        for (Method m : type.getMethods()) {
            if (!m.getName().equals(name)) {
                continue;
            }
            if (m.getParameterCount() != 0) {
                continue;
            }
            return m;
        }
        return null;
    }

    private void scheduleMain(List<Runnable> tasks) {
        if (tasks == null || tasks.isEmpty()) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            for (Runnable r : tasks) {
                try {
                    r.run();
                } catch (Throwable ignored) {}
            }
        });
    }
}