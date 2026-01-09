package com.gmail.bobason01.questengine.storage.sql;

import com.gmail.bobason01.questengine.QuestEnginePlugin;
import com.gmail.bobason01.questengine.progress.PlayerData;
import com.gmail.bobason01.questengine.storage.StorageProvider;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.*;

public abstract class AbstractSqlStorage implements StorageProvider {

    protected final QuestEnginePlugin plugin;
    private final HikariDataSource dataSource;

    protected AbstractSqlStorage(QuestEnginePlugin plugin, HikariConfig config) {
        this.plugin = plugin;

        // HikariCP 풀 이름 설정 (디버깅 용이)
        config.setPoolName("QuestEngine-Pool");
        // 커넥션 풀 설정 (기본값 제안)
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(2);
        config.setConnectionTimeout(5000); // 5초
        config.setMaxLifetime(1800000); // 30분

        this.dataSource = new HikariDataSource(config);

        ensureTable();
    }

    protected abstract String createTableSql();
    protected abstract String upsertSql();

    private void ensureTable() {
        try (Connection conn = dataSource.getConnection();
             Statement st = conn.createStatement()) {
            st.executeUpdate(createTableSql());
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to create tables: " + e.getMessage());
        }
    }

    // 커넥션 획득 메서드
    protected Connection getConnection() throws Exception {
        return dataSource.getConnection();
    }

    @Override
    public PlayerData load(UUID id, String name) {
        PlayerData d = new PlayerData(id, name);
        String sql = "SELECT quest_id, active, completed, value, points, repeat_count FROM qe_progress WHERE uuid = ?";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String qid = rs.getString(1);
                    boolean active = rs.getInt(2) == 1;
                    boolean completed = rs.getInt(3) == 1;
                    int value = rs.getInt(4);
                    int points = rs.getInt(5);
                    int repeat = rs.getInt(6);

                    if (active) d.start(qid);
                    if (completed) d.complete(qid, points, -1);
                    if (value > 0) d.add(qid, value);
                    if (repeat > 0) d.setRepeatCount(qid, repeat);
                }
            }
        } catch (Exception t) {
            plugin.getLogger().warning("SQL load failed: " + t.getMessage());
        }
        return d;
    }

    @Override
    public void save(PlayerData d) {
        try (Connection conn = getConnection()) {
            boolean autoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);

            try (PreparedStatement ps = conn.prepareStatement(upsertSql())) {
                Set<String> allQuests = new HashSet<>();
                allQuests.addAll(d.getActiveQuests());
                allQuests.addAll(d.getCompletedQuests());

                for (String qid : allQuests) {
                    ps.setString(1, d.getId().toString());
                    ps.setString(2, qid);
                    ps.setInt(3, d.isActive(qid) ? 1 : 0);
                    ps.setInt(4, d.isCompleted(qid) ? 1 : 0);
                    ps.setInt(5, d.valueOf(qid));
                    ps.setInt(6, d.pointsOf(qid));
                    ps.setInt(7, d.getRepeatCount(qid));
                    ps.addBatch();
                }
                ps.executeBatch();
                conn.commit();
            } catch (Exception t) {
                conn.rollback();
                throw t;
            } finally {
                conn.setAutoCommit(autoCommit);
            }
        } catch (Exception t) {
            plugin.getLogger().warning("SQL save failed: " + t.getMessage());
        }
    }

    @Override
    public Map<UUID, Integer> loadAllPointsApprox() {
        Map<UUID, Integer> out = new HashMap<>();
        String sql = "SELECT uuid, SUM(points) FROM qe_progress WHERE completed = 1 GROUP BY uuid";

        try (Connection conn = getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                try {
                    out.put(UUID.fromString(rs.getString(1)), rs.getInt(2));
                } catch (Exception ignored) {}
            }
        } catch (Exception t) {
            plugin.getLogger().warning("SQL loadAllPointsApprox failed: " + t.getMessage());
        }
        return out;
    }

    @Override
    public void reset(UUID id) {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM qe_progress WHERE uuid = ?")) {
            ps.setString(1, id.toString());
            ps.executeUpdate();
        } catch (Exception t) {
            plugin.getLogger().warning("SQL reset failed: " + t.getMessage());
        }
    }

    @Override
    public void resetQuest(UUID id, String questId) {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM qe_progress WHERE uuid = ? AND quest_id = ?")) {
            ps.setString(1, id.toString());
            ps.setString(2, questId);
            ps.executeUpdate();
        } catch (Exception t) {
            plugin.getLogger().warning("SQL resetQuest failed: " + t.getMessage());
        }
    }

    @Override public void preloadAll() {}

    @Override
    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }
}