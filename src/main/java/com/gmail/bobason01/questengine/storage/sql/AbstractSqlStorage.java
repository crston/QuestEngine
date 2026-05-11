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

        config.setPoolName("QuestEngine-Pool");
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(2);
        config.setConnectionTimeout(5000);
        config.setMaxLifetime(1800000);

        this.dataSource = new HikariDataSource(config);

        ensureTable();
    }

    protected abstract String createTableSql();
    protected abstract String createMetaTableSql();
    protected abstract String upsertSql();
    protected abstract String upsertMetaSql();

    private void ensureTable() {
        try (Connection conn = dataSource.getConnection();
             Statement st = conn.createStatement()) {
            st.executeUpdate(createTableSql());
            st.executeUpdate(createMetaTableSql());
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to create tables: " + e.getMessage());
        }
    }

    protected Connection getConnection() throws Exception {
        return dataSource.getConnection();
    }

    @Override
    public PlayerData load(UUID id, String name) {
        PlayerData d = new PlayerData(id, name);
        String sql = "SELECT quest_id, active, completed, value, points, repeat_count FROM qe_progress WHERE uuid = ?";
        String metaSql = "SELECT language FROM qe_player_meta WHERE uuid = ?";

        try (Connection conn = getConnection()) {

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
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
            }

            try (PreparedStatement psMeta = conn.prepareStatement(metaSql)) {
                psMeta.setString(1, id.toString());
                try (ResultSet rsMeta = psMeta.executeQuery()) {
                    if (rsMeta.next()) {
                        d.setLanguage(rsMeta.getString(1));
                    }
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

            try {
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
                }

                try (PreparedStatement psMeta = conn.prepareStatement(upsertMetaSql())) {
                    psMeta.setString(1, d.getId().toString());
                    psMeta.setString(2, d.getLanguage());
                    psMeta.executeUpdate();
                }

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
        try (Connection conn = getConnection()) {
            try (PreparedStatement ps1 = conn.prepareStatement("DELETE FROM qe_progress WHERE uuid = ?")) {
                ps1.setString(1, id.toString());
                ps1.executeUpdate();
            }
            try (PreparedStatement ps2 = conn.prepareStatement("DELETE FROM qe_player_meta WHERE uuid = ?")) {
                ps2.setString(1, id.toString());
                ps2.executeUpdate();
            }
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