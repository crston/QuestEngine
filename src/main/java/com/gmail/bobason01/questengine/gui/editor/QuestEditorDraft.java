package com.gmail.bobason01.questengine.gui.editor;

import com.gmail.bobason01.questengine.quest.CustomEventData;
import com.gmail.bobason01.questengine.quest.QuestDef;

import java.util.*;

/**
 * QuestEditorDraft (Optimized)
 * - 컬렉션 초기 용량 지정
 * - 불필요한 객체 복사 제거
 */
public final class QuestEditorDraft {

    // Metadata
    public String id = "";
    public String name = "";
    public String event = "CUSTOM";
    public String type = "vanilla";
    public int amount = 1;
    public int repeat = 0;
    public int points = 0;
    public boolean isPublic = false;
    public boolean party = false;
    public QuestDef.StartMode startMode = QuestDef.StartMode.NONE;

    // Lists (초기 용량 4로 최적화, 대부분 적음)
    public final List<String> targets = new ArrayList<>(4);
    public final List<String> condStart = new ArrayList<>(4);
    public final List<String> condSuccess = new ArrayList<>(4);
    public final List<String> condFail = new ArrayList<>(4);

    // Reset
    public String resetPolicy = "";
    public String resetTime = "";

    // Chain
    public String nextQuestOnComplete = "";

    // Display
    public String displayTitle = "&fNo Name Quest";
    public final List<String> displayDescription = new ArrayList<>(4);
    public String displayProgress = "&7%value%/%target%";
    public String displayReward = "";
    public String displayCategory = "";
    public String displayDifficulty = "";
    public String displayIcon = "BOOK";
    public String displayHint = "";
    public int displayCustomModelData = -1;

    // Custom Event
    public String customEventClass = "";
    public String customPlayerGetter = "";
    public final Map<String, String> customCaptures = new LinkedHashMap<>(4);

    // Actions
    public final Map<String, List<String>> actions = new LinkedHashMap<>(8);

    public QuestDef buildQuestDef() {
        // 불변 리스트 복사
        List<String> tCopy = List.copyOf(targets);
        List<String> cStart = List.copyOf(condStart);
        List<String> cSucc = List.copyOf(condSuccess);
        List<String> cFail = List.copyOf(condFail);

        QuestDef.Reset reset = new QuestDef.Reset(resetPolicy, resetTime);

        // Display Map 최적화
        Map<String, Object> dMap = new LinkedHashMap<>(8);
        dMap.put("title", displayTitle);
        if (!displayDescription.isEmpty()) dMap.put("description", List.copyOf(displayDescription));
        dMap.put("progress", displayProgress);
        if (!displayReward.isEmpty()) dMap.put("reward", displayReward);
        if (!displayCategory.isEmpty()) dMap.put("category", displayCategory);
        if (!displayDifficulty.isEmpty()) dMap.put("difficulty", displayDifficulty);
        dMap.put("icon", displayIcon);
        if (!displayHint.isEmpty()) dMap.put("hint", displayHint);
        if (displayCustomModelData != -1) dMap.put("custommodeldata", displayCustomModelData);

        QuestDef.Display display = new QuestDef.Display(dMap);

        // Custom Event
        CustomEventData custom = null;
        if (customEventClass != null && !customEventClass.isEmpty()) {
            custom = new CustomEventData(
                    customEventClass,
                    customPlayerGetter,
                    customCaptures.isEmpty() ? Collections.emptyMap() : Map.copyOf(customCaptures)
            );
        }

        // Actions (Deep Copy)
        Map<String, List<String>> actionsCopy;
        if (actions.isEmpty()) {
            actionsCopy = Collections.emptyMap();
        } else {
            actionsCopy = new LinkedHashMap<>(actions.size());
            actions.forEach((k, v) -> {
                if (k != null && !k.isEmpty() && v != null && !v.isEmpty()) {
                    actionsCopy.put(k, List.copyOf(v));
                }
            });
        }

        return new QuestDef(
                id, name, event, tCopy, amount, repeat, points, isPublic, party, type,
                reset, display, custom, cStart, cSucc, cFail, actionsCopy,
                nextQuestOnComplete, startMode
        );
    }

    public static QuestEditorDraft fromQuest(QuestDef q) {
        QuestEditorDraft d = new QuestEditorDraft();

        d.id = q.id;
        d.name = q.name;
        d.event = q.event;
        d.type = q.type;
        d.amount = q.amount;
        d.repeat = q.repeat;
        d.points = q.points;
        d.isPublic = q.isPublic;
        d.party = q.party;
        d.startMode = q.startMode;

        if (q.targets != null) d.targets.addAll(q.targets);

        if (q.reset != null) {
            d.resetPolicy = q.reset.policy;
            d.resetTime = q.reset.time;
        }

        d.nextQuestOnComplete = q.nextQuestOnComplete;

        if (q.display != null) {
            d.displayTitle = q.display.title;
            if (q.display.description != null) d.displayDescription.addAll(q.display.description);
            d.displayProgress = q.display.progress;
            d.displayReward = q.display.reward;
            d.displayCategory = q.display.category;
            d.displayDifficulty = q.display.difficulty;
            d.displayIcon = q.display.icon;
            d.displayHint = q.display.hint;
            d.displayCustomModelData = q.display.customModelData;
        }

        if (q.condStart != null) d.condStart.addAll(q.condStart);
        if (q.condSuccess != null) d.condSuccess.addAll(q.condSuccess);
        if (q.condFail != null) d.condFail.addAll(q.condFail);

        if (q.custom != null) {
            d.customEventClass = q.custom.eventClass;
            d.customPlayerGetter = q.custom.playerGetter;
            if (q.custom.captures != null) d.customCaptures.putAll(q.custom.captures);
        }

        if (q.actions != null) {
            q.actions.forEach((k, v) -> {
                if (v != null) d.actions.put(k, new ArrayList<>(v));
            });
        }

        return d;
    }
}