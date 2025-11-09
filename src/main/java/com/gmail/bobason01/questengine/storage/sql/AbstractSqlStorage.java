package com.gmail.bobason01.questengine.storage.sql;

import com.gmail.bobason01.questengine.QuestEnginePlugin;
import com.gmail.bobason01.questengine.progress.PlayerData;
import com.gmail.bobason01.questengine.storage.StorageProvider;

import java.sql.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public abstract class AbstractSqlStorage implements StorageProvider {

    protected final QuestEnginePlugin plugin;
    protected final String url;
    protected final Properties props;
    private Connection conn;
    private final AtomicBoolean init = new AtomicBoolean(false);

    protected AbstractSqlStorage(QuestEnginePlugin plugin, String url, Properties props) {
        this.plugin = plugin;
        this.url = url;
        this.props = props;
    }

    protected abstract String driverClass();
    protected abstract String createTableSql();

    protected synchronized Connection connection() {
        try {
            if (conn == null || conn.isClosed()) {
                Class.forName(driverClass());
                conn = DriverManager.getConnection(url, props);
                conn.setAutoCommit(true);
                if (init.compareAndSet(false, true)) {
                    try (Statement st = conn.createStatement()) {
                        st.executeUpdate(createTableSql());
                    }
                }
            }
            return conn;
        } catch (Throwable t) {
            plugin.getLogger().warning("[SQL] connection error: " + t.getMessage());
            return null;
        }
    }

    @Override
    public PlayerData load(UUID id, String name) {
        Connection c = connection();
        if (c == null) return new PlayerData(id, name);
        PlayerData d = new PlayerData(id, name);
        String sql = "select quest_id, active, completed, value, points from qe_progress where uuid = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, id.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String qid = rs.getString(1);
                    boolean active = rs.getInt(2) == 1;
                    boolean completed = rs.getInt(3) == 1;
                    int value = rs.getInt(4);
                    int points = rs.getInt(5);
                    if (active) d.start(qid);
                    if (completed) d.complete(qid, points);
                    if (value > 0) d.add(qid, value);
                }
            }
        } catch (Throwable t) {
            plugin.getLogger().warning("[SQL] load failed for " + id + ": " + t.getMessage());
        }
        return d;
    }

    @Override
    public void save(PlayerData d) {
        Connection c = connection();
        if (c == null) return;
        Set<String> all = new HashSet<>(d.activeIds());
        all.addAll(d.completedIds());
        String upsert = "insert into qe_progress (uuid, quest_id, active, completed, value, points) values (?, ?, ?, ?, ?, ?) " +
                "on conflict(uuid, quest_id) do update set active = excluded.active, completed = excluded.completed, value = excluded.value, points = excluded.points";
        try (PreparedStatement ps = c.prepareStatement(upsert)) {
            for (String qid : all) {
                ps.setString(1, d.getId().toString());
                ps.setString(2, qid);
                ps.setInt(3, d.isActive(qid) ? 1 : 0);
                ps.setInt(4, d.isCompleted(qid) ? 1 : 0);
                ps.setInt(5, d.valueOf(qid));
                ps.setInt(6, d.isCompleted(qid) ? d.pointsOf(qid) : 0);
                ps.addBatch();
            }
            ps.executeBatch();
        } catch (Throwable t) {
            plugin.getLogger().warning("[SQL] save failed for " + d.getId() + ": " + t.getMessage());
        }
    }

    @Override
    public Map<UUID, Integer> loadAllPointsApprox() {
        Connection c = connection();
        Map<UUID, Integer> map = new HashMap<>();
        if (c == null) return map;
        String sql = "select uuid, sum(points) from qe_progress where completed = 1 group by uuid";
        try (PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                try {
                    UUID id = UUID.fromString(rs.getString(1));
                    int total = rs.getInt(2);
                    map.put(id, total);
                } catch (Throwable ignored) {}
            }
        } catch (Throwable t) {
            plugin.getLogger().warning("[SQL] loadAllPoints failed: " + t.getMessage());
        }
        return map;
    }

    @Override
    public void preloadAll() {
        // For SQL backends nothing is required
    }

    @Override
    public void reset(UUID id) {
        Connection c = connection();
        if (c == null) return;
        String sql = "delete from qe_progress where uuid = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, id.toString());
            ps.executeUpdate();
        } catch (Throwable t) {
            plugin.getLogger().warning("[SQL] reset failed for " + id + ": " + t.getMessage());
        }
    }

    @Override
    public void resetQuest(UUID id, String questId) {
        Connection c = connection();
        if (c == null) return;
        String sql = "delete from qe_progress where uuid = ? and quest_id = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, id.toString());
            ps.setString(2, questId);
            ps.executeUpdate();
        } catch (Throwable t) {
            plugin.getLogger().warning("[SQL] resetQuest failed for " + id + ", " + questId + ": " + t.getMessage());
        }
    }

    @Override
    public void close() {
        try {
            if (conn != null) conn.close();
        } catch (Throwable ignored) {}
    }
}
