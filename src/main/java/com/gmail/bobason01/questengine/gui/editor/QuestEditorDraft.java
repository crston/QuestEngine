package com.gmail.bobason01.questengine.gui.editor;

import com.gmail.bobason01.questengine.quest.CustomEventData;
import com.gmail.bobason01.questengine.quest.QuestDef;

import java.util.*;

/**
 * QuestEditorDraft
 * QuestDef 를 GUI 에서 편집하기 위한 중간 모델
 */
public final class QuestEditorDraft {

    // 기본 메타
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

    // 타겟
    public final List<String> targets = new ArrayList<>();

    // 리셋
    public String resetPolicy = "";
    public String resetTime = "";

    // 체인
    public String nextQuestOnComplete = "";

    // 디스플레이
    public String displayTitle = "&fNo Name Quest";
    public final List<String> displayDescription = new ArrayList<>();
    public String displayProgress = "&7%value%/%target%";
    public String displayReward = "";
    public String displayCategory = "";
    public String displayDifficulty = "";
    public String displayIcon = "BOOK";
    public String displayHint = "";
    public int displayCustomModelData = -1;

    // 조건
    public final List<String> condStart = new ArrayList<>();
    public final List<String> condSuccess = new ArrayList<>();
    public final List<String> condFail = new ArrayList<>();

    // 커스텀 이벤트
    public String customEventClass = "";
    public String customPlayerGetter = "";
    public final Map<String, String> customCaptures = new LinkedHashMap<>();

    // 액션
    public final Map<String, List<String>> actions = new LinkedHashMap<>();

    // Draft -> QuestDef 변환
    public QuestDef buildQuestDef() {

        List<String> targetsCopy = List.copyOf(targets);
        List<String> startCopy = List.copyOf(condStart);
        List<String> successCopy = List.copyOf(condSuccess);
        List<String> failCopy = List.copyOf(condFail);

        QuestDef.Reset reset = new QuestDef.Reset(resetPolicy, resetTime);

        Map<String, Object> displayMap = new LinkedHashMap<>();
        displayMap.put("title", displayTitle);
        if (!displayDescription.isEmpty()) displayMap.put("description", new ArrayList<>(displayDescription));
        displayMap.put("progress", displayProgress);
        if (!displayReward.isEmpty()) displayMap.put("reward", displayReward);
        if (!displayCategory.isEmpty()) displayMap.put("category", displayCategory);
        if (!displayDifficulty.isEmpty()) displayMap.put("difficulty", displayDifficulty);
        displayMap.put("icon", displayIcon);
        if (!displayHint.isEmpty()) displayMap.put("hint", displayHint);
        if (displayCustomModelData != -1) displayMap.put("custommodeldata", displayCustomModelData);

        QuestDef.Display display = new QuestDef.Display(displayMap);

        CustomEventData custom = null;
        if (!customEventClass.isEmpty()) {
            Map<String, String> caps = customCaptures.isEmpty()
                    ? Collections.emptyMap()
                    : new LinkedHashMap<>(customCaptures);
            custom = new CustomEventData(customEventClass, customPlayerGetter, caps);
        }

        Map<String, List<String>> actionsCopy;
        if (actions.isEmpty()) {
            actionsCopy = Collections.emptyMap();
        } else {
            Map<String, List<String>> tmp = new LinkedHashMap<>();
            for (Map.Entry<String, List<String>> e : actions.entrySet()) {
                String k = e.getKey();
                if (k == null || k.isEmpty()) continue;
                List<String> v = e.getValue();
                if (v == null || v.isEmpty()) continue;
                tmp.put(k, new ArrayList<>(v));
            }
            actionsCopy = Collections.unmodifiableMap(tmp);
        }

        return new QuestDef(
                id,
                name,
                event,
                targetsCopy,
                amount,
                repeat,
                points,
                isPublic,
                party,
                type,
                reset,
                display,
                custom,
                startCopy,
                successCopy,
                failCopy,
                actionsCopy,
                nextQuestOnComplete,
                startMode
        );
    }

    // QuestDef -> Draft 변환
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

        d.targets.addAll(q.targets);

        d.resetPolicy = q.reset.policy;
        d.resetTime = q.reset.time;

        d.nextQuestOnComplete = q.nextQuestOnComplete;

        d.displayTitle = q.display.title;
        d.displayDescription.addAll(q.display.description);
        d.displayProgress = q.display.progress;
        d.displayReward = q.display.reward;
        d.displayCategory = q.display.category;
        d.displayDifficulty = q.display.difficulty;
        d.displayIcon = q.display.icon;
        d.displayHint = q.display.hint;
        d.displayCustomModelData = q.display.customModelData;

        d.condStart.addAll(q.condStart);
        d.condSuccess.addAll(q.condSuccess);
        d.condFail.addAll(q.condFail);

        if (q.custom != null) {
            d.customEventClass = q.custom.eventClass;
            d.customPlayerGetter = q.custom.playerGetter;
            if (q.custom.captures != null) {
                d.customCaptures.putAll(q.custom.captures);
            }
        }

        if (q.actions != null) {
            for (Map.Entry<String, List<String>> e : q.actions.entrySet()) {
                d.actions.put(e.getKey(), new ArrayList<>(e.getValue()));
            }
        }

        return d;
    }
}
