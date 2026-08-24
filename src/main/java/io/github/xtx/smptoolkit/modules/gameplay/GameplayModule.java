package io.github.xtx.smptoolkit.modules.gameplay;

import io.github.xtx.smptoolkit.SMPToolkitPlugin;
import io.github.xtx.smptoolkit.core.Module;
import io.github.xtx.smptoolkit.core.Text;
import io.github.xtx.smptoolkit.data.Database;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.scheduler.BukkitTask;

import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public final class GameplayModule extends GameplayCommandsBase implements Module, Listener {
    public GameplayModule(SMPToolkitPlugin plugin) { super(plugin); }
    @Override public void enable() { super.enable(); Bukkit.getPluginManager().registerEvents(this, plugin); }
    @Override @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true) public void onPvp(EntityDamageByEntityEvent e) { super.onPvp(e); }
    @Override @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true) public void onCommandWhileCombat(PlayerCommandPreprocessEvent e) { super.onCommandWhileCombat(e); }
    @Override @EventHandler public void onQuit(PlayerQuitEvent e) { super.onQuit(e); }
    @Override @EventHandler(priority = EventPriority.MONITOR) public void onDeath(PlayerDeathEvent e) { super.onDeath(e); }
}
