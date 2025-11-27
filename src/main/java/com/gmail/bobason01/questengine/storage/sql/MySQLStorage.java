package com.gmail.bobason01.questengine.storage.sql;

import com.gmail.bobason01.questengine.QuestEnginePlugin;
import java.util.Properties;

public final class MySQLStorage extends AbstractSqlStorage {

    public MySQLStorage(QuestEnginePlugin plugin) {
        super(plugin, buildUrl(plugin), buildProps(plugin));
    }

    private static String buildUrl(QuestEnginePlugin plugin) {
        String host = plugin.getConfig().getString("storage.mysql.host", "localhost");
        int port = plugin.getConfig().getInt("storage.mysql.port", 3306);
        String db = plugin.getConfig().getString("storage.mysql.database", "questengine");
        // useSSL=false 권장 (SSL 인증서 없으면 에러 날 수 있음)
        String params = "useSSL=false&characterEncoding=utf8&serverTimezone=UTC&rewriteBatchedStatements=true";
        return "jdbc:mysql://" + host + ":" + port + "/" + db + "?" + params;
    }

    private static Properties buildProps(QuestEnginePlugin plugin) {
        Properties p = new Properties();
        p.setProperty("user", plugin.getConfig().getString("storage.mysql.user", "root"));
        p.setProperty("password", plugin.getConfig().getString("storage.mysql.password", ""));
        return p;
    }

    @Override
    protected String driverClass() {
        return "com.mysql.cj.jdbc.Driver";
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
        // MySQL: ON DUPLICATE KEY UPDATE
        return "INSERT INTO qe_progress (uuid, quest_id, active, completed, value, points, repeat_count) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE " +
                "active=VALUES(active), completed=VALUES(completed), value=VALUES(value), " +
                "points=VALUES(points), repeat_count=VALUES(repeat_count)";
    }
}