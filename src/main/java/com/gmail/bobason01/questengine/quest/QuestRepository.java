package com.gmail.bobason01.questengine.quest;

import org.bukkit.plugin.Plugin;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * QuestRepository
 * 고성능 퀘스트 정의 저장소
 * quests 폴더 내 YAML 퀘스트 자동 로드
 * ID와 EVENT 인덱스 동시 관리
 * 모든 id는 소문자로 통일하여 unknown quest 문제를 방지함
 * reload 시 캐시 완전 갱신
 */
public final class QuestRepository {

    private final Plugin plugin;
    private final File dir;

    /* id -> QuestDef */
    private final Map<String, QuestDef> byId = new ConcurrentHashMap<>();

    /* event -> QuestDef 목록 */
    private final Map<String, List<QuestDef>> byEvent = new ConcurrentHashMap<>();

    public QuestRepository(Plugin plugin, File dir) {
        this.plugin = plugin;
        this.dir = dir;

        if (!dir.exists() && !dir.mkdirs()) {
            plugin.getLogger().warning("QuestEngine failed to create quest folder: " + dir.getAbsolutePath());
        }

        reload();
        rebuildEventMap();
    }

    /* 퀘스트 파일 전체 로드 */
    public void reload() {
        byId.clear();

        File[] files = dir.listFiles((d, n) -> n.endsWith(".yml") || n.endsWith(".yaml"));
        if (files == null || files.length == 0) {
            plugin.getLogger().info("QuestEngine no quest files found in " + dir.getName());
            return;
        }

        int count = 0;

        for (File f : files) {
            try {
                QuestDef q = QuestDef.loadFromFile(f);
                if (q == null || q.id == null || q.id.isBlank()) {
                    plugin.getLogger().warning("QuestEngine skipped invalid quest file: " + f.getName());
                    continue;
                }

                /* 여기서 id를 항상 소문자로 저장 */
                String lid = q.id.toLowerCase(Locale.ROOT);
                byId.put(lid, q);
                count++;

            } catch (Throwable t) {
                plugin.getLogger().warning("QuestEngine failed to load quest " + f.getName() + ": " + t.getMessage());
            }
        }

        plugin.getLogger().info("QuestEngine loaded " + count + " quests from " + dir.getName());
    }

    /* event -> quests 맵 생성 */
    public void rebuildEventMap() {
        byEvent.clear();

        for (QuestDef q : byId.values()) {
            if (q == null || q.event == null || q.event.isBlank()) continue;

            String key = q.event.trim().toUpperCase(Locale.ROOT);
            byEvent.computeIfAbsent(key, k -> new ArrayList<>()).add(q);
        }

        for (Map.Entry<String, List<QuestDef>> e : byEvent.entrySet()) {
            e.setValue(List.copyOf(e.getValue()));
        }

        plugin.getLogger().info("QuestEngine event map built " + byEvent.size() + " event types");
    }

    /* id 조회 통합 */
    public QuestDef get(String id) {
        if (id == null) return null;
        return byId.get(id.toLowerCase(Locale.ROOT));
    }

    /* byId 메서드는 get과 동일하게 통합 */
    public QuestDef byId(String id) {
        return get(id);
    }

    /* ids 반환 */
    public Set<String> ids() {
        return Collections.unmodifiableSet(byId.keySet());
    }

    /* 동일 이벤트를 가진 퀘스트 목록 조회 */
    public QuestDef[] byEvent(String eventKey) {
        if (eventKey == null || eventKey.isBlank()) return new QuestDef[0];
        List<QuestDef> list = byEvent.get(eventKey.trim().toUpperCase(Locale.ROOT));
        if (list == null || list.isEmpty()) return new QuestDef[0];
        return list.toArray(new QuestDef[0]);
    }

    /* 전체 퀘스트 */
    public Collection<QuestDef> all() {
        return Collections.unmodifiableCollection(byId.values());
    }

    /* 퀘스트를 파일로 저장 */
    public void saveToFile(QuestDef quest) {
        if (quest == null || quest.id == null) return;
        File out = new File(dir, quest.id + ".yml");
        try {
            YamlConfiguration yaml = QuestDef.toYaml(quest);
            yaml.save(out);
            plugin.getLogger().info("QuestEngine saved quest " + quest.id);
        } catch (IOException ex) {
            plugin.getLogger().warning("QuestEngine failed to save quest " + quest.id + ": " + ex.getMessage());
        }
    }
}
