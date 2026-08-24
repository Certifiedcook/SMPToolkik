package io.github.xtx.smptoolkit.data;

import io.github.xtx.smptoolkit.SMPToolkitPlugin;
import java.util.UUID;

public final class Database extends DatabaseStorage {
    public Database(SMPToolkitPlugin plugin) { super(plugin); }
    public record Stats(String name, int kills, int deaths, int chatWins, long playtimeMs, long firstJoin, long lastJoin) {
        public double kd() { return deaths == 0 ? kills : (double) kills / deaths; }
    }
    public record LeaderboardEntry(UUID uuid, String name, int kills, int deaths, int chatWins, long playtimeMs) {
        public double kd() { return deaths == 0 ? kills : (double) kills / deaths; }
    }
    public record Punishment(int id, UUID uuid, String type, String reason, String staffName, long createdAt, Long expiresAt, boolean active) {}
    public record Note(int id, String staffName, String note, long createdAt) {}
    public record AltMatch(UUID uuid, String name, long lastSeen, int matches) {}
    public record ReportRecord(int id, String reporterName, String targetName, String reason, String status, String staffName, long createdAt, String result) {}
    public record PlayerLookup(UUID uuid, String name, long firstJoin, long lastJoin, long playtimeMs) {}
}
