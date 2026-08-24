package io.github.xtx.smptoolkit.modules.utilities;

import io.github.xtx.smptoolkit.SMPToolkitPlugin;
import io.github.xtx.smptoolkit.core.Locations;
import io.github.xtx.smptoolkit.core.Module;
import io.github.xtx.smptoolkit.core.Text;
import io.github.xtx.smptoolkit.data.Database;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

class UtilitiesState implements Module {
protected final SMPToolkitPlugin plugin;
protected final Map<UUID, TpaRequest> tpaIncoming = new ConcurrentHashMap<>();
protected final Set<UUID> tpaDisabled = ConcurrentHashMap.newKeySet();
protected final Map<UUID, UUID> lastMessagePartner = new ConcurrentHashMap<>();
protected final Map<UUID, Set<UUID>> ignoreCache = new ConcurrentHashMap<>();
protected final Set<UUID> afk = ConcurrentHashMap.newKeySet();
protected final Set<UUID> internalTeleport = ConcurrentHashMap.newKeySet();

UtilitiesState(SMPToolkitPlugin plugin) { this.plugin = plugin; }
@Override public String name() { return "player-utilities"; }
@Override public void enable() { for (Player p : Bukkit.getOnlinePlayers()) loadIgnores(p.getUniqueId()); }
@Override public void disable() {}

protected void loadIgnores(UUID uuid) {
    plugin.database().ignoredTargets(uuid).thenAccept(set -> {
        Set<UUID> copy = ConcurrentHashMap.newKeySet();
        copy.addAll(set);
        ignoreCache.put(uuid, copy);
    });
}
public boolean isIgnoring(UUID owner, UUID target) { return ignoreCache.getOrDefault(owner, Set.of()).contains(target); }
public void rememberDeath(Player player) { plugin.database().setLocation(player.getUniqueId(), "back", Locations.encode(player.getLocation())); }
protected Location configuredSpawn() { return Locations.decode(plugin.getConfig().getString("utilities.spawn", "")); }
protected void teleportInternal(Player p, Location loc) {
    if (loc == null) return;
    plugin.database().setLocation(p.getUniqueId(), "back", Locations.encode(p.getLocation()));
    internalTeleport.add(p.getUniqueId());
    p.teleportAsync(loc);
}
protected record TpaRequest(UUID requester, long expiresAt) {}
}
