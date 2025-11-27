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
    protected abstract String upsertSql(); // DB별 문법 차이 해결

    protected synchronized Connection connection() {
        try {
            if (conn != null && !conn.isClosed()) {
                // 간단한 유효성 체크 (1초 타임아웃)
                if (!conn.isValid(1)) {
                    conn.close();
                    conn = null;
                } else {
                    return conn;
                }
            }

            Class.forName(driverClass());
            conn = DriverManager.getConnection(url, props);
            conn.setAutoCommit(true); // 기본은 AutoCommit

            if (init.compareAndSet(false, true)) {
                try (Statement st = conn.createStatement()) {
                    st.executeUpdate(createTableSql());
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
        String sql = "SELECT quest_id, active, completed, value, points, repeat_count FROM qe_progress WHERE uuid = ?";

        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, id.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String qid = rs.getString(1);
                    boolean active = rs.getInt(2) == 1;
                    boolean completed = rs.getInt(3) == 1;
                    int value = rs.getInt(4);
                    int points = rs.getInt(5);
                    int repeat = rs.getInt(6);

                    // PlayerData 내부 로직 활용 (직접 필드 주입보다 안전)
                    if (active) d.start(qid);
                    if (completed) d.complete(qid, points, -1); // -1: force set state
                    if (value > 0) d.add(qid, value);
                    if (repeat > 0) d.setRepeatCount(qid, repeat);
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

        // [최적화] 트랜잭션 시작
        try {
            boolean autoCommit = c.getAutoCommit();
            c.setAutoCommit(false);

            try (PreparedStatement ps = c.prepareStatement(upsertSql())) {
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
                c.commit(); // 커밋
            } catch (Throwable t) {
                c.rollback();
                throw t;
            } finally {
                c.setAutoCommit(autoCommit); // 원복
            }
        } catch (Throwable t) {
            plugin.getLogger().warning("SQL save failed: " + t.getMessage());
        }
    }

    @Override
    public Map<UUID, Integer> loadAllPointsApprox() {
        Connection c = connection();
        if (c == null) return Collections.emptyMap();

        Map<UUID, Integer> out = new HashMap<>();
        String sql = "SELECT uuid, SUM(points) FROM qe_progress WHERE completed = 1 GROUP BY uuid";

        try (Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                try {
                    out.put(UUID.fromString(rs.getString(1)), rs.getInt(2));
                } catch (Exception ignored) {}
            }
        } catch (Throwable t) {
            plugin.getLogger().warning("SQL loadAllPointsApprox failed: " + t.getMessage());
        }
        return out;
    }

    @Override public void preloadAll() {}

    @Override
    public void reset(UUID id) {
        Connection c = connection();
        if (c == null) return;
        try (PreparedStatement ps = c.prepareStatement("DELETE FROM qe_progress WHERE uuid = ?")) {
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
        try (PreparedStatement ps = c.prepareStatement("DELETE FROM qe_progress WHERE uuid = ? AND quest_id = ?")) {
            ps.setString(1, id.toString());
            ps.setString(2, questId);
            ps.executeUpdate();
        } catch (Throwable t) {
            plugin.getLogger().warning("SQL resetQuest failed: " + t.getMessage());
        }
    }

    @Override
    public void close() {
        try { if (conn != null) conn.close(); } catch (Throwable ignored) {}
    }
}