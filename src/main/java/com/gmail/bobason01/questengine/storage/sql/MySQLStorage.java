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
        String params = "useSSL=false&characterEncoding=utf8&serverTimezone=UTC&rewriteBatchedStatements=true";
        return "jdbc:mysql://" + host + ":" + port + "/" + db + "?" + params;
    }

    private static Properties buildProps(QuestEnginePlugin plugin) {
        Properties p = new Properties();
        p.setProperty("user", plugin.getConfig().getString("storage.mysql.user", "root"));
        p.setProperty("password", plugin.getConfig().getString("storage.mysql.password", ""));
        p.setProperty("autoReconnect", "true");
        p.setProperty("cachePrepStmts", "true");
        p.setProperty("prepStmtCacheSize", "256");
        p.setProperty("prepStmtCacheSqlLimit", "2048");
        return p;
    }

    @Override
    protected String driverClass() {
        return "com.mysql.cj.jdbc.Driver";
    }

    @Override
    protected String createTableSql() {
        return "create table if not exists qe_progress (" +
                "uuid varchar(36) not null," +
                "quest_id varchar(128) not null," +
                "active tinyint not null," +
                "completed tinyint not null," +
                "value int not null," +
                "points int not null," +
                "primary key (uuid, quest_id)" +
                ") engine=InnoDB default charset=utf8mb4";
    }
}
