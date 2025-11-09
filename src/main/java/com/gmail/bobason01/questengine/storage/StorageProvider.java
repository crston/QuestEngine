package com.gmail.bobason01.questengine.storage;


import com.gmail.bobason01.questengine.progress.PlayerData;

import java.util.Map;
import java.util.UUID;

public interface StorageProvider {
    PlayerData load(UUID id, String name);
    void save(PlayerData data);
    Map<UUID, Integer> loadAllPointsApprox();
    void preloadAll();
    void reset(UUID id);
    void resetQuest(UUID id, String questId);
    void close();
}
