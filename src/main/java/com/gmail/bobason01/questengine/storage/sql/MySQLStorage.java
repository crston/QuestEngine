package com.gmail.bobason01.questengine.storage.sql;

import com.gmail.bobason01.questengine.QuestEnginePlugin;
import com.zaxxer.hikari.HikariConfig;

public final class MySQLStorage extends AbstractSqlStorage {

    public MySQLStorage(QuestEnginePlugin plugin) {
        super(plugin, buildConfig(plugin));
    }

    private static HikariConfig buildConfig(QuestEnginePlugin plugin) {
        String host = plugin.getConfig().getString("storage.mysql.host", "localhost");
        int port = plugin.getConfig().getInt("storage.mysql.port", 3306);
        String db = plugin.getConfig().getString("storage.mysql.database", "questengine");
        String user = plugin.getConfig().getString("storage.mysql.user", "root");
        String pass = plugin.getConfig().getString("storage.mysql.password", "");

        HikariConfig config = new HikariConfig();
        // MySQL 드라이버 클래스 지정 (명시적)
        config.setDriverClassName("com.mysql.cj.jdbc.Driver");
        config.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + db);
        config.setUsername(user);
        config.setPassword(pass);

        // MySQL 필수/성능 파라미터
        config.addDataSourceProperty("useSSL", "false");
        config.addDataSourceProperty("characterEncoding", "utf8");
        config.addDataSourceProperty("serverTimezone", "UTC");

        // 성능 최적화 설정 (MySQL 특화)
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        config.addDataSourceProperty("useServerPrepStmts", "true");
        config.addDataSourceProperty("useLocalSessionState", "true");
        config.addDataSourceProperty("rewriteBatchedStatements", "true");
        config.addDataSourceProperty("cacheResultSetMetadata", "true");
        config.addDataSourceProperty("maintainTimeStats", "false");

        return config;
    }

    @Override
    protected String createTableSql() {
        return "CREATE TABLE IF NOT EXISTS qe_progress (" +
                "uuid VARCHAR(36) NOT NULL," +
                "quest_id VARCHAR(128) NOT NULL," +
                "active TINYINT NOT NULL," +
                "completed TINYINT NOT NULL," +
                "value INT NOT NULL," +
                "points INT NOT NULL," +
                "repeat_count INT NOT NULL DEFAULT 0," +
                "PRIMARY KEY (uuid, quest_id)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";
    }

    @Override
    protected String upsertSql() {
        return "INSERT INTO qe_progress (uuid, quest_id, active, completed, value, points, repeat_count) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE " +
                "active=VALUES(active), completed=VALUES(completed), value=VALUES(value), " +
                "points=VALUES(points), repeat_count=VALUES(repeat_count)";
    }
}