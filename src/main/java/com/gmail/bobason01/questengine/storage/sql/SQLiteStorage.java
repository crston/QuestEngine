package com.gmail.bobason01.questengine.storage.sql;

import com.gmail.bobason01.questengine.QuestEnginePlugin;
import java.io.File;
import java.util.Properties;

public final class SQLiteStorage extends AbstractSqlStorage {

    public SQLiteStorage(QuestEnginePlugin plugin) {
        super(plugin, buildUrl(plugin), new Properties());
    }

    private static String buildUrl(QuestEnginePlugin plugin) {
        String path = plugin.getConfig().getString("storage.sqlite.file", "data/questengine.db");
        File f = new File(plugin.getDataFolder(), path);
        if (!f.getParentFile().exists()) f.getParentFile().mkdirs();
        return "jdbc:sqlite:" + f.getAbsolutePath();
    }

    @Override
    protected String driverClass() {
        return "org.sqlite.JDBC";
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
        // SQLite: ON CONFLICT DO UPDATE
        return "INSERT INTO qe_progress (uuid, quest_id, active, completed, value, points, repeat_count) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?) " +
                "ON CONFLICT(uuid, quest_id) DO UPDATE SET " +
                "active=excluded.active, completed=excluded.completed, value=excluded.value, " +
                "points=excluded.points, repeat_count=excluded.repeat_count";
    }
}