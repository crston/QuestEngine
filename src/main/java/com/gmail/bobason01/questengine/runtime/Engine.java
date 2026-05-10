package com.gmail.bobason01.questengine.runtime;

import com.gmail.bobason01.questengine.QuestEnginePlugin;
import com.gmail.bobason01.questengine.action.ActionExecutor;
import com.gmail.bobason01.questengine.party.PartyHook;
import com.gmail.bobason01.questengine.progress.ProgressRepository;
import com.gmail.bobason01.questengine.quest.CustomEventData;
import com.gmail.bobason01.questengine.quest.QuestDef;
import com.gmail.bobason01.questengine.quest.QuestRepository;
import com.gmail.bobason01.questengine.util.Msg;
import io.lumine.mythic.bukkit.events.MythicMobDeathEvent;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Engine {

    private final QuestEnginePlugin plugin;
    private final QuestRepository quests;
    private final ProgressRepository progress;
    private final ActionExecutor actions;
    private final Msg msg;
    private final ExecutorService worker;

    private final Map<UUID, Object> playerLocks = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, Long>> recentEventWindow = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> tokenCache = new ConcurrentHashMap<>();
    private final Map<String, BoolCacheEntry> conditionCache = new ConcurrentHashMap<>();

    private static final Map<String, MethodHandle[]> CHAIN_CACHE = new ConcurrentHashMap<>();
    private static final MethodHandles.Lookup LOOKUP = MethodHandles.publicLookup();

    private final Map<UUID, NpcArmState> npcArm = new ConcurrentHashMap<>();
    private final Map<String, QuestDef[]> customEventIndex = new ConcurrentHashMap<>();

    private final Map<String, TargetMatcher> matchers = new ConcurrentHashMap<>();

    private final long conditionTtlNanos;
    private final long dedupWindowNanos;
    private final boolean hasPapi;
    private final int partyRadius;

    private static final long NPC_ARM_WINDOW_NANOS = 2_000_000_000L;
    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");

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

        this.hasPapi = Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI");
        this.partyRadius = plugin.getConfig().getInt("party.share-radius", 50);

        long ttlMs = Math.max(50L, plugin.getConfig().getLong("performance.condition-cache-ttl-ms", 300L));
        this.conditionTtlNanos = ttlMs * 1_000_000L;

        long dedupMs = Math.max(3L, plugin.getConfig().getLong("performance.event-dedup-window-ms", 10L));
        this.dedupWindowNanos = dedupMs * 1_000_000L;

        installDefaultMatchers();
        scheduleDailyResets();
        preloadInternalQuests();
    }

    // 핵심 로직

    // 유저 퇴장 시 메모리 누수 방지용 캐시 정리 추가됨
    public void cleanupPlayer(UUID uid) {
        if (uid == null) return;
        playerLocks.remove(uid);
        recentEventWindow.remove(uid);
        npcArm.remove(uid);
        // conditionCache는 UUID별로 복잡하게 엮여있으므로 TTL에 의해 자연 소멸되거나 key가 uid로 시작하는 것을 지울 수 있습니다
        conditionCache.keySet().removeIf(k -> k.startsWith(uid.toString()));
    }

    private boolean checkRequirements(UUID uid, String name, QuestDef def) {
        if (def.requiredQuests == null || def.requiredQuests.isEmpty()) return true;
        for (String reqId : def.requiredQuests) {
            if (!progress.isCompleted(uid, name, reqId)) return false;
        }
        return true;
    }

    public void handle(Player player, String eventName, Event event) {
        if (player == null || eventName == null) return;

        String key = normalizeEventKey(eventName);
        QuestDef[] list = quests.byEvent(key);
        if (list == null || list.length == 0) return;

        UUID uid = player.getUniqueId();
        if (isDedup(uid, key)) return;

        Collection<Player> partyMembers = PartyHook.membersNear(player, partyRadius);
        if (partyMembers == null || partyMembers.isEmpty()) {
            partyMembers = Collections.singletonList(player);
        }

        String targetLabel = resolveTargetLabel(event);
        Map<String, Object> ctx = EventContextMapper.map(event);

        Object lock = playerLocks.computeIfAbsent(uid, k -> new Object());

        Collection<Player> finalPartyMembers = partyMembers;
        worker.execute(() -> {
            synchronized (lock) {
                processEventInternal(player, key, targetLabel, event, ctx, list, finalPartyMembers);
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

        Collection<Player> partyMembers = PartyHook.membersNear(player, partyRadius);
        if (partyMembers == null || partyMembers.isEmpty()) {
            partyMembers = Collections.singletonList(player);
        }

        Map<String, Object> finalCtx = (ctx == null) ? Collections.emptyMap() : ctx;
        Object lock = playerLocks.computeIfAbsent(uid, k -> new Object());

        Collection<Player> finalPartyMembers = partyMembers;
        worker.execute(() -> {
            synchronized (lock) {
                if ("ENTITY_INTERACT".equalsIgnoreCase(key)) {
                    processNpcInteract(player, finalCtx, list);
                } else {
                    processCustomInternal(player, key, finalCtx, list, finalPartyMembers);
                }
            }
        });
    }

    public void handleNpcInteract(Player player, String targetKey) {
        if (player == null || targetKey == null || targetKey.isEmpty()) return;
        Map<String, Object> ctx = new HashMap<>();
        ctx.put("target_id", targetKey);
        handleCustom(player, "ENTITY_INTERACT", ctx);
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
        if (player == null && event instanceof EntityDeathEvent) {
            player = ((EntityDeathEvent) event).getEntity().getKiller();
        }
        if (player == null) return;

        String key = guessEventKeyFromClass(clazz.getSimpleName());
        QuestDef[] list = quests.byEvent(key);
        if (list == null || list.length == 0) return;

        UUID uid = player.getUniqueId();
        if (isDedup(uid, key)) return;

        Collection<Player> partyMembers = PartyHook.membersNear(player, partyRadius);
        if (partyMembers == null || partyMembers.isEmpty()) {
            partyMembers = Collections.singletonList(player);
        }

        String targetLabel = resolveTargetLabel(event);
        Map<String, Object> ctx = EventContextMapper.map(event);

        Object lock = playerLocks.computeIfAbsent(uid, k -> new Object());
        Player finalPlayer = player;

        Collection<Player> finalPartyMembers = partyMembers;
        worker.execute(() -> {
            synchronized (lock) {
                processEventInternal(finalPlayer, key, targetLabel, event, ctx, list, finalPartyMembers);
            }
        });
    }

    private String resolveTargetLabel(Event event) {
        if (event instanceof BlockBreakEvent) return ((BlockBreakEvent) event).getBlock().getType().name();
        if (event instanceof BlockPlaceEvent) return ((BlockPlaceEvent) event).getBlockPlaced().getType().name();
        if (Bukkit.getPluginManager().isPluginEnabled("MythicMobs")) {
            if (event instanceof MythicMobDeathEvent) {
                return ((MythicMobDeathEvent) event).getMobType().getInternalName();
            }
        }
        if (event instanceof EntityDeathEvent) return ((EntityDeathEvent) event).getEntity().getType().name();
        if (event instanceof PlayerCommandPreprocessEvent) {
            String msgText = ((PlayerCommandPreprocessEvent) event).getMessage();
            if (msgText.startsWith("/")) return msgText.substring(1);
            return msgText;
        }
        if (event instanceof AsyncPlayerChatEvent) return ((AsyncPlayerChatEvent) event).getMessage();
        return "";
    }

    private void processEventInternal(
            Player actor,
            String eventKey,
            String targetLabel,
            Event event,
            Map<String, Object> ctx,
            QuestDef[] list,
            Collection<Player> beneficiaries
    ) {
        List<Runnable> pending = null;
        TargetMatcher matcher = matchers.getOrDefault(eventKey.toLowerCase(Locale.ROOT), matchers.get("*"));

        for (Player beneficiary : beneficiaries) {
            if (beneficiary == null || !beneficiary.isOnline()) continue;

            UUID uid = beneficiary.getUniqueId();
            String name = beneficiary.getName();
            boolean isSelf = beneficiary.equals(actor);

            for (QuestDef def : list) {
                if (def == null) continue;

                if (!isSelf && !def.party) {
                    continue;
                }

                boolean active = progress.isActive(uid, name, def.id);

                // 퀘스트 시작 로직
                if (!active) {
                    if (def.startMode != QuestDef.StartMode.AUTO) continue;
                    if (!isSelf && !def.party) continue;

                    if (!progress.canStart(uid, name, def)) continue;

                    if (!checkRequirements(uid, name, def)) continue;

                    if (!checkTargetMatch(actor, event, matcher, def)) continue;
                    if (!checkConditions(actor, event, ctx, def.condStart)) continue;

                    progress.start(uid, name, def);
                    actions.runAll(def, "accept", beneficiary);
                    actions.runAll(def, "start", beneficiary);
                    beneficiary.sendMessage(format(beneficiary, msg.get(beneficiary, "quest_started").replace("%quest_name%", def.name)));
                    active = true;
                }

                if (!active) continue;

                // 퀘스트 진행 로직
                if (!checkTargetMatch(actor, event, matcher, def)) continue;

                if (checkAnyFail(actor, event, ctx, def.condFail)) {
                    final String qid = def.id;
                    if (pending == null) pending = new ArrayList<>();
                    pending.add(() -> {
                        actions.runAll(def, "fail", beneficiary);
                        progress.cancel(uid, name, qid);
                    });
                    continue;
                }

                if (!checkConditions(actor, event, ctx, def.condSuccess)) continue;

                int value = progress.addProgress(uid, name, def.id, 1);

                if (value >= def.amount) {
                    if (pending == null) pending = new ArrayList<>();
                    pending.add(() -> handleQuestCompleteOnMain(beneficiary, def));
                }
            }
        }

        if (pending != null) {
            scheduleMain(pending);
        }
    }

    private void processCustomInternal(
            Player actor,
            String eventKey,
            Map<String, Object> ctx,
            QuestDef[] list,
            Collection<Player> beneficiaries
    ) {
        List<Runnable> pending = null;

        for (Player beneficiary : beneficiaries) {
            if (beneficiary == null || !beneficiary.isOnline()) continue;

            UUID uid = beneficiary.getUniqueId();
            String name = beneficiary.getName();
            boolean isSelf = beneficiary.equals(actor);

            for (QuestDef def : list) {
                if (def == null) continue;
                if (!progress.isActive(uid, name, def.id)) continue;

                if (!isSelf && !def.party) continue;

                if (checkAnyFail(actor, null, ctx, def.condFail)) {
                    final String qid = def.id;
                    if (pending == null) pending = new ArrayList<>();
                    pending.add(() -> {
                        actions.runAll(def, "fail", beneficiary);
                        progress.cancel(uid, name, qid);
                    });
                    continue;
                }

                if (!checkConditions(actor, null, ctx, def.condSuccess)) continue;

                int value = progress.addProgress(uid, name, def.id, 1);
                if (value >= def.amount) {
                    if (pending == null) pending = new ArrayList<>();
                    pending.add(() -> handleQuestCompleteOnMain(beneficiary, def));
                }
            }
        }

        if (pending != null) scheduleMain(pending);
    }

    private void processNpcInteract(Player player, Map<String, Object> ctx, QuestDef[] list) {
        UUID uid = player.getUniqueId();
        String name = player.getName();
        String targetId = String.valueOf(ctx.get("target_id")).trim();
        if (targetId.isEmpty()) return;

        QuestDef candidate = null;
        for (QuestDef def : list) {
            if (def == null) continue;
            if (def.startMode != QuestDef.StartMode.PUBLIC && def.startMode != QuestDef.StartMode.NPC) continue;
            if (def.hasTarget() && !def.matchesTarget(targetId)) continue;

            if (!progress.canStart(uid, name, def) && !progress.isActive(uid, name, def.id)) continue;
            if (!checkRequirements(uid, name, def)) continue;

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
            if (!checkRequirements(uid, name, candidate)) {
                player.sendMessage(format(player, msg.get(player, "quest_locked")));
                return;
            }

            if (!checkConditions(player, null, ctx, candidate.condStart)) return;

            progress.start(uid, name, candidate);
            actions.runAll(candidate, "accept", player);
            actions.runAll(candidate, "start", player);
            player.sendMessage(format(player, msg.get(player, "quest_started").replace("%quest_name%", candidate.name)));
        }
        npcArm.put(uid, new NpcArmState(candidate.id, now + NPC_ARM_WINDOW_NANOS));
    }

    private void handleQuestCompleteOnMain(Player player, QuestDef def) {
        UUID uid = player.getUniqueId();
        String name = player.getName();
        actions.runAll(def, "success", player);
        progress.complete(uid, name, def);
        player.sendMessage(format(player, msg.get(player, "quest_completed").replace("%quest_name%", def.name)));
        runCompletionFlow(player, def);
    }

    public void startQuest(Player p, String questId) {
        if (p == null || questId == null) return;
        startQuest(p, quests.get(questId));
    }

    public void startQuest(Player player, QuestDef def) {
        if (player == null || def == null) return;
        UUID uid = player.getUniqueId();
        String name = player.getName();

        if (!checkRequirements(uid, name, def)) {
            player.sendMessage(format(player, msg.get(player, "quest_locked")));
            return;
        }

        if (!progress.canStart(uid, name, def)) {
            player.sendMessage(format(player, msg.get(player, "quest_no_repeat").replace("%quest_name%", def.name)));
            return;
        }
        if (progress.isActive(uid, name, def.id)) {
            player.sendMessage(format(player, msg.get(player, "quest_already_active")));
            return;
        }
        progress.start(uid, name, def);
        actions.runAll(def, "accept", player);
        actions.runAll(def, "start", player);
        player.sendMessage(format(player, msg.get(player, "quest_started").replace("%quest_name%", def.name)));
    }

    public void cancelQuest(Player p, String questId) {
        if (p == null || questId == null) return;
        cancelQuest(p, quests.get(questId));
    }

    public void cancelQuest(Player player, QuestDef def) {
        if (player == null || def == null) return;
        if (!progress.isActive(player.getUniqueId(), player.getName(), def.id)) {
            player.sendMessage(format(player, msg.get(player, "quest_not_active")));
            return;
        }
        progress.cancel(player.getUniqueId(), player.getName(), def.id);
        actions.runAll(def, "cancel", player);
        player.sendMessage(format(player, msg.get(player, "quest_canceled").replace("%quest_name%", def.name)));
    }

    public void stopQuest(UUID uuid, String playerName, String questId) {
        if (uuid == null || playerName == null || questId == null) return;
        String id = questId.toLowerCase(Locale.ROOT);
        progress.cancel(uuid, playerName, id);
        Player p = Bukkit.getPlayer(uuid);
        if (p != null && p.isOnline()) {
            QuestDef q = quests.get(id);
            if (q != null) actions.runAll(q, "cancel", p);
            p.sendMessage(format(p, msg.get(p, "quest_stopped").replace("%quest_name%", q != null ? q.name : id)));
        }
    }

    public void stopQuest(Player player, QuestDef def) {
        if (player == null || def == null) return;
        UUID uid = player.getUniqueId();
        String name = player.getName();
        if (!progress.isActive(uid, name, def.id)) return;
        progress.cancel(uid, name, def.id);
        player.sendMessage(format(player, msg.get(player, "quest_stopped").replace("%quest_name%", def.name)));
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
                p.sendMessage(format(p, msg.get(p, "quest_completed").replace("%quest_name%", q.name)));
                runCompletionFlow(p, q);
            }
        } else {
            progress.complete(uuid, playerName, id, 0);
        }
    }

    public void forceComplete(Player player, QuestDef def) {
        if (player == null || def == null) return;
        progress.complete(player.getUniqueId(), player.getName(), def);
        actions.runAll(def, "success", player);
        player.sendMessage(format(player, msg.get(player, "quest_completed").replace("%quest_name%", def.name)));
        runCompletionFlow(player, def);
    }

    public void abandonAll(Player player) {
        if (player != null) {
            progress.cancelAll(player.getUniqueId(), player.getName());
            player.sendMessage(format(player, msg.get(player, "abandon_all_done")));
        }
    }

    public void listActiveTo(Player player) {
        if (player == null) return;
        List<String> active = progress.activeOf(player.getUniqueId(), player.getName());
        if (active == null || active.isEmpty()) {
            player.sendMessage(format(player, msg.get(player, "list_empty")));
            return;
        }
        player.sendMessage(format(player, msg.get(player, "list_header")));
        for (String id : active) {
            QuestDef def = quests.byId(id);
            if (def == null) continue;
            int value = progress.value(player.getUniqueId(), player.getName(), id);
            player.sendMessage(format(player, ChatColor.translateAlternateColorCodes('&', "&f- &a" + def.name + " &7(&e" + value + " / " + Math.max(1, def.amount) + "&7)")));
        }
    }

    public void runCompletionFlow(Player player, QuestDef def) {
        if (def == null) return;
        String nextId = def.nextQuestOnComplete;
        if (nextId != null && !nextId.isEmpty()) {
            QuestDef next = quests.byId(nextId);
            if (next != null) {
                if (checkRequirements(player.getUniqueId(), player.getName(), next)) {
                    player.sendMessage(format(player, msg.get(player, "quest_chain").replace("%current%", def.name).replace("%next%", next.name)));
                    startQuest(player, next);
                } else {
                    player.sendMessage(format(player, msg.get(player, "quest_locked")));
                }
            }
        }
        if (def.repeat < 0) {
            progress.start(player.getUniqueId(), player.getName(), def);
            actions.runAll(def, "restart", player);
            actions.runAll(def, "repeat", player);
        }
    }

    public void shutdown() {
        try {
            worker.shutdownNow();
        } catch (Throwable ignored) {
        }
        conditionCache.clear();
        playerLocks.clear();
        recentEventWindow.clear();
        npcArm.clear();
        customEventIndex.clear();
        tokenCache.clear();
        CHAIN_CACHE.clear();
    }

    public void refreshEventCache() {
        quests.reload();
        quests.rebuildEventMap();
        tokenCache.clear();
        CHAIN_CACHE.clear();
        rebuildCustomEventIndex();
    }

    private String format(Player p, String raw) {
        if (raw == null) return "";
        if (hasPapi && p != null) raw = PlaceholderAPI.setPlaceholders(p, raw);
        Matcher matcher = HEX_PATTERN.matcher(raw);
        StringBuffer buffer = new StringBuffer(raw.length());
        while (matcher.find()) {
            String group = matcher.group(1);
            matcher.appendReplacement(buffer, "§x§" + group.charAt(0) + "§" + group.charAt(1) + "§" + group.charAt(2) + "§" + group.charAt(3) + "§" + group.charAt(4) + "§" + group.charAt(5));
        }
        matcher.appendTail(buffer);
        return ChatColor.translateAlternateColorCodes('&', buffer.toString());
    }

    private boolean checkConditions(Player player, Event event, Map<String, Object> ctx, List<String> list) {
        if (list == null || list.isEmpty()) return true;
        for (String expr : list) if (!cachedEval(player, event, ctx, expr)) return false;
        return true;
    }

    private boolean checkAnyFail(Player player, Event event, Map<String, Object> ctx, List<String> list) {
        if (list == null || list.isEmpty()) return false;
        for (String expr : list) if (cachedEval(player, event, ctx, expr)) return true;
        return false;
    }

    private boolean cachedEval(Player player, Event event, Map<String, Object> ctx, String expr) {
        if (expr == null || expr.isEmpty()) return true;
        String key = player.getUniqueId() + "|" + expr;
        long now = System.nanoTime();
        BoolCacheEntry ent = conditionCache.get(key);
        if (ent != null && ent.expireAt > now) return ent.value;
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

    private boolean checkTokens(String value, QuestDef def) {
        if (!def.hasTarget()) return true;
        if (value == null) value = "";
        value = value.toUpperCase(Locale.ROOT);

        for (String rawTarget : def.targets) {
            if (rawTarget.equals("*")) return true;

            Set<String> tokens = getParsedTokens(rawTarget);
            if (tokens.contains(value)) return true;

            String finalValue = value;
            if (value.startsWith("/") && tokens.stream().anyMatch(t -> finalValue.startsWith(t) || finalValue.startsWith("/" + t))) {
                return true;
            }
            if (tokens.stream().anyMatch(value::contains)) {
                return true;
            }
        }
        return false;
    }

    private boolean checkTokens(String value, String rawTarget) {
        if (rawTarget == null || rawTarget.isEmpty()) return true;
        Set<String> tokens = getParsedTokens(rawTarget);
        if (tokens.contains(value)) return true;
        if (rawTarget.contains("!")) return tokenAnyMatchLegacy(value, rawTarget);
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
            if (!(event instanceof PlayerCommandPreprocessEvent e)) return false;
            String msgText = e.getMessage().toLowerCase(Locale.ROOT);
            String rawTarget = target.toLowerCase(Locale.ROOT);
            String cleanTarget = rawTarget.replaceAll("%[^%]+%", "").trim();
            String cmdBody = msgText.startsWith("/") ? msgText.substring(1) : msgText;
            return cmdBody.contains(cleanTarget);
        });
        matchers.put("player_chat", (player, event, target) -> {
            if (!(event instanceof AsyncPlayerChatEvent)) return false;
            String msgText = ((AsyncPlayerChatEvent) event).getMessage().toLowerCase(Locale.ROOT);
            return msgText.contains(target.toLowerCase(Locale.ROOT));
        });
    }

    private void preloadInternalQuests() {
        try {
            refreshEventCache();
        } catch (Throwable t) {
            plugin.getLogger().warning("Load failed: " + t.getMessage());
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
            Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    for (String qid : copy) progress.reset(p.getUniqueId(), p.getName(), qid);
                }
            }, delayMs / 50L, periodMs / 50L);
        }
    }

    private long millisUntil(String hhmm) {
        String[] parts = hhmm.split(":");
        int h = 0, m = 0;
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
        return (key == null) ? "" : key.trim().toUpperCase(Locale.ROOT);
    }

    private String guessEventKeyFromClass(String simpleName) {
        if (simpleName == null) return "";
        String k = simpleName;
        if (k.endsWith("Event")) k = k.substring(0, k.length() - 5);
        if (k.equalsIgnoreCase("MythicMobDeath")) return "MYTHICMOBS_ENTITY_KILL";
        if (k.equalsIgnoreCase("PlayerInteractEntity")) return "ENTITY_INTERACT";
        if (k.equalsIgnoreCase("EntityDeath")) return "MOBKILLING";
        return k.replace("MythicMob", "MYTHICMOBS_").replace("Player", "PLAYER_").replace("Entity", "ENTITY_").replace("Block", "BLOCK_").toUpperCase(Locale.ROOT);
    }

    private void rebuildCustomEventIndex() {
        customEventIndex.clear();
        Map<String, List<QuestDef>> tmp = new HashMap<>();
        for (QuestDef def : quests.all()) {
            if (def == null || def.custom == null) continue;
            String evt = def.custom.eventClass;
            if (evt == null || evt.isEmpty()) continue;
            tmp.computeIfAbsent(evt.trim(), k -> new ArrayList<>()).add(def);
        }
        for (Map.Entry<String, List<QuestDef>> e : tmp.entrySet()) {
            customEventIndex.put(e.getKey(), e.getValue().toArray(new QuestDef[0]));
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

            Collection<Player> partyMembers = PartyHook.membersNear(p, partyRadius);

            worker.execute(() -> {
                synchronized (lock) {
                    Map<String, Object> ctx = new HashMap<>(fCtx);
                    applyCustomCaptures(event, def.custom, ctx);
                    String eventKey = def.event;
                    QuestDef[] single = new QuestDef[]{def};
                    processEventInternal(finalP, eventKey, "", event, ctx, single, partyMembers);
                }
            });
        }
    }

    private Player resolveCustomPlayer(Event event, CustomEventData data) {
        if (data == null) return EventContextMapper.extractPlayer(event);
        String getter = data.playerGetter;
        if (getter == null || getter.isEmpty()) return EventContextMapper.extractPlayer(event);
        Object o = evalChain(event, getter);
        return (o instanceof Player) ? (Player) o : EventContextMapper.extractPlayer(event);
    }

    private void applyCustomCaptures(Event event, CustomEventData data, Map<String, Object> ctx) {
        if (data == null || data.captures == null || data.captures.isEmpty()) return;
        for (Map.Entry<String, String> entry : data.captures.entrySet()) {
            Object val = evalChain(event, entry.getValue());
            if (val != null) ctx.put(entry.getKey(), val);
        }
    }

    private Object evalChain(Object root, String chain) {
        if (root == null || chain == null || chain.isEmpty()) return null;
        String cacheKey = root.getClass().getName() + "#" + chain;
        MethodHandle[] handles = CHAIN_CACHE.get(cacheKey);
        try {
            if (handles == null) {
                String[] parts = chain.split("\\.");
                List<MethodHandle> list = new ArrayList<>();
                Class<?> current = root.getClass();
                for (String part : parts) {
                    String name = part.trim();
                    if (name.isEmpty()) return null;
                    int idx = name.indexOf('(');
                    if (idx >= 0) name = name.substring(0, idx).trim();
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
            if (m.getName().equals(name) && m.getParameterCount() == 0) return m;
        }
        return null;
    }

    private void scheduleMain(List<Runnable> tasks) {
        if (tasks == null || tasks.isEmpty()) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            for (Runnable r : tasks) {
                try {
                    r.run();
                } catch (Throwable ignored) {
                }
            }
        });
    }

    public QuestRepository quests() { return quests; }
    public ProgressRepository progress() { return progress; }
    public ActionExecutor actions() { return actions; }
    public Msg msg() { return msg; }
    public ExecutorService asyncPool() { return worker; }
}