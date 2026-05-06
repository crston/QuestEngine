package com.gmail.bobason01.questengine.api;

import com.gmail.bobason01.questengine.QuestEnginePlugin;
import com.gmail.bobason01.questengine.progress.PlayerData;
import com.gmail.bobason01.questengine.quest.QuestDef;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public final class QuestEngineAPI {

    private QuestEngineAPI() {}

    public static QuestEnginePlugin getPlugin() {
        return JavaPlugin.getPlugin(QuestEnginePlugin.class);
    }

    public static QuestDef getQuest(String id) {
        if (id == null) return null;
        return getPlugin().quests().get(id);
    }

    public static Collection<QuestDef> getAllQuests() {
        return getPlugin().quests().all();
    }

    public static PlayerData getPlayerData(UUID uuid, String name) {
        if (uuid == null) return null;
        return getPlugin().progress().of(uuid, name);
    }

    public static boolean isActive(UUID uuid, String name, String questId) {
        if (uuid == null || questId == null) return false;
        return getPlugin().progress().isActive(uuid, name, questId);
    }

    public static boolean isCompleted(UUID uuid, String name, String questId) {
        if (uuid == null || questId == null) return false;
        return getPlugin().progress().isCompleted(uuid, name, questId);
    }

    public static int getQuestProgress(UUID uuid, String name, String questId) {
        if (uuid == null || questId == null) return 0;
        return getPlugin().progress().value(uuid, name, questId);
    }

    public static List<String> getActiveQuests(UUID uuid, String name) {
        if (uuid == null) return null;
        return getPlugin().progress().activeQuestIds(uuid, name);
    }

    public static List<String> getCompletedQuests(UUID uuid, String name) {
        if (uuid == null) return null;
        return getPlugin().progress().completedQuestIds(uuid, name);
    }

    public static void startQuest(Player player, String questId) {
        if (player == null || questId == null) return;
        getPlugin().engine().startQuest(player, questId);
    }

    public static void cancelQuest(Player player, String questId) {
        if (player == null || questId == null) return;
        getPlugin().engine().cancelQuest(player, questId);
    }

    public static void forceCompleteQuest(UUID uuid, String name, String questId) {
        if (uuid == null || name == null || questId == null) return;
        getPlugin().engine().forceComplete(uuid, name, questId);
    }

    public static void forceCompleteQuest(Player player, String questId) {
        if (player == null || questId == null) return;
        QuestDef quest = getQuest(questId);
        if (quest != null) {
            getPlugin().engine().forceComplete(player, quest);
        }
    }

    public static void stopQuest(UUID uuid, String name, String questId) {
        if (uuid == null || name == null || questId == null) return;
        getPlugin().engine().stopQuest(uuid, name, questId);
    }

    public static int addQuestProgress(UUID uuid, String name, String questId, int amount) {
        if (uuid == null || questId == null) return 0;
        return getPlugin().progress().addProgress(uuid, name, questId, amount);
    }

    public static void resetQuestProgress(UUID uuid, String name, String questId) {
        if (uuid == null || questId == null) return;
        getPlugin().progress().reset(uuid, name, questId);
    }

    public static void openQuestListMenu(Player player, int page) {
        if (player == null) return;
        getPlugin().gui().openList(player, page);
    }

    public static void openPublicQuestMenu(Player player, int page) {
        if (player == null) return;
        getPlugin().gui().openPublic(player, page);
    }
}