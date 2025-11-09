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
        return "create table if not exists qe_progress (" +
                "uuid text not null," +
                "quest_id text not null," +
                "active integer not null," +
                "completed integer not null," +
                "value integer not null," +
                "points integer not null," +
                "primary key (uuid, quest_id)" +
                ")";
    }
}
