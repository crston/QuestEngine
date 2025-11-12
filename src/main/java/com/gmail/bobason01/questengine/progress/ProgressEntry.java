package com.gmail.bobason01.questengine.progress;

import java.time.Instant;

/**
 * ProgressEntry
 * 플레이어가 특정 퀘스트를 진행 중일 때의 상태를 나타내는 불변 데이터 구조
 */
public final class ProgressEntry {

    private final String questId;
    private final int progress;
    private final boolean completed;
    private final long lastUpdate;

    public ProgressEntry(String questId, int progress) {
        this(questId, progress, false);
    }

    public ProgressEntry(String questId, int progress, boolean completed) {
        this.questId = questId;
        this.progress = progress;
        this.completed = completed;
        this.lastUpdate = Instant.now().toEpochMilli();
    }

    public String questId() {
        return questId;
    }

    public int progress() {
        return progress;
    }

    public boolean isCompleted() {
        return completed;
    }

    public long lastUpdate() {
        return lastUpdate;
    }

    public ProgressEntry withProgress(int newProgress) {
        return new ProgressEntry(questId, newProgress, completed);
    }

    public ProgressEntry markCompleted() {
        return new ProgressEntry(questId, progress, true);
    }

    @Override
    public String toString() {
        return "ProgressEntry{" +
                "questId='" + questId + '\'' +
                ", progress=" + progress +
                ", completed=" + completed +
                ", lastUpdate=" + lastUpdate +
                '}';
    }
}
