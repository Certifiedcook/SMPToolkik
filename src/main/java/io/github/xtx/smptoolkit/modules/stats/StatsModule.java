package io.github.xtx.smptoolkit.modules.stats;

import io.github.xtx.smptoolkit.SMPToolkitPlugin;
import io.github.xtx.smptoolkit.core.Module;
import io.github.xtx.smptoolkit.core.Text;
import io.github.xtx.smptoolkit.data.Database;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class StatsModule implements Module, Listener {
    private final SMPToolkitPlugin plugin;
    private final Map<UUID, Long> sessions = new ConcurrentHashMap<>();
    private final Map<UUID, Database.Stats> cache = new ConcurrentHashMap<>();

    public StatsModule(SMPToolkitPlugin plugin) { this.plugin = plugin; }
    @Override public String name() { return "statistics"; }

    @Override
    public void enable() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        long now = System.currentTimeMillis();
        for (Player p : Bukkit.getOnlinePlayers()) {
            sessions.put(p.getUniqueId(), now);
            load(p.getUniqueId());
        }
    }

    @Override
    public void disable() {
        long now = System.currentTimeMillis();
        sessions.forEach((uuid, start) -> plugin.database().addPlaytime(uuid, now - start));
        sessions.clear();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        sessions.put(p.getUniqueId(), System.currentTimeMillis());
        plugin.database().upsertJoin(p.getUniqueId(), p.getName()).thenRun(() -> load(p.getUniqueId()));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player p = event.getPlayer();
        Long start = sessions.remove(p.getUniqueId());
        if (start != null) plugin.database().addPlaytime(p.getUniqueId(), System.currentTimeMillis() - start);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        increment(victim.getUniqueId(), "deaths");
        Player killer = victim.getKiller();
        if (killer != null && !killer.getUniqueId().equals(victim.getUniqueId())) {
            increment(killer.getUniqueId(), "kills");
            if (plugin.gameplay() != null) plugin.gameplay().handlePvPKill(killer, victim);
        }
        if (plugin.utilities() != null) plugin.utilities().rememberDeath(victim);
    }

    public void incrementChatWin(UUID uuid) { increment(uuid, "chat_wins"); }

    private void increment(UUID uuid, String column) {
        plugin.database().incrementStat(uuid, column);
        cache.compute(uuid, (id, old) -> {
            Database.Stats s = old == null ? new Database.Stats("Unknown",0,0,0,0,0,0) : old;
            return switch (column) {
                case "kills" -> new Database.Stats(s.name(), s.kills()+1, s.deaths(), s.chatWins(), s.playtimeMs(), s.firstJoin(), s.lastJoin());
                case "deaths" -> new Database.Stats(s.name(), s.kills(), s.deaths()+1, s.chatWins(), s.playtimeMs(), s.firstJoin(), s.lastJoin());
                case "chat_wins" -> new Database.Stats(s.name(), s.kills(), s.deaths(), s.chatWins()+1, s.playtimeMs(), s.firstJoin(), s.lastJoin());
                default -> s;
            };
        });
    }

    public Database.Stats cached(UUID uuid) {
        Database.Stats s = cache.get(uuid);
        if (s == null) {
            load(uuid);
            return new Database.Stats("Unknown",0,0,0,0,0,0);
        }
        Long start = sessions.get(uuid);
        if (start == null) return s;
        return new Database.Stats(s.name(), s.kills(), s.deaths(), s.chatWins(), s.playtimeMs() + Math.max(0, System.currentTimeMillis()-start), s.firstJoin(), s.lastJoin());
    }

    private void load(UUID uuid) {
        plugin.database().stats(uuid).thenAccept(s -> cache.put(uuid, s));
    }

    public boolean handle(CommandSender sender, String label, String[] args) {
        switch (label.toLowerCase(Locale.ROOT)) {
            case "stats" -> {
                if (args.length == 0) {
                    if (!(sender instanceof Player p)) { sender.sendMessage("Use /stats <player>"); return true; }
                    showStats(sender, p.getUniqueId(), p.getName());
                } else resolvePlayer(args[0], lookup -> showStats(sender, lookup.uuid(), lookup.name()), sender);
                return true;
            }
            case "topkills" -> { showTop(sender, "kills", "Kills"); return true; }
            case "topdeaths" -> { showTop(sender, "deaths", "Deaths"); return true; }
            case "topkd" -> { showTop(sender, "kd", "K/D"); return true; }
            case "topplaytime" -> { showTop(sender, "playtime", "Playtime"); return true; }
        }
        return false;
    }

    private void showStats(CommandSender sender, UUID uuid, String name) {
        plugin.database().stats(uuid).thenAccept(s -> plugin.sync(() -> {
            long session = sessions.containsKey(uuid) ? System.currentTimeMillis() - sessions.get(uuid) : 0;
            sender.sendMessage(Text.mm("<gold><bold>Profile — " + Text.escapeMini(name) + "</bold></gold>"));
            sender.sendMessage(Text.mm("<gray>Kills:</gray> <white>" + s.kills() + "</white>  <gray>Deaths:</gray> <white>" + s.deaths() + "</white>  <gray>K/D:</gray> <white>" + String.format("%.2f", s.kd()) + "</white>"));
            sender.sendMessage(Text.mm("<gray>Playtime:</gray> <white>" + Text.formatDuration(s.playtimeMs()+session) + "</white>  <gray>Chat wins:</gray> <white>" + s.chatWins() + "</white>"));
        }));
    }

    private void showTop(CommandSender sender, String metric, String title) {
        plugin.database().leaderboard(metric, 10).thenAccept(rows -> plugin.sync(() -> {
            sender.sendMessage(Text.mm("<gold><bold>Top " + title + "</bold></gold>"));
            int i = 1;
            for (Database.LeaderboardEntry row : rows) {
                String value = switch (metric) {
                    case "kills" -> Integer.toString(row.kills());
                    case "deaths" -> Integer.toString(row.deaths());
                    case "kd" -> String.format("%.2f", row.kd());
                    case "playtime" -> Text.formatDuration(row.playtimeMs());
                    default -> Integer.toString(row.chatWins());
                };
                sender.sendMessage(Text.mm("<gray>" + (i++) + ".</gray> <white>" + Text.escapeMini(row.name()) + "</white> <dark_gray>—</dark_gray> <yellow>" + value + "</yellow>"));
            }
        }));
    }

    private void resolvePlayer(String name, java.util.function.Consumer<Database.PlayerLookup> found, CommandSender sender) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            found.accept(new Database.PlayerLookup(online.getUniqueId(), online.getName(), 0,0,0));
            return;
        }
        plugin.database().findPlayerByName(name).thenAccept(opt -> plugin.sync(() -> {
            if (opt.isEmpty()) sender.sendMessage(Text.mm("<red>Unknown player.</red>")); else found.accept(opt.get());
        }));
    }
}
