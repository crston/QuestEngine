package com.gmail.bobason01.questengine.gui.editor;

import com.gmail.bobason01.questengine.quest.CustomEventData;
import com.gmail.bobason01.questengine.quest.QuestDef;
import java.util.*;

public final class QuestEditorDraft {
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

    public final List<String> targets = new ArrayList<>(4);
    public final List<String> condStart = new ArrayList<>(4);
    public final List<String> condSuccess = new ArrayList<>(4);
    public final List<String> condFail = new ArrayList<>(4);
    public final List<String> requiredQuests = new ArrayList<>(4);

    public String resetPolicy = "";
    public String resetTime = "";
    public String nextQuestOnComplete = "";

    public String displayTitle = "&fNew Quest";
    public final List<String> displayDescription = new ArrayList<>(4);
    public String displayProgress = "&7%value%/%target%";
    public String displayReward = "";
    public String displayCategory = "";
    public String displayDifficulty = "";
    public String displayIcon = "BOOK";
    public String displayHint = "";
    // int에서 String으로 변경하여 nexo:tomato 등 지원
    public String displayModel = "-1";

    public String leftClickTip = "";
    public String leftClickCommand = "";

    public String customEventClass = "";
    public String customPlayerGetter = "getPlayer()";
    public final Map<String, String> customCaptures = new LinkedHashMap<>(4);
    public final Map<String, List<String>> actions = new LinkedHashMap<>(8);

    public QuestDef buildQuestDef() {
        Map<String, Object> dMap = new LinkedHashMap<>(12);
        dMap.put("title", displayTitle);
        dMap.put("description", new ArrayList<>(displayDescription));
        dMap.put("progress", displayProgress);
        dMap.put("reward", displayReward);
        dMap.put("category", displayCategory);
        dMap.put("difficulty", displayDifficulty);
        dMap.put("icon", displayIcon);
        dMap.put("hint", displayHint);
        dMap.put("model", displayModel); // 키값 변경
        dMap.put("left_click_tip", leftClickTip);
        dMap.put("left_click_command", leftClickCommand);

        QuestDef.Display display = new QuestDef.Display(dMap);
        QuestDef.Reset reset = new QuestDef.Reset(resetPolicy, resetTime);

        CustomEventData custom = null;
        if (customEventClass != null && !customEventClass.isEmpty()) {
            custom = new CustomEventData(customEventClass, customPlayerGetter, Map.copyOf(customCaptures));
        }

        Map<String, List<String>> actionsCopy = new HashMap<>();
        actions.forEach((k, v) -> actionsCopy.put(k, List.copyOf(v)));

        return new QuestDef(
                id, name, event, new ArrayList<>(targets), amount, repeat, points,
                isPublic, party, type, reset, display, custom,
                new ArrayList<>(condStart), new ArrayList<>(condSuccess), new ArrayList<>(condFail),
                new ArrayList<>(requiredQuests), actionsCopy, nextQuestOnComplete, startMode
        );
    }

    public static QuestEditorDraft fromQuest(QuestDef q) {
        QuestEditorDraft d = new QuestEditorDraft();
        if (q == null) return d;
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
        d.condStart.addAll(q.condStart);
        d.condSuccess.addAll(q.condSuccess);
        d.condFail.addAll(q.condFail);
        d.requiredQuests.addAll(q.requiredQuests);
        d.resetPolicy = q.reset.policy;
        d.resetTime = q.reset.time;
        d.nextQuestOnComplete = q.nextQuestOnComplete;

        if (q.display != null) {
            d.displayTitle = q.display.title;
            d.displayDescription.addAll(q.display.description);
            d.displayProgress = q.display.progress;
            d.displayReward = q.display.reward;
            d.displayCategory = q.display.category;
            d.displayDifficulty = q.display.difficulty;
            d.displayIcon = q.display.icon;
            d.displayHint = q.display.hint;
            d.displayModel = String.valueOf(q.display.model);
            d.leftClickTip = q.display.leftClickTip;
            d.leftClickCommand = q.display.leftClickCommand;
        }

        if (q.custom != null) {
            d.customEventClass = q.custom.eventClass;
            d.customPlayerGetter = q.custom.playerGetter;
            d.customCaptures.putAll(q.custom.captures);
        }
        q.actions.forEach((k, v) -> d.actions.put(k, new ArrayList<>(v)));
        return d;
    }
}