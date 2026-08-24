package io.github.xtx.smptoolkit.data;

import io.github.xtx.smptoolkit.SMPToolkitPlugin;
import java.io.File;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;

class DatabaseModeration extends DatabaseCore {
    DatabaseModeration(SMPToolkitPlugin plugin) { super(plugin); }
    public synchronized Optional<Database.Punishment> activePunishmentNow(UUID uuid, String type) {
        try {
            long now = System.currentTimeMillis();
            try (PreparedStatement expire = connection.prepareStatement("UPDATE punishments SET active=0 WHERE uuid=? AND type=? AND active=1 AND expires_at IS NOT NULL AND expires_at<=?")) {
                expire.setString(1, uuid.toString()); expire.setString(2, type); expire.setLong(3, now); expire.executeUpdate();
            }
            try (PreparedStatement ps = connection.prepareStatement("SELECT * FROM punishments WHERE uuid=? AND type=? AND active=1 ORDER BY id DESC LIMIT 1")) {
                ps.setString(1, uuid.toString()); ps.setString(2, type);
                try (ResultSet rs = ps.executeQuery()) { return rs.next() ? Optional.of(readPunishment(rs)) : Optional.empty(); }
            }
        } catch (SQLException ex) {
            plugin.getLogger().severe("Database punishment lookup failed: " + ex.getMessage());
            return Optional.empty();
        }
    }
    public CompletableFuture<Optional<Database.Punishment>> activePunishmentAsync(UUID uuid, String type) {
        return supplyAsync(c -> {
            long now = System.currentTimeMillis();
            try (PreparedStatement expire = c.prepareStatement("UPDATE punishments SET active=0 WHERE uuid=? AND type=? AND active=1 AND expires_at IS NOT NULL AND expires_at<=?")) {
                expire.setString(1, uuid.toString()); expire.setString(2, type); expire.setLong(3, now); expire.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement("SELECT * FROM punishments WHERE uuid=? AND type=? AND active=1 ORDER BY id DESC LIMIT 1")) {
                ps.setString(1, uuid.toString()); ps.setString(2, type);
                try (ResultSet rs = ps.executeQuery()) { return rs.next() ? Optional.of(readPunishment(rs)) : Optional.empty(); }
            }
        });
    }
    public CompletableFuture<Integer> addPunishment(UUID uuid, String type, String reason, UUID staffUuid, String staffName, Long expiresAt) {
        return supplyAsync(c -> {
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO punishments(uuid,type,reason,staff_uuid,staff_name,created_at,expires_at,active) VALUES(?,?,?,?,?,?,?,1)", Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, uuid.toString()); ps.setString(2, type); ps.setString(3, reason);
                if (staffUuid == null) ps.setNull(4, Types.VARCHAR); else ps.setString(4, staffUuid.toString());
                ps.setString(5, staffName); ps.setLong(6, System.currentTimeMillis());
                if (expiresAt == null) ps.setNull(7, Types.BIGINT); else ps.setLong(7, expiresAt);
                ps.executeUpdate();
                try (ResultSet rs = ps.getGeneratedKeys()) { return rs.next() ? rs.getInt(1) : -1; }
            }
        });
    }
    public CompletableFuture<Integer> deactivatePunishments(UUID uuid, String type) {
        return supplyAsync(c -> {
            try (PreparedStatement ps = c.prepareStatement("UPDATE punishments SET active=0 WHERE uuid=? AND type=? AND active=1")) {
                ps.setString(1, uuid.toString()); ps.setString(2, type); return ps.executeUpdate();
            }
        });
    }
    public CompletableFuture<List<Database.Punishment>> history(UUID uuid) {
        return supplyAsync(c -> {
            List<Database.Punishment> out = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement("SELECT * FROM punishments WHERE uuid=? ORDER BY id DESC LIMIT 50")) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) { while (rs.next()) out.add(readPunishment(rs)); }
            }
            return out;
        });
    }
    public CompletableFuture<Void> addNote(UUID uuid, UUID staffUuid, String staffName, String note) {
        return runAsync(c -> {
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO notes(uuid,staff_uuid,staff_name,note,created_at) VALUES(?,?,?,?,?)")) {
                ps.setString(1, uuid.toString());
                if (staffUuid == null) ps.setNull(2, Types.VARCHAR); else ps.setString(2, staffUuid.toString());
                ps.setString(3, staffName); ps.setString(4, note); ps.setLong(5, System.currentTimeMillis()); ps.executeUpdate();
            }
        });
    }
    public CompletableFuture<List<Database.Note>> notes(UUID uuid) {
        return supplyAsync(c -> {
            List<Database.Note> out = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement("SELECT * FROM notes WHERE uuid=? ORDER BY id DESC LIMIT 30")) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) out.add(new Database.Note(rs.getInt("id"), rs.getString("staff_name"), rs.getString("note"), rs.getLong("created_at")));
                }
            }
            return out;
        });
    }
    public CompletableFuture<Void> recordIp(UUID uuid, String name, String hash) {
        return runAsync(c -> {
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO ip_history(uuid,name,ip_hash,seen_at) VALUES(?,?,?,?)")) {
                ps.setString(1, uuid.toString()); ps.setString(2, name); ps.setString(3, hash); ps.setLong(4, System.currentTimeMillis()); ps.executeUpdate();
            }
            long cutoff = System.currentTimeMillis() - plugin.getConfig().getLong("privacy.alt-history-retention-days", 90) * 86400000L;
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM ip_history WHERE seen_at<?")) { ps.setLong(1, cutoff); ps.executeUpdate(); }
        });
    }
    public CompletableFuture<List<Database.AltMatch>> findAlts(UUID uuid) {
        return supplyAsync(c -> {
            List<Database.AltMatch> out = new ArrayList<>();
            String sql = "SELECT h2.uuid,h2.name,MAX(h2.seen_at) last_seen,COUNT(DISTINCT h2.id) matches FROM ip_history h1 JOIN ip_history h2 ON h1.ip_hash=h2.ip_hash AND h1.uuid<>h2.uuid WHERE h1.uuid=? GROUP BY h2.uuid,h2.name ORDER BY last_seen DESC LIMIT 20";
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) out.add(new Database.AltMatch(UUID.fromString(rs.getString("uuid")), rs.getString("name"), rs.getLong("last_seen"), rs.getInt("matches")));
                }
            }
            return out;
        });
    }
    public CompletableFuture<Integer> createReport(UUID reporter, String reporterName, UUID target, String targetName, String reason) {
        return supplyAsync(c -> {
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO reports(reporter_uuid,reporter_name,target_uuid,target_name,reason,created_at) VALUES(?,?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, reporter.toString()); ps.setString(2, reporterName); ps.setString(3, target.toString()); ps.setString(4, targetName); ps.setString(5, reason); ps.setLong(6, System.currentTimeMillis()); ps.executeUpdate();
                try (ResultSet rs = ps.getGeneratedKeys()) { return rs.next() ? rs.getInt(1) : -1; }
            }
        });
    }
    public CompletableFuture<List<Database.ReportRecord>> openReports() {
        return supplyAsync(c -> {
            List<Database.ReportRecord> out = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement("SELECT * FROM reports WHERE status<>'CLOSED' ORDER BY id DESC LIMIT 50"); ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(readReport(rs));
            }
            return out;
        });
    }
    public CompletableFuture<Optional<Database.ReportRecord>> report(int id) {
        return supplyAsync(c -> {
            try (PreparedStatement ps = c.prepareStatement("SELECT * FROM reports WHERE id=?")) {
                ps.setInt(1, id); try (ResultSet rs = ps.executeQuery()) { return rs.next() ? Optional.of(readReport(rs)) : Optional.empty(); }
            }
        });
    }
    public CompletableFuture<Boolean> claimReport(int id, UUID staff, String staffName) {
        return supplyAsync(c -> {
            try (PreparedStatement ps = c.prepareStatement("UPDATE reports SET status='CLAIMED',staff_uuid=?,staff_name=? WHERE id=? AND status='OPEN'")) {
                ps.setString(1, staff.toString()); ps.setString(2, staffName); ps.setInt(3, id); return ps.executeUpdate() > 0;
            }
        });
    }
    public CompletableFuture<Boolean> closeReport(int id, UUID staff, String staffName, String result) {
        return supplyAsync(c -> {
            try (PreparedStatement ps = c.prepareStatement("UPDATE reports SET status='CLOSED',staff_uuid=?,staff_name=?,closed_at=?,result=? WHERE id=? AND status<>'CLOSED'")) {
                ps.setString(1, staff.toString()); ps.setString(2, staffName); ps.setLong(3, System.currentTimeMillis()); ps.setString(4, result); ps.setInt(5, id); return ps.executeUpdate() > 0;
            }
        });
    }
    private static Database.Punishment readPunishment(ResultSet rs) throws SQLException {
        long expires = rs.getLong("expires_at");
        Long exp = rs.wasNull() ? null : expires;
        return new Database.Punishment(rs.getInt("id"), UUID.fromString(rs.getString("uuid")), rs.getString("type"), rs.getString("reason"), rs.getString("staff_name"), rs.getLong("created_at"), exp, rs.getInt("active") != 0);
    }
    private static Database.ReportRecord readReport(ResultSet rs) throws SQLException {
        return new Database.ReportRecord(rs.getInt("id"), rs.getString("reporter_name"), rs.getString("target_name"), rs.getString("reason"), rs.getString("status"), rs.getString("staff_name"), rs.getLong("created_at"), rs.getString("result"));
    }
}
