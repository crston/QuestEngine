package com.gmail.bobason01.questengine.storage.sql;

import com.gmail.bobason01.questengine.QuestEnginePlugin;
import com.zaxxer.hikari.HikariConfig;
import java.io.File;

public final class SQLiteStorage extends AbstractSqlStorage {

    public SQLiteStorage(QuestEnginePlugin plugin) {
        super(plugin, buildConfig(plugin));
    }

    private static HikariConfig buildConfig(QuestEnginePlugin plugin) {
        String path = plugin.getConfig().getString("storage.sqlite.file", "data/questengine.db");
        File f = new File(plugin.getDataFolder(), path);
        if (!f.getParentFile().exists()) f.getParentFile().mkdirs();

        HikariConfig config = new HikariConfig();
        config.setDriverClassName("org.sqlite.JDBC");
        config.setJdbcUrl("jdbc:sqlite:" + f.getAbsolutePath());

        // SQLite는 풀 사이즈 1이 안전함 (파일 락 때문)
        config.setMaximumPoolSize(1);
        config.setConnectionTestQuery("SELECT 1");

        return config;
    }

    @Override
    protected String createTableSql() {
        return "CREATE TABLE IF NOT EXISTS qe_progress (" +
                "uuid TEXT NOT NULL," +
                "quest_id TEXT NOT NULL," +
                "active INTEGER NOT NULL," +
                "completed INTEGER NOT NULL," +
                "value INTEGER NOT NULL," +
                "points INTEGER NOT NULL," +
                "repeat_count INTEGER NOT NULL DEFAULT 0," +
                "PRIMARY KEY (uuid, quest_id)" +
                ")";
    }

    @Override
    protected String upsertSql() {
        return "INSERT INTO qe_progress (uuid, quest_id, active, completed, value, points, repeat_count) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?) " +
                "ON CONFLICT(uuid, quest_id) DO UPDATE SET " +
                "active=excluded.active, completed=excluded.completed, value=excluded.value, " +
                "points=excluded.points, repeat_count=excluded.repeat_count";
    }
}