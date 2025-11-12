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
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;

/**
 * Engine
 * 핵심 엔진
 * 조건 캐시 이벤트 디듀프 타겟 매처 체인 처리 보드 반복 처리
 */
public final class Engine {

    private final Map<UUID, String> armedQuest = new ConcurrentHashMap<>();
    private final Map<UUID, Long> armedUntil = new ConcurrentHashMap<>();
    private static final long INTERACT_ARM_WINDOW_NANOS = 2_000_000_000L; // 2초
    private final QuestEnginePlugin plugin;
    private final QuestRepository quests;
    private final ProgressRepository progress;
    private final ActionExecutor actions;
    private final Msg msg;
    private final ExecutorService worker;

    private final Map<UUID, Set<String>> interactArmed = new ConcurrentHashMap<>();
    private final Map<String, BoolCacheEntry> conditionCache = new ConcurrentHashMap<>(1024);
    private final Map<UUID, Object> playerLocks = new ConcurrentHashMap<>(256);
    private final Map<UUID, Map<String, Long>> recentEventWindow = new ConcurrentHashMap<>(256);
    private final Map<String, TargetMatcher> matchers = new ConcurrentHashMap<>(64);

    private final long conditionTtlNanos;
    private final long dedupWindowNanos;

    private static final class BoolCacheEntry {
        final boolean value;
        final long expireAt;
        BoolCacheEntry(boolean v, long e) { value = v; expireAt = e; }
    }

    @FunctionalInterface
    interface TargetMatcher {
        boolean test(Player p, Event e, String target);
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

        long ttlMs = Math.max(50, plugin.getConfig().getLong("performance.condition-cache-ttl-ms", 300));
        this.conditionTtlNanos = ttlMs * 1_000_000L;

        long dedupMs = Math.max(3, plugin.getConfig().getLong("performance.event-dedup-window-ms", 10));
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

    public void refreshEventCache() {
        quests.reload();
        quests.rebuildEventMap();
    }

    public void shutdown() {
        try { worker.shutdownNow(); } catch (Throwable ignored) {}
        conditionCache.clear();
        playerLocks.clear();
        recentEventWindow.clear();
        matchers.clear();
    }

    public void startQuest(Player p, String questId) {
        if (p == null || questId == null) return;
        QuestDef q = quests.get(questId);
        if (q == null) {
            p.sendMessage(msg.pref("invalid_args"));
            return;
        }
        startQuest(p, q);
    }

    // Engine.java 내부 startQuest(Player p, QuestDef q)
    public void startQuest(Player p, QuestDef q) {
        if (p == null || q == null) return;
        UUID uid = p.getUniqueId();
        String name = p.getName();

        // 이미 완료한 퀘스트면 재수행 불가
        if (progress.isCompleted(uid, name, q.id)) {
            p.sendMessage(msg.pref("quest_no_repeat").replace("%quest_name%", q.name));
            return;
        }

        // 이미 진행 중인 퀘스트면 중복 방지
        if (progress.isActive(uid, name, q.id)) {
            p.sendMessage(msg.pref("quest_already_active"));
            return;
        }

        // 보드 전용인데 직접 명령어로 시도하면 거부
        if (isBoardQuest(q) && !allowBoardStartContext(p)) {
            p.sendMessage(msg.pref("quest_board_only"));
            return;
        }

        // PUBLIC 퀘스트일 경우 accept 메시지와 started 메시지 생략
        if (!isBoardQuest(q)) {
            actions.runAll(q, "accept", p);
            p.sendMessage(msg.pref("quest_started").replace("%quest_name%", q.name));
        }

        progress.start(uid, name, q.id);
        actions.runAll(q, "start", p);
    }

    public void cancelQuest(Player p, String questId) {
        if (p == null || questId == null) return;
        QuestDef q = quests.get(questId);
        cancelQuest(p, q);
    }

    public void cancelQuest(Player p, QuestDef q) {
        if (p == null || q == null) return;
        UUID uid = p.getUniqueId();
        String name = p.getName();
        if (!progress.isActive(uid, name, q.id)) {
            p.sendMessage(msg.pref("quest_not_active"));
            return;
        }
        progress.cancel(uid, name, q.id);
        actions.runAll(q, "cancel", p);
        p.sendMessage(msg.pref("quest_canceled").replace("%quest_name%", q.name));
    }

    public void stopQuest(UUID uuid, String playerName, String questId) {
        if (uuid == null || playerName == null || questId == null) return;
        progress.cancel(uuid, playerName, questId);
        Player p = Bukkit.getPlayer(uuid);
        if (p != null && p.isOnline()) {
            QuestDef q = quests.get(questId);
            if (q != null) actions.runAll(q, "cancel", p);
            p.sendMessage(msg.pref("quest_stopped").replace("%quest_name%", q != null ? q.name : questId));
        }
    }

    public void stopQuest(Player p, QuestDef q) {
        if (p == null || q == null) return;
        if (!progress.isActive(p.getUniqueId(), p.getName(), q.id)) return;
        progress.cancel(p.getUniqueId(), p.getName(), q.id);
        p.sendMessage(msg.pref("quest_stopped").replace("%quest_name%", q.name));
    }

    public void forceComplete(UUID uuid, String playerName, String questId) {
        if (uuid == null || playerName == null || questId == null) return;
        Player p = Bukkit.getPlayer(uuid);
        QuestDef q = quests.get(questId);
        int pts = q != null ? q.points : 0;
        progress.complete(uuid, playerName, questId, pts);
        if (p != null && q != null) {
            actions.runAll(q, "success", p);
            p.sendMessage(msg.pref("quest_completed").replace("%quest_name%", q.name));
            runCompletionFlow(p, q);
        }
    }

    public void forceComplete(Player p, QuestDef q) {
        if (p == null || q == null) return;
        progress.complete(p.getUniqueId(), p.getName(), q.id, q.points);
        actions.runAll(q, "success", p);
        p.sendMessage(msg.pref("quest_completed").replace("%quest_name%", q.name));
        runCompletionFlow(p, q);
    }

    public void abandonAll(Player p) {
        if (p == null) return;
        progress.cancelAll(p.getUniqueId(), p.getName());
        p.sendMessage(msg.pref("abandon_all_done"));
    }

    public void listActiveTo(Player p) {
        if (p == null) return;
        List<String> active = progress.activeOf(p.getUniqueId(), p.getName());
        if (active == null || active.isEmpty()) {
            p.sendMessage(msg.pref("list_empty"));
            return;
        }
        p.sendMessage(msg.pref("list_header"));

        StringBuilder sb = new StringBuilder(64);
        for (String id : active) {
            QuestDef q = quests.get(id);
            if (q == null) continue;

            int val = progress.value(p.getUniqueId(), p.getName(), id);
            int target = Math.max(1, q.amount);
            double pct = Math.min(1.0, Math.max(0.0, val / (double) target));
            int filled = (int) (pct * 20);

            sb.setLength(0);
            sb.append("§f- §a").append(q.name).append(" §7(§e").append(val).append(" / ").append(target).append("§7) ");
            sb.append("§a");
            for (int i = 0; i < filled; i++) sb.append('■');
            sb.append("§7");
            for (int i = filled; i < 20; i++) sb.append('■');

            p.sendMessage(sb.toString());
        }
    }

    /**
     * NPC/엔티티 우클릭 전용 두-번-클릭 플로우
     * targetKey 예) "CITIZENS_1" 또는 "VILLAGER"
     */
    public void handleNpcInteract(Player p, String targetKey) {
        if (p == null || targetKey == null || targetKey.isEmpty()) return;

        // 이 퀘스트들만 조사
        QuestDef[] list = quests.byEvent("ENTITY_INTERACT");
        if (list.length == 0) return;

        UUID uid = p.getUniqueId();
        String name = p.getName();

        // 같은 타깃으로 시작 가능한 퀘스트 찾기 (start_mode: PUBLIC|NPC)
        QuestDef candidate = null;
        for (QuestDef q : list) {
            if (q == null) continue;
            if (!(q.startMode == QuestDef.StartMode.PUBLIC || q.startMode == QuestDef.StartMode.NPC)) continue;

            // 타깃 매칭: targets 비었으면 프리매치, 있으면 키 일치 필요
            boolean matched = !q.hasTarget();
            if (!matched) {
                for (String t : q.targets) {
                    if (t.equalsIgnoreCase(targetKey)) { matched = true; break; }
                }
            }
            if (!matched) continue;

            candidate = q;
            break;
        }
        if (candidate == null) return;

        // 두-번-클릭 윈도우 검사
        long now = System.nanoTime();
        String armed = armedQuest.get(uid);
        Long until = armedUntil.get(uid);

        // ① 이미 무장 상태이고 같은 퀘스트 & 아직 유효 → 성공 처리
        if (armed != null && armed.equalsIgnoreCase(candidate.id) && until != null && until > now) {
            // 활성 중일 때만 완료
            if (progress.isActive(uid, name, candidate.id)) {
                handleQuestCompleteOnMain(p, candidate);
            } else {
                // 혹시 중간에 취소/리셋됐다면 안전하게 시작 후 완료
                progress.start(uid, name, candidate.id);
                actions.runAll(candidate, "start", p);
                handleQuestCompleteOnMain(p, candidate);
            }
            // 상태 해제
            armedQuest.remove(uid);
            armedUntil.remove(uid);
            return;
        }

        // ② 아직 무장되지 않았거나 다른 퀘스트였다면: 시작만 수행하고 무장
        if (!progress.isActive(uid, name, candidate.id)) {
            progress.start(uid, name, candidate.id);
            actions.runAll(candidate, "start", p);
            p.sendMessage(msg.pref("quest_started").replace("%quest_name%", candidate.name));
        } else {
            // 이미 활성화되어 있는데 첫 클릭이면(= 무장 아님) 바로 무장만
            // (혹시 다른 인터랙트로 활성화한 경우에도 동일하게 동작)
        }

        armedQuest.put(uid, candidate.id);
        armedUntil.put(uid, now + INTERACT_ARM_WINDOW_NANOS);
    }

    public void handle(Player p, String eventName, Event e) {
        if (p == null || eventName == null) return;
        QuestDef[] list = quests.byEvent(eventName);
        if (list.length == 0) return;

        UUID uid = p.getUniqueId();
        if (isDedup(uid, eventName)) return;

        Object lock = playerLocks.computeIfAbsent(uid, k -> new Object());
        worker.execute(() -> {
            synchronized (lock) {
                processEventInternal(p, eventName, e, list);
            }
        });
    }

    public void handleCustom(Player player, String eventKey, Map<String, Object> ctx) {
        if (!eventKey.equalsIgnoreCase("ENTITY_INTERACT")) return;

        String target = String.valueOf(ctx.get("target_id"));
        if (target == null || target.isEmpty()) return;

        for (QuestDef def : quests.byEvent(eventKey)) {
            // start_mode 검사 (PUBLIC 또는 NPC 허용)
            if (def.startMode != QuestDef.StartMode.PUBLIC && def.startMode != QuestDef.StartMode.NPC)
                continue;

            // 타깃 일치 확인
            if (!def.matchesTarget(target)) continue;

            PlayerData data = progress.get(player.getUniqueId());

            // 아직 시작 안했으면 시작
            if (!data.isActive(def.id) && !data.isCompleted(def.id)) {
                startQuest(player, def.id);
                return;
            }

            // 이미 진행 중이면 완료 처리
            if (data.isActive(def.id) && !data.isCompleted(def.id)) {
                completeQuest(player, def.id);
                return;
            }

            // 이미 완료된 경우는 무시
        }
    }


    public void handleDynamic(Player p, String key, Object val) {
        if (p == null || key == null) return;
        Map<String, Object> ctx = new HashMap<>();
        if (val != null) ctx.put("value", val);
        handleCustom(p, key, ctx);
    }

    public void handleDynamic(Event e) {
        if (e == null) return;

        // 플레이어 탐지
        Player p = EventContextMapper.extractPlayer(e);
        if (p == null) {
            if (e instanceof EntityDeathEvent de && de.getEntity().getKiller() != null)
                p = de.getEntity().getKiller();
            else if (e instanceof EntityDamageByEntityEvent hit && hit.getDamager() instanceof Player dp)
                p = dp;
        }
        if (p == null) return;

        // 이벤트 키 정규화 (ex: BlockBreakEvent → BLOCK_BREAK)
        String eventKey = e.getClass().getSimpleName()
                .replace("Event", "")
                .replace("MythicMob", "MYTHICMOBS_")
                .replace("Player", "PLAYER_")
                .replace("Entity", "ENTITY_")
                .replace("Block", "BLOCK_")
                .replace("Inventory", "INVENTORY_")
                .replace("Item", "ITEM_")
                .toUpperCase(Locale.ROOT);

        switch (eventKey) {
            case "PLAYER_INTERACT_ENTITY" -> eventKey = "ENTITY_INTERACT";   // Citizens, MythicMobs 등 NPC 클릭
            case "PLAYER_INTERACT" -> eventKey = "BLOCK_INTERACT";            // 블록 클릭류 통합
            case "PLAYER_DROP_ITEM" -> eventKey = "ITEM_DROP";                // 아이템 드롭
            case "PLAYER_PICKUP_ITEM" -> eventKey = "ITEM_PICKUP";            // 아이템 줍기
            case "ENTITY_DEATH" -> eventKey = "MOBKILLING";                   // 퀘스트 호환용
        }

        // 퀘스트 매칭
        QuestDef[] list = quests.byEvent(eventKey);
        if (list.length == 0) return;

        UUID uid = p.getUniqueId();
        if (isDedup(uid, eventKey)) return;

        // 컨텍스트 빌드
        Map<String, Object> ctx = EventContextMapper.map(e);
        Object lock = playerLocks.computeIfAbsent(uid, k -> new Object());

        // 비동기 처리
        Player finalP = p;
        worker.execute(() -> {
            synchronized (lock) {
                List<Runnable> pending = new ArrayList<>(4);

                for (QuestDef q : list) {
                    if (q == null) continue;
                    if (!progress.isActive(uid, finalP.getName(), q.id)) continue;

                    boolean fail = false;
                    for (String cond : q.condFail) {
                        if (cachedEval(finalP, e, ctx, cond)) { fail = true; break; }
                    }
                    if (fail) {
                        final String qid = q.id;
                        pending.add(() -> {
                            actions.runAll(q, "fail", finalP);
                            progress.cancel(uid, finalP.getName(), qid);
                        });
                        continue;
                    }

                    boolean ok = true;
                    for (String cond : q.condSuccess) {
                        if (!cachedEval(finalP, e, ctx, cond)) { ok = false; break; }
                    }
                    if (!ok) continue;

                    int val = progress.addProgress(uid, finalP.getName(), q.id, 1);
                    if (val >= q.amount) {
                        pending.add(() -> handleQuestCompleteOnMain(finalP, q));
                    }
                }

                if (!pending.isEmpty()) {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        for (Runnable r : pending) {
                            try { r.run(); } catch (Throwable ignored) {}
                        }
                    });
                }
            }
        });
    }

    private boolean markArmed(UUID uid, String qid) {
        return interactArmed.computeIfAbsent(uid, k -> ConcurrentHashMap.newKeySet()).add(qid);
    }

    private boolean consumeArmed(UUID uid, String qid) {
        Set<String> set = interactArmed.get(uid);
        if (set == null) return false;
        boolean had = set.remove(qid);
        if (set.isEmpty()) interactArmed.remove(uid);
        return had;
    }

    private void processEventInternal(Player p, String eventName, Event e, QuestDef[] list) {
        UUID uid = p.getUniqueId();
        String name = p.getName();

        boolean isInteract = "ENTITY_INTERACT".equalsIgnoreCase(eventName);

        TargetMatcher matcher = matchers.get(eventName.toLowerCase(Locale.ROOT));
        if (matcher == null) matcher = matchers.getOrDefault("*", (pp, ee, t) -> true);

        List<Runnable> pending = new ArrayList<>(4);

        for (QuestDef q : list) {
            if (q == null) continue;

            boolean active = progress.isActive(uid, name, q.id);

            // [1] ENTITY_INTERACT + 아직 시작 안함 → 첫 클릭 (start)
            if (isInteract && !active && (q.startMode == QuestDef.StartMode.PUBLIC || q.startMode == QuestDef.StartMode.NPC)) {
                boolean matched = !q.hasTarget();
                if (!matched) {
                    for (String t : q.targets) {
                        if (matcher.test(p, e, t)) { matched = true; break; }
                    }
                }
                if (!matched) continue;

                // 첫 클릭: 퀘스트 시작 + 무장 상태 설정
                pending.add(() -> {
                    actions.runAll(q, "start", p);
                    progress.start(uid, name, q.id);
                    markArmed(uid, q.id);
                });
                continue;
            }

            // [2] 일반 진행 중 퀘스트
            if (!active) continue;

            boolean matched = !q.hasTarget();
            if (!matched) {
                for (String t : q.targets) {
                    if (matcher.test(p, e, t)) { matched = true; break; }
                }
            }
            if (!matched) continue;

            // 실패 조건
            boolean fail = false;
            for (String cond : q.condFail) {
                if (cachedEval(p, e, null, cond)) { fail = true; break; }
            }
            if (fail) {
                final String qid = q.id;
                pending.add(() -> {
                    actions.runAll(q, "fail", p);
                    progress.cancel(uid, name, qid);
                });
                continue;
            }

            // 성공 조건
            boolean ok = true;
            for (String cond : q.condSuccess) {
                if (!cachedEval(p, e, null, cond)) { ok = false; break; }
            }
            if (!ok) continue;

            // [3] 두 번째 클릭 시 (armed 상태면 즉시 완료)
            if (isInteract) {
                if (consumeArmed(uid, q.id)) {
                    pending.add(() -> handleQuestCompleteOnMain(p, q));
                }
                continue;
            }

            // [4] 누적형 이벤트 (ex: BLOCK_BREAK)
            int val = progress.addProgress(uid, name, q.id, 1);
            if (val >= q.amount) {
                pending.add(() -> handleQuestCompleteOnMain(p, q));
            }
        }

        if (!pending.isEmpty()) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                for (Runnable r : pending) {
                    try { r.run(); } catch (Throwable ignored) {}
                }
            });
        }
    }

    private void processCustomInternal(Player p, QuestDef[] list, Map<String, Object> ctx) {
        UUID uid = p.getUniqueId();
        String name = p.getName();
        List<Runnable> pending = new ArrayList<>(4);

        for (QuestDef q : list) {
            if (q == null) continue;
            if (!progress.isActive(uid, name, q.id)) continue;

            boolean fail = false;
            for (String cond : q.condFail) {
                if (cachedEval(p, null, ctx, cond)) { fail = true; break; }
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
            for (String cond : q.condSuccess) {
                if (!cachedEval(p, null, ctx, cond)) { ok = false; break; }
            }
            if (!ok) continue;

            pending.add(() -> handleQuestCompleteOnMain(p, q));
        }

        if (!pending.isEmpty()) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                for (Runnable r : pending) {
                    try { r.run(); } catch (Throwable ignored) {}
                }
            });
        }
    }

    private void handleQuestCompleteOnMain(Player p, QuestDef q) {
        UUID uid = p.getUniqueId();
        String name = p.getName();

        actions.runAll(q, "success", p);
        progress.complete(uid, name, q.id, q.points);
        p.sendMessage(msg.pref("quest_completed").replace("%quest_name%", q.name));

        runCompletionFlow(p, q);
    }

    private void runCompletionFlow(Player p, QuestDef q) {
        String nextId = resolveNextId(q);
        if (nextId != null && !nextId.isBlank()) {
            QuestDef next = quests.get(nextId);
            if (next != null) {
                if (isBoardQuest(next)) {
                    p.sendMessage(
                            msg.pref("quest_chain_board")
                                    .replace("%current%", q.name)
                                    .replace("%next%", next.name)
                    );
                } else {
                    p.sendMessage(
                            msg.pref("quest_chain")
                                    .replace("%current%", q.name)
                                    .replace("%next%", next.name)
                    );
                    startQuest(p, next);
                }
            } else {
                p.sendMessage(msg.pref("quest_chain_end"));
            }
        }

        if (q.repeat < 0) {
            if (isBoardQuest(q)) {
                p.sendMessage(
                        msg.pref("quest_board_repeat").replace("%quest_name%", q.name)
                );
            } else {
                Supplier<Boolean> started = () -> {
                    if (progress.isActive(p.getUniqueId(), p.getName(), q.id)) return false;
                    progress.start(p.getUniqueId(), p.getName(), q.id);
                    actions.runAll(q, "restart", p);
                    actions.runAll(q, "repeat", p);
                    return true;
                };
                started.get();
            }
        }
    }

    private String resolveNextId(QuestDef q) {
        if (q == null || q.actions == null) return null;
        List<String> list = q.actions.get("next");
        if (list == null || list.isEmpty()) return null;
        String raw = list.get(0);
        if (raw == null) return null;
        String s = raw.trim();
        if (s.isEmpty()) return null;
        int sp = s.indexOf(' ');
        return sp > 0 ? s.substring(0, sp) : s;
    }

    private boolean isBoardQuest(QuestDef q) {
        return q != null && q.isPublic;
    }

    private boolean allowBoardStartContext(Player p) {
        return true;
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

    private boolean isDedup(UUID uid, String key) {
        long now = System.nanoTime();
        Map<String, Long> m = recentEventWindow.computeIfAbsent(uid, k -> new ConcurrentHashMap<>(8));
        Long last = m.get(key);
        if (last != null && now - last < dedupWindowNanos) return true;
        m.put(key, now);
        return false;
    }

    private void installDefaultMatchers() {
        matchers.put("*", (p, e, t) -> true);

        matchers.put("block_break", (p, e, t) -> {
            if (!(e instanceof BlockBreakEvent be)) return false;
            if (t == null || t.isEmpty()) return true;
            return tokenAnyMatch(be.getBlock().getType().name(), t);
        });

        matchers.put("block_place", (p, e, t) -> {
            if (!(e instanceof BlockPlaceEvent bp)) return false;
            if (t == null || t.isEmpty()) return true;
            return tokenAnyMatch(bp.getBlockPlaced().getType().name(), t);
        });

        matchers.put("entity_kill", (p, e, t) -> {
            if (!(e instanceof EntityDeathEvent de)) return false;
            if (t == null || t.isEmpty()) return true;
            return tokenAnyMatch(de.getEntity().getType().name(), t);
        });

        matchers.put("entity_interact", TargetMatchers.ENTITY_INTERACT_MATCHER);

        matchers.put("player_command", (p, e, t) -> {
            if (!(e instanceof PlayerCommandPreprocessEvent ce)) return false;
            if (t == null || t.isEmpty()) return true;
            return ce.getMessage().toLowerCase(Locale.ROOT).startsWith("/" + t.toLowerCase(Locale.ROOT));
        });

        matchers.put("player_chat", (p, e, t) -> {
            if (!(e instanceof AsyncPlayerChatEvent ce)) return false;
            if (t == null || t.isEmpty()) return true;
            return ce.getMessage().toLowerCase(Locale.ROOT).contains(t.toLowerCase(Locale.ROOT));
        });
    }

    private static boolean tokenAnyMatch(String val, String target) {
        String v = val.toUpperCase(Locale.ROOT);
        for (String tok : target.split("\\|")) {
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
            if (plugin.getResource("quests") != null) {
                plugin.getLogger().info("[QuestEngine] Loading internal quests from JAR");
                quests.reload();
            }
        } catch (Throwable t) {
            plugin.getLogger().warning("[QuestEngine] Internal quest load failed: " + t.getMessage());
        }
    }

    private void scheduleDailyResets() {
        Map<String, List<String>> timeToQuestIds = new HashMap<>();
        String defaultTime = plugin.getConfig().getString("reset.default-time", "04:00");

        for (String id : quests.ids()) {
            QuestDef q = quests.get(id);
            if (q == null || q.reset == null) continue;
            if (!"DAILY".equalsIgnoreCase(q.reset.policy)) continue;
            String at = (q.reset.time == null || q.reset.time.isBlank()) ? defaultTime : q.reset.time;
            timeToQuestIds.computeIfAbsent(at, k -> new ArrayList<>()).add(id);
        }

        for (Map.Entry<String, List<String>> e : timeToQuestIds.entrySet()) {
            String time = e.getKey();
            long delayMs = millisUntil(time);
            long periodMs = 24L * 60L * 60L * 1000L;
            List<String> copy = List.copyOf(e.getValue());

            Bukkit.getScheduler().runTaskTimerAsynchronously(
                    plugin,
                    () -> {
                        for (Player p : Bukkit.getOnlinePlayers()) {
                            UUID uid = p.getUniqueId();
                            String name = p.getName();
                            for (String qid : copy) progress.reset(uid, name, qid);
                        }
                        plugin.getLogger().info("[QuestEngine] Daily reset done at " + time);
                    },
                    delayMs / 50,
                    periodMs / 50
            );
        }
    }

    private long millisUntil(String hhmm) {
        String[] parts = hhmm.split(":");
        int h = 0, m = 0;
        try {
            h = Integer.parseInt(parts[0]);
            if (parts.length > 1) m = Integer.parseInt(parts[1]);
        } catch (Throwable ignored) {}
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime next = now.withHour(h).withMinute(m).withSecond(0).withNano(0);
        if (!next.isAfter(now)) next = next.plusDays(1);
        return Duration.between(now, next).toMillis();
    }
    public void completeQuest(Player player, String questId) {
        QuestDef def = quests.byId(questId);
        if (def == null) return;

        PlayerData data = progress.get(player.getUniqueId());
        if (data == null || !data.isActive(questId)) return;

        data.complete(questId, def.points);
        progress.save(data);

        actions.run(def, "success", player); // ← runAll 기반 헬퍼 호출

        if (def.nextQuestOnComplete != null && !def.nextQuestOnComplete.isEmpty()) {
            QuestDef next = quests.byId(def.nextQuestOnComplete);
            if (next != null) startQuest(player, next.id);
        }

        player.sendMessage(msg.pref("quest_completed")
                .replace("%quest_name%", def.name));
    }
}
