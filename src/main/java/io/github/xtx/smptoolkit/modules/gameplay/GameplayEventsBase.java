package io.github.xtx.smptoolkit.modules.gameplay;

import io.github.xtx.smptoolkit.SMPToolkitPlugin;
import io.github.xtx.smptoolkit.core.Text;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.*;

class GameplayEventsBase extends GameplayState {
    GameplayEventsBase(SMPToolkitPlugin plugin) { super(plugin); }

    public void onPvp(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        Player attacker = playerDamager(event.getDamager());
        if (attacker == null || attacker.equals(victim)) return;
        boolean protectionAllowed=plugin.featureEnabled("new-player-protection")&&(plugin.platform()==null||plugin.platform().worlds().newPlayerProtectionEnabled(victim.getWorld()));
        if (protectionAllowed && (isProtected(attacker) || isProtected(victim))) {
            event.setCancelled(true);
            attacker.sendMessage(Text.mm("<yellow>PvP is blocked while either player has new-player protection.</yellow>"));
            return;
        }
        if(!plugin.featureEnabled("combat-tag")||(plugin.platform()!=null&&!plugin.platform().worlds().combatTagEnabled(victim.getWorld())))return;
        int seconds = Math.max(1, plugin.getConfig().getInt("gameplay.combat-tag.seconds",15));
        long until = System.currentTimeMillis()+seconds*1000L;
        combatUntil.put(attacker.getUniqueId(),until);
        combatUntil.put(victim.getUniqueId(),until);
    }

    public void onCommandWhileCombat(PlayerCommandPreprocessEvent event) {
        if(!plugin.featureEnabled("combat-tag")||combatSeconds(event.getPlayer().getUniqueId())<=0)return;
        String command = event.getMessage().substring(1).split("\\s+",2)[0].toLowerCase(Locale.ROOT);
        List<String> blocked = plugin.getConfig().getStringList("gameplay.combat-tag.blocked-commands");
        if (blocked.stream().map(s->s.toLowerCase(Locale.ROOT)).anyMatch(command::equals) && !event.getPlayer().hasPermission("smp.gameplay.combat.bypass")) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(Text.mm("<red>You cannot use that command while combat-tagged. " + combatSeconds(event.getPlayer().getUniqueId()) + "s remaining.</red>"));
        }
    }

    public void onQuit(PlayerQuitEvent event) {
        UUID id=event.getPlayer().getUniqueId();
        if (plugin.featureEnabled("combat-tag")&&combatSeconds(id)>0 && plugin.getConfig().getBoolean("gameplay.combat-tag.logout-kill",true) && !event.getPlayer().hasPermission("smp.gameplay.combat.bypass")) {
            event.getPlayer().setHealth(0.0);
            Bukkit.broadcast(Text.mm("<red>"+Text.escapeMini(event.getPlayer().getName())+" logged out during combat.</red>"));
        }
        combatUntil.remove(id);
    }

    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        if(plugin.getConfig().getBoolean("gameplay.death-coordinates.enabled",true)){
            Location loc = victim.getLocation();
            victim.sendMessage(Text.mm(plugin.getConfig().getString("gameplay.death-coordinates.message","<gray>You died at <yellow>%x%, %y%, %z%</yellow> in <white>%world%</white>.</gray>")
                    .replace("%x%",Integer.toString(loc.getBlockX())).replace("%y%",Integer.toString(loc.getBlockY())).replace("%z%",Integer.toString(loc.getBlockZ())).replace("%world%",Text.escapeMini(loc.getWorld().getName()))));
        }
        if (victim.getUniqueId().equals(manhuntRunner)) {
            Bukkit.broadcast(Text.mm("<red><bold>MANHUNT:</bold></red> <gray>The runner died. Hunters win.</gray>"));
            stopManhunt(false);
        }
    }
}
