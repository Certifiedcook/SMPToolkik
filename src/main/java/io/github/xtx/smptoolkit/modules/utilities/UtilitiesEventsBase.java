package io.github.xtx.smptoolkit.modules.utilities;

import io.github.xtx.smptoolkit.SMPToolkitPlugin;
import io.github.xtx.smptoolkit.core.Locations;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import java.util.UUID;

class UtilitiesEventsBase extends UtilitiesState {
    UtilitiesEventsBase(SMPToolkitPlugin plugin) { super(plugin); }
    public void onJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        loadIgnores(p.getUniqueId());
        if (!p.hasPlayedBefore() && plugin.getConfig().getBoolean("utilities.spawn-on-first-join", false)) {
            Location spawn = configuredSpawn();
            if (spawn != null) plugin.sync(() -> teleportInternal(p, spawn));
        }
    }
    public void onQuit(PlayerQuitEvent event) { afk.remove(event.getPlayer().getUniqueId()); }
    public void onTeleport(PlayerTeleportEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        if (internalTeleport.remove(id)) return;
        if (event.getFrom().getWorld() != null) plugin.database().setLocation(id, "back", Locations.encode(event.getFrom()));
    }
}
