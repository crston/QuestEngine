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
            plugin.getLogger().warning("SQL connection error: " + t.getMessage());
            return null;
        }
    }

    @Override
    public PlayerData load(UUID id, String name) {
        Connection c = connection();
        if (c == null) return new PlayerData(id, name);

        PlayerData d = new PlayerData(id, name);

        String sql = "select quest_id, active, completed, value, points, repeat_count from qe_progress where uuid = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, id.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {

                    String qid = rs.getString(1);
                    boolean active = rs.getInt(2) == 1;
                    boolean completed = rs.getInt(3) == 1;
                    int value = rs.getInt(4);
                    int points = rs.getInt(5);
                    int repeatCount = rs.getInt(6);

                    if (active) d.start(qid);

                    if (value > 0) d.add(qid, value);

                    if (repeatCount > 0) {
                        d.setRepeatCount(qid, repeatCount);
                    }

                    if (completed) {
                        d.setRepeatCount(qid, Math.max(1, repeatCount));
                    }
                }
            }
        } catch (Throwable t) {
            plugin.getLogger().warning("SQL load failed: " + t.getMessage());
        }

        return d;
    }

    @Override
    public void save(PlayerData d) {
        Connection c = connection();
        if (c == null) return;

        String upsert =
                "insert into qe_progress (uuid, quest_id, active, completed, value, points, repeat_count) " +
                        "values (?, ?, ?, ?, ?, ?, ?) " +
                        "on conflict(uuid, quest_id) do update set " +
                        "active = excluded.active, completed = excluded.completed, value = excluded.value, " +
                        "points = excluded.points, repeat_count = excluded.repeat_count";

        try (PreparedStatement ps = c.prepareStatement(upsert)) {

            Set<String> base = new HashSet<>();
            base.addAll(d.getActiveQuests());
            base.addAll(d.getCompletedQuests());

            for (String qid : base) {

                int repeat = d.getRepeatCount(qid);

                ps.setString(1, d.getId().toString());
                ps.setString(2, qid);
                ps.setInt(3, d.isActive(qid) ? 1 : 0);
                ps.setInt(4, d.isCompleted(qid) ? 1 : 0);
                ps.setInt(5, d.valueOf(qid));
                ps.setInt(6, d.pointsOf(qid));
                ps.setInt(7, repeat);

                ps.addBatch();
            }
            ps.executeBatch();

        } catch (Throwable t) {
            plugin.getLogger().warning("SQL save failed: " + t.getMessage());
        }
    }

    @Override
    public Map<UUID, Integer> loadAllPointsApprox() {
        Connection c = connection();
        Map<UUID, Integer> out = new HashMap<>();
        if (c == null) return out;

        String sql = "select uuid, sum(points) from qe_progress where completed = 1 group by uuid";

        try (PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                try {
                    UUID uuid = UUID.fromString(rs.getString(1));
                    int total = rs.getInt(2);
                    out.put(uuid, total);
                } catch (Throwable ignored) {}
            }
        } catch (Throwable t) {
            plugin.getLogger().warning("SQL loadAllPointsApprox failed: " + t.getMessage());
        }

        return out;
    }

    @Override
    public void preloadAll() {}

    @Override
    public void reset(UUID id) {
        Connection c = connection();
        if (c == null) return;

        String sql = "delete from qe_progress where uuid = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, id.toString());
            ps.executeUpdate();
        } catch (Throwable t) {
            plugin.getLogger().warning("SQL reset failed: " + t.getMessage());
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
            plugin.getLogger().warning("SQL resetQuest failed: " + t.getMessage());
        }
    }

    @Override
    public void close() {
        try {
            if (conn != null) conn.close();
        } catch (Throwable ignored) {}
    }
}
