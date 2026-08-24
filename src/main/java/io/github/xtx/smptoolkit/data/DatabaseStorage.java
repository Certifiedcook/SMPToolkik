package io.github.xtx.smptoolkit.data;

import io.github.xtx.smptoolkit.SMPToolkitPlugin;
import java.io.File;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;

class DatabaseStorage extends DatabaseModeration {
    DatabaseStorage(SMPToolkitPlugin plugin) { super(plugin); }
    public CompletableFuture<Void> setHome(UUID uuid, String name, String encodedLocation) {
        return runAsync(c -> {
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO homes(uuid,name,location) VALUES(?,?,?) ON CONFLICT(uuid,name) DO UPDATE SET location=excluded.location")) {
                ps.setString(1, uuid.toString()); ps.setString(2, name.toLowerCase(Locale.ROOT)); ps.setString(3, encodedLocation); ps.executeUpdate();
            }
        });
    }
    public CompletableFuture<Optional<String>> home(UUID uuid, String name) {
        return supplyAsync(c -> {
            try (PreparedStatement ps = c.prepareStatement("SELECT location FROM homes WHERE uuid=? AND name=?")) {
                ps.setString(1, uuid.toString()); ps.setString(2, name.toLowerCase(Locale.ROOT));
                try (ResultSet rs = ps.executeQuery()) { return rs.next() ? Optional.of(rs.getString(1)) : Optional.empty(); }
            }
        });
    }
    public CompletableFuture<List<String>> homes(UUID uuid) {
        return supplyAsync(c -> {
            List<String> out = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement("SELECT name FROM homes WHERE uuid=? ORDER BY name")) {
                ps.setString(1, uuid.toString()); try (ResultSet rs = ps.executeQuery()) { while (rs.next()) out.add(rs.getString(1)); }
            }
            return out;
        });
    }
    public CompletableFuture<Boolean> deleteHome(UUID uuid, String name) {
        return supplyAsync(c -> {
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM homes WHERE uuid=? AND name=?")) {
                ps.setString(1, uuid.toString()); ps.setString(2, name.toLowerCase(Locale.ROOT)); return ps.executeUpdate() > 0;
            }
        });
    }
    public CompletableFuture<Void> setLocation(UUID uuid, String key, String location) {
        return runAsync(c -> {
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO locations(uuid,key,location) VALUES(?,?,?) ON CONFLICT(uuid,key) DO UPDATE SET location=excluded.location")) {
                ps.setString(1, uuid.toString()); ps.setString(2, key); ps.setString(3, location); ps.executeUpdate();
            }
        });
    }
    public CompletableFuture<Optional<String>> location(UUID uuid, String key) {
        return supplyAsync(c -> {
            try (PreparedStatement ps = c.prepareStatement("SELECT location FROM locations WHERE uuid=? AND key=?")) {
                ps.setString(1, uuid.toString()); ps.setString(2, key); try (ResultSet rs = ps.executeQuery()) { return rs.next() ? Optional.of(rs.getString(1)) : Optional.empty(); }
            }
        });
    }
    public CompletableFuture<Void> setFlag(UUID uuid, String key, String value) {
        return runAsync(c -> {
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO flags(uuid,key,value) VALUES(?,?,?) ON CONFLICT(uuid,key) DO UPDATE SET value=excluded.value")) {
                ps.setString(1, uuid.toString()); ps.setString(2, key); ps.setString(3, value); ps.executeUpdate();
            }
        });
    }
    public CompletableFuture<Optional<String>> flag(UUID uuid, String key) {
        return supplyAsync(c -> {
            try (PreparedStatement ps = c.prepareStatement("SELECT value FROM flags WHERE uuid=? AND key=?")) {
                ps.setString(1, uuid.toString()); ps.setString(2, key); try (ResultSet rs = ps.executeQuery()) { return rs.next() ? Optional.of(rs.getString(1)) : Optional.empty(); }
            }
        });
    }
    public CompletableFuture<Set<UUID>> flagsWithValue(String key, String value) {
        return supplyAsync(c -> {
            Set<UUID> out = new HashSet<>();
            try (PreparedStatement ps = c.prepareStatement("SELECT uuid FROM flags WHERE key=? AND value=?")) {
                ps.setString(1, key); ps.setString(2, value); try (ResultSet rs = ps.executeQuery()) { while (rs.next()) out.add(UUID.fromString(rs.getString(1))); }
            }
            return out;
        });
    }
    public CompletableFuture<Boolean> toggleIgnore(UUID owner, UUID target) {
        return supplyAsync(c -> {
            try (PreparedStatement check = c.prepareStatement("SELECT 1 FROM ignores WHERE owner_uuid=? AND target_uuid=?")) {
                check.setString(1, owner.toString()); check.setString(2, target.toString());
                try (ResultSet rs = check.executeQuery()) {
                    if (rs.next()) {
                        try (PreparedStatement del = c.prepareStatement("DELETE FROM ignores WHERE owner_uuid=? AND target_uuid=?")) { del.setString(1, owner.toString()); del.setString(2, target.toString()); del.executeUpdate(); }
                        return false;
                    }
                }
            }
            try (PreparedStatement add = c.prepareStatement("INSERT INTO ignores(owner_uuid,target_uuid) VALUES(?,?)")) { add.setString(1, owner.toString()); add.setString(2, target.toString()); add.executeUpdate(); }
            return true;
        });
    }
    public CompletableFuture<Set<UUID>> ignoredTargets(UUID owner) {
        return supplyAsync(c -> {
            Set<UUID> out = new HashSet<>();
            try (PreparedStatement ps = c.prepareStatement("SELECT target_uuid FROM ignores WHERE owner_uuid=?")) {
                ps.setString(1, owner.toString());
                try (ResultSet rs = ps.executeQuery()) { while (rs.next()) out.add(UUID.fromString(rs.getString(1))); }
            }
            return out;
        });
    }
    public synchronized boolean isIgnoringNow(UUID owner, UUID target) {
        try (PreparedStatement ps = connection.prepareStatement("SELECT 1 FROM ignores WHERE owner_uuid=? AND target_uuid=?")) {
            ps.setString(1, owner.toString()); ps.setString(2, target.toString()); try (ResultSet rs = ps.executeQuery()) { return rs.next(); }
        } catch (SQLException ex) { return false; }
    }
}
