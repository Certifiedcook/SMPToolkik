package io.github.xtx.smptoolkit.modules.moderation;

import io.github.xtx.smptoolkit.SMPToolkitPlugin;
import io.github.xtx.smptoolkit.core.Module;
import io.github.xtx.smptoolkit.core.Text;
import io.github.xtx.smptoolkit.data.Database;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Base64;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

public final class AltCheckModule implements Module, Listener {
    private final SMPToolkitPlugin plugin;

    public AltCheckModule(SMPToolkitPlugin plugin) { this.plugin = plugin; }
    @Override public String name() { return "altcheck"; }
    @Override public void enable() { Bukkit.getPluginManager().registerEvents(this, plugin); }
    @Override public void disable() {}

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        String address = event.getAddress() == null ? "unknown" : event.getAddress().getHostAddress();
        plugin.database().recordIp(event.getUniqueId(), event.getName(), hash(address));
    }

    public boolean handle(CommandSender sender, String label, String[] args) {
        if (!label.equalsIgnoreCase("altcheck") && !label.equalsIgnoreCase("alts")) return false;
        if (args.length < 1) {
            sender.sendMessage(Text.mm("<red>Usage: /altcheck <player></red>"));
            return true;
        }
        resolve(args[0]).thenAccept(opt -> {
            if (opt.isEmpty()) {
                plugin.sync(() -> sender.sendMessage(Text.mm("<red>Unknown player.</red>")));
                return;
            }
            Database.PlayerLookup target = opt.get();
            plugin.database().findAlts(target.uuid()).thenAccept(rows -> {
                java.util.Map<UUID, java.util.concurrent.CompletableFuture<Optional<Database.Punishment>>> bans = new java.util.HashMap<>();
                for (Database.AltMatch row : rows) bans.put(row.uuid(), plugin.database().activePunishmentAsync(row.uuid(), "BAN"));
                java.util.concurrent.CompletableFuture.allOf(bans.values().toArray(java.util.concurrent.CompletableFuture[]::new)).thenRun(() -> plugin.sync(() -> {
                    sender.sendMessage(Text.mm("<gold><bold>Alt Check — " + Text.escapeMini(target.name()) + "</bold></gold>"));
                    sender.sendMessage(Text.mm("<dark_gray>Matches are based on salted address hashes; shared networks can cause false positives.</dark_gray>"));
                    if (rows.isEmpty()) {
                        sender.sendMessage(Text.mm("<gray>No matching account history found.</gray>"));
                        return;
                    }
                    long now = System.currentTimeMillis();
                    for (Database.AltMatch row : rows) {
                        long age = Math.max(0, now - row.lastSeen());
                        String confidence = confidence(row.matches(), age);
                        String banned = bans.get(row.uuid()).join().isPresent() ? " <red>[BANNED]</red>" : "";
                        sender.sendMessage(Text.mm("<white>" + Text.escapeMini(row.name()) + "</white> " + confidence + banned +
                                " <dark_gray>matches=" + row.matches() + ", last shared match " + Text.formatDuration(age) + " ago</dark_gray>"));
                    }
                }));
            });
        });
        return true;
    }

    private java.util.concurrent.CompletableFuture<Optional<Database.PlayerLookup>> resolve(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            Database.Stats s = plugin.stats() == null ? new Database.Stats(online.getName(),0,0,0,0,0,0) : plugin.stats().cached(online.getUniqueId());
            return java.util.concurrent.CompletableFuture.completedFuture(Optional.of(
                    new Database.PlayerLookup(online.getUniqueId(), online.getName(), s.firstJoin(), s.lastJoin(), s.playtimeMs())));
        }
        return plugin.database().findPlayerByName(name);
    }

    private String confidence(int matches, long ageMs) {
        if (matches >= 3 && ageMs <= Duration.ofDays(14).toMillis()) return "<red>HIGH</red>";
        if (matches >= 2 || ageMs <= Duration.ofDays(30).toMillis()) return "<yellow>MEDIUM</yellow>";
        return "<gray>LOW</gray>";
    }

    private String hash(String address) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String salt = plugin.getConfig().getString("privacy.ip-hash-salt", "");
            byte[] out = digest.digest((salt + "\u0000" + address).getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(out);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
