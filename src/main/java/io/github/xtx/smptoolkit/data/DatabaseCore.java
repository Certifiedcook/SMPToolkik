package io.github.xtx.smptoolkit.data;

import io.github.xtx.smptoolkit.SMPToolkitPlugin;
import java.io.File;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;

class DatabaseCore {
    DatabaseCore(SMPToolkitPlugin plugin) { this.plugin = plugin; }
    protected final SMPToolkitPlugin plugin;
    protected final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "SMPToolkit-Database");
        t.setDaemon(true);
        return t;
    });
    protected Connection connection;
    public synchronized void init() throws SQLException {
        File dbFile = new File(plugin.getDataFolder(), plugin.getConfig().getString("database.file", "data.db"));
        dbFile.getParentFile().mkdirs();
        connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
        try (Statement s = connection.createStatement()) {
            s.execute("PRAGMA journal_mode=WAL");
            s.execute("PRAGMA foreign_keys=ON");
            s.execute("PRAGMA busy_timeout=5000");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS players(uuid TEXT PRIMARY KEY,name TEXT NOT NULL,first_join INTEGER NOT NULL,last_join INTEGER NOT NULL,playtime_ms INTEGER NOT NULL DEFAULT 0)");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS stats(uuid TEXT PRIMARY KEY,kills INTEGER NOT NULL DEFAULT 0,deaths INTEGER NOT NULL DEFAULT 0,chat_wins INTEGER NOT NULL DEFAULT 0,FOREIGN KEY(uuid) REFERENCES players(uuid) ON DELETE CASCADE)");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS punishments(id INTEGER PRIMARY KEY AUTOINCREMENT,uuid TEXT NOT NULL,type TEXT NOT NULL,reason TEXT NOT NULL,staff_uuid TEXT,staff_name TEXT NOT NULL,created_at INTEGER NOT NULL,expires_at INTEGER,active INTEGER NOT NULL DEFAULT 1)");
            s.executeUpdate("CREATE INDEX IF NOT EXISTS idx_punishments_uuid ON punishments(uuid)");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS notes(id INTEGER PRIMARY KEY AUTOINCREMENT,uuid TEXT NOT NULL,staff_uuid TEXT,staff_name TEXT NOT NULL,note TEXT NOT NULL,created_at INTEGER NOT NULL)");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS ip_history(id INTEGER PRIMARY KEY AUTOINCREMENT,uuid TEXT NOT NULL,name TEXT NOT NULL,ip_hash TEXT NOT NULL,seen_at INTEGER NOT NULL)");
            s.executeUpdate("CREATE INDEX IF NOT EXISTS idx_ip_hash ON ip_history(ip_hash)");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS reports(id INTEGER PRIMARY KEY AUTOINCREMENT,reporter_uuid TEXT NOT NULL,reporter_name TEXT NOT NULL,target_uuid TEXT NOT NULL,target_name TEXT NOT NULL,reason TEXT NOT NULL,status TEXT NOT NULL DEFAULT 'OPEN',staff_uuid TEXT,staff_name TEXT,created_at INTEGER NOT NULL,closed_at INTEGER,result TEXT)");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS homes(uuid TEXT NOT NULL,name TEXT NOT NULL,location TEXT NOT NULL,PRIMARY KEY(uuid,name))");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS locations(uuid TEXT NOT NULL,key TEXT NOT NULL,location TEXT NOT NULL,PRIMARY KEY(uuid,key))");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS flags(uuid TEXT NOT NULL,key TEXT NOT NULL,value TEXT NOT NULL,PRIMARY KEY(uuid,key))");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS ignores(owner_uuid TEXT NOT NULL,target_uuid TEXT NOT NULL,PRIMARY KEY(owner_uuid,target_uuid))");
        }
    }
    public void close() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) executor.shutdownNow();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }
        synchronized (this) {
            if (connection != null) {
                try { connection.close(); } catch (SQLException ignored) {}
                connection = null;
            }
        }
    }
    public CompletableFuture<Void> runAsync(SqlRunnable action) {
        return CompletableFuture.runAsync(() -> {
            synchronized (this) {
                try { action.run(connection); }
                catch (SQLException ex) { throw new RuntimeException(ex); }
            }
        }, executor);
    }
    public <T> CompletableFuture<T> supplyAsync(SqlSupplier<T> action) {
        return CompletableFuture.supplyAsync(() -> {
            synchronized (this) {
                try { return action.get(connection); }
                catch (SQLException ex) { throw new RuntimeException(ex); }
            }
        }, executor);
    }
    public CompletableFuture<Void> upsertJoin(UUID uuid, String name) {
        long now = System.currentTimeMillis();
        return runAsync(c -> {
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO players(uuid,name,first_join,last_join,playtime_ms) VALUES(?,?,?,?,0) ON CONFLICT(uuid) DO UPDATE SET name=excluded.name,last_join=excluded.last_join")) {
                ps.setString(1, uuid.toString()); ps.setString(2, name); ps.setLong(3, now); ps.setLong(4, now); ps.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement("INSERT OR IGNORE INTO stats(uuid) VALUES(?)")) { ps.setString(1, uuid.toString()); ps.executeUpdate(); }
        });
    }
    public CompletableFuture<Void> addPlaytime(UUID uuid, long millis) {
        return runAsync(c -> {
            try (PreparedStatement ps = c.prepareStatement("UPDATE players SET playtime_ms=playtime_ms+?,last_join=? WHERE uuid=?")) {
                ps.setLong(1, Math.max(0, millis)); ps.setLong(2, System.currentTimeMillis()); ps.setString(3, uuid.toString()); ps.executeUpdate();
            }
        });
    }
    public CompletableFuture<Void> incrementStat(UUID uuid, String column) {
        if (!Set.of("kills", "deaths", "chat_wins").contains(column)) throw new IllegalArgumentException("Invalid stat column");
        return runAsync(c -> {
            try (PreparedStatement ensure = c.prepareStatement("INSERT OR IGNORE INTO stats(uuid) VALUES(?)")) { ensure.setString(1, uuid.toString()); ensure.executeUpdate(); }
            try (PreparedStatement ps = c.prepareStatement("UPDATE stats SET " + column + "=" + column + "+1 WHERE uuid=?")) { ps.setString(1, uuid.toString()); ps.executeUpdate(); }
        });
    }
    public CompletableFuture<Database.Stats> stats(UUID uuid) {
        return supplyAsync(c -> {
            try (PreparedStatement ps = c.prepareStatement("SELECT p.name,p.first_join,p.last_join,p.playtime_ms,s.kills,s.deaths,s.chat_wins FROM players p LEFT JOIN stats s ON s.uuid=p.uuid WHERE p.uuid=?")) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) return new Database.Stats("Unknown", 0,0,0,0,0,0);
                    return new Database.Stats(rs.getString("name"), rs.getInt("kills"), rs.getInt("deaths"), rs.getInt("chat_wins"), rs.getLong("playtime_ms"), rs.getLong("first_join"), rs.getLong("last_join"));
                }
            }
        });
    }
    public CompletableFuture<List<Database.LeaderboardEntry>> leaderboard(String metric, int limit) {
        String order = switch (metric) {
            case "kills" -> "s.kills DESC";
            case "deaths" -> "s.deaths DESC";
            case "chat_wins" -> "s.chat_wins DESC";
            case "playtime" -> "p.playtime_ms DESC";
            case "kd" -> "CASE WHEN s.deaths=0 THEN s.kills ELSE CAST(s.kills AS REAL)/s.deaths END DESC";
            default -> throw new IllegalArgumentException("Invalid leaderboard metric");
        };
        return supplyAsync(c -> {
            List<Database.LeaderboardEntry> out = new ArrayList<>();
            String sql = "SELECT p.uuid,p.name,p.playtime_ms,s.kills,s.deaths,s.chat_wins FROM players p JOIN stats s ON s.uuid=p.uuid ORDER BY " + order + " LIMIT ?";
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setInt(1, limit);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) out.add(new Database.LeaderboardEntry(UUID.fromString(rs.getString("uuid")), rs.getString("name"), rs.getInt("kills"), rs.getInt("deaths"), rs.getInt("chat_wins"), rs.getLong("playtime_ms")));
                }
            }
            return out;
        });
    }
    public CompletableFuture<Optional<Database.PlayerLookup>> findPlayerByName(String name) {
        return supplyAsync(c -> {
            try (PreparedStatement ps = c.prepareStatement("SELECT uuid,name,first_join,last_join,playtime_ms FROM players WHERE lower(name)=lower(?) ORDER BY last_join DESC LIMIT 1")) {
                ps.setString(1, name);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) return Optional.empty();
                    return Optional.of(new Database.PlayerLookup(UUID.fromString(rs.getString("uuid")), rs.getString("name"), rs.getLong("first_join"), rs.getLong("last_join"), rs.getLong("playtime_ms")));
                }
            }
        });
    }
    @FunctionalInterface public interface SqlRunnable { void run(Connection c) throws SQLException; }
    @FunctionalInterface public interface SqlSupplier<T> { T get(Connection c) throws SQLException; }
}
