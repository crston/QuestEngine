package com.gmail.bobason01.questengine.progress;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Run main after Maven test-compile; does not require a Minecraft server. */
public final class PlayerDataRegressionChecks {
    private static PlayerData player() { return new PlayerData(UUID.randomUUID(), "Tester"); }
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
    private static PlayerData roundTrip(PlayerData source) {
        PlayerData restored = player();
        source.snapshot().forEach((qid, s) -> restored.restoreQuest(qid, s.active(), s.completed(),
                s.value(), s.points(), s.repeatCount()));
        return restored;
    }
    public static void main(String[] args) throws Exception {
        PlayerData repeat = player();
        repeat.start("repeat"); repeat.complete("repeat", 10, 3);
        PlayerData restored = roundTrip(repeat);
        check(restored.getRepeatCount("repeat") == 1, "Inactive repeat history lost");
        check(restored.hasSatisfiedRequirement("repeat"), "Prerequisite history lost");
        check(restored.canStart("repeat", 3), "Repeat cannot resume");
        check(restored.pointsOf("repeat") == 10, "Partial-repeat points lost");
        System.out.println("PASS: inactive repeat snapshot and restore");

        PlayerData once = player(); once.start("once"); once.complete("once", 25, 0);
        for (int i = 0; i < 5; i++) once = roundTrip(once);
        check(once.isCompleted("once") && !once.canStart("once", 0), "Completed quest reopened");
        check(once.totalPoints() == 25, "Completed points lost");
        check(once.getRepeatCount("once") == 1, "Reload incremented completion count");
        System.out.println("PASS: five reloads retain completed state, count and points");

        PlayerData active = player(); active.start("b"); active.start("a"); active.add("b", 7);
        PlayerData copy = roundTrip(active);
        check(copy.activeIds().equals(Arrays.asList("b", "a")), "Active order changed");
        check(copy.valueOf("b") == 7, "Active progress lost");
        var frozen = active.snapshot(); active.add("b", 1);
        check(frozen.get("b").value() == 7, "Snapshot mutated");
        System.out.println("PASS: active progress/order and immutable snapshot");

        PlayerData infinite = player(); infinite.start("q");
        long revision = infinite.advanceForCompletion("q", 1, 1);
        check(infinite.completeIfActive("q", 10, -1, revision), "First completion rejected");
        infinite.start("q");
        check(!infinite.completeIfActive("q", 10, -1, revision), "Stale completion affected next cycle");
        check(infinite.getRepeatCount("q") == 1 && infinite.isActive("q"), "Infinite repeat corrupted");
        System.out.println("PASS: duplicate completion rejected after infinite restart");

        PlayerData cancelled = player(); cancelled.start("q");
        long stale = cancelled.completionRevision("q"); cancelled.cancel("q"); cancelled.start("q");
        check(!cancelled.completeIfActive("q", 10, 0, stale), "Cancelled cycle awarded reward");
        long resetStale = cancelled.completionRevision("q"); cancelled.resetQuest("q"); cancelled.start("q");
        check(!cancelled.completeIfActive("q", 10, 0, resetStale), "Reset cycle awarded reward");
        PlayerData replacement = player(); replacement.start("q");
        check(!replacement.completeIfActive("q", 10, 0, stale), "Full reset reused an old cycle token");
        System.out.println("PASS: cancel, quest reset and full reset invalidate queued completion");

        PlayerData concurrent = player(); concurrent.start("q");
        long token = concurrent.advanceForCompletion("q", 1, 1);
        AtomicInteger wins = new AtomicInteger();
        ExecutorService workers = Executors.newFixedThreadPool(8);
        List<Future<?>> tasks = new ArrayList<>();
        try {
            for (int i = 0; i < 32; i++) tasks.add(workers.submit(() -> {
                if (concurrent.completeIfActive("q", 10, 0, token)) wins.incrementAndGet();
            }));
            for (Future<?> task : tasks) task.get();
        } finally { workers.shutdownNow(); }
        check(wins.get() == 1 && concurrent.getRepeatCount("q") == 1, "Concurrent duplicate completion");
        System.out.println("PASS: 32 concurrent completion requests award exactly once");
    }
}
