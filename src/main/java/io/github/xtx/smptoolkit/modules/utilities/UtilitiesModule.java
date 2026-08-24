package io.github.xtx.smptoolkit.modules.utilities;

import io.github.xtx.smptoolkit.SMPToolkitPlugin;
import io.github.xtx.smptoolkit.core.Module;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

public final class UtilitiesModule extends UtilitiesCommandsBase implements Module, Listener {
    public UtilitiesModule(SMPToolkitPlugin plugin) { super(plugin); }
    @Override public void enable() { super.enable(); Bukkit.getPluginManager().registerEvents(this, plugin); }
    @Override @EventHandler public void onJoin(PlayerJoinEvent e) { super.onJoin(e); }
    @Override @EventHandler public void onQuit(PlayerQuitEvent e) { super.onQuit(e); }
    @Override @EventHandler public void onTeleport(PlayerTeleportEvent e) { super.onTeleport(e); }
}
