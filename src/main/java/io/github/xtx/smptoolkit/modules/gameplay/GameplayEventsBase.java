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

class GameplayEventsBase extends GameplayState {
    GameplayEventsBase(SMPToolkitPlugin plugin) { super(plugin); }

public void onPvp(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        Player attacker = playerDamager(event.getDamager());
        if (attacker == null || attacker.equals(victim)) return;
        if (isProtected(attacker) || isProtected(victim)) {
            event.setCancelled(true);
            attacker.sendMessage(Text.mm("<yellow>PvP is blocked while either player has new-player protection.</yellow>"));
            return;
        }
        int seconds = Math.max(1, plugin.getConfig().getInt("gameplay.combat-tag.seconds",15));
        long until = System.currentTimeMillis()+seconds*1000L;
        combatUntil.put(attacker.getUniqueId(),until);
        combatUntil.put(victim.getUniqueId(),until);
    }

public void onCommandWhileCombat(PlayerCommandPreprocessEvent event) {
        if (combatSeconds(event.getPlayer().getUniqueId()) <= 0) return;
        String command = event.getMessage().substring(1).split("\\s+",2)[0].toLowerCase(Locale.ROOT);
        List<String> blocked = plugin.getConfig().getStringList("gameplay.combat-tag.blocked-commands");
        if (blocked.stream().map(s->s.toLowerCase(Locale.ROOT)).anyMatch(command::equals) && !event.getPlayer().hasPermission("smp.gameplay.combat.bypass")) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(Text.mm("<red>You cannot use that command while combat-tagged. " + combatSeconds(event.getPlayer().getUniqueId()) + "s remaining.</red>"));
        }
    }

public void onQuit(PlayerQuitEvent event) {
        UUID id=event.getPlayer().getUniqueId();
        if (combatSeconds(id)>0 && plugin.getConfig().getBoolean("gameplay.combat-tag.logout-kill",true) && !event.getPlayer().hasPermission("smp.gameplay.combat.bypass")) {
            event.getPlayer().setHealth(0.0);
            Bukkit.broadcast(Text.mm("<red>"+Text.escapeMini(event.getPlayer().getName())+" logged out during combat.</red>"));
        }
        combatUntil.remove(id);
    }

public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Location loc = victim.getLocation();
        victim.sendMessage(Text.mm("<gray>You died at <yellow>"+loc.getBlockX()+", "+loc.getBlockY()+", "+loc.getBlockZ()+"</yellow> in <white>"+Text.escapeMini(loc.getWorld().getName())+"</white>.</gray>"));
        if (victim.getUniqueId().equals(manhuntRunner)) {
            Bukkit.broadcast(Text.mm("<red><bold>MANHUNT:</bold></red> <gray>The runner died. Hunters win.</gray>"));
            stopManhunt(false);
        }
    }
}
