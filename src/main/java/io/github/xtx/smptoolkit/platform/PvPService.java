package io.github.xtx.smptoolkit.platform;

import io.github.xtx.smptoolkit.SMPToolkitPlugin;
import io.github.xtx.smptoolkit.core.Text;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.projectiles.ProjectileSource;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class PvPService implements Listener {
    private final SMPToolkitPlugin plugin;
    private final PlatformDatabase db;
    private final Map<UUID,Map<UUID,Hit>> damage=new ConcurrentHashMap<>();
    private final Map<UUID,UUID> lastKiller=new ConcurrentHashMap<>();
    private final Map<UUID,Integer> deathStreak=new ConcurrentHashMap<>();
    public PvPService(SMPToolkitPlugin plugin,PlatformDatabase db){this.plugin=plugin;this.db=db;}

    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void onDamage(EntityDamageByEntityEvent event){
        if(!plugin.featureEnabled("pvp-expansion")||!(event.getEntity() instanceof Player victim))return;
        Player attacker=playerDamager(event.getDamager());if(attacker==null||attacker.equals(victim))return;
        damage.computeIfAbsent(victim.getUniqueId(),k->new ConcurrentHashMap<>()).merge(attacker.getUniqueId(),new Hit(event.getFinalDamage(),System.currentTimeMillis()),(a,b)->new Hit(a.damage+b.damage,b.time));
    }

    @EventHandler(priority=EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event){
        if(!plugin.featureEnabled("pvp-expansion"))return;
        Player victim=event.getEntity(),killer=victim.getKiller();long now=System.currentTimeMillis();long window=plugin.getConfig().getLong("platform.pvp.assist-window-seconds",15)*1000L;double min=plugin.getConfig().getDouble("platform.pvp.assist-min-damage",2.0);
        Map<UUID,Hit> hits=damage.remove(victim.getUniqueId());List<String> assists=new ArrayList<>();
        if(hits!=null)for(var entry:hits.entrySet()){if(killer!=null&&entry.getKey().equals(killer.getUniqueId()))continue;if(now-entry.getValue().time>window||entry.getValue().damage<min)continue;Player assist=Bukkit.getPlayer(entry.getKey());if(assist!=null){assists.add(assist.getName());for(String command:plugin.getConfig().getStringList("platform.pvp.assist-reward-commands"))Bukkit.dispatchCommand(Bukkit.getConsoleSender(),command.replace("%player%",assist.getName()).replace("%victim%",victim.getName()));}}
        int deaths=deathStreak.merge(victim.getUniqueId(),1,Integer::sum);
        if(killer==null){db.logCombat(null,"Environment",victim.getUniqueId(),victim.getName(),"ENVIRONMENT",0,String.join(",",assists),false);return;}
        boolean revenge=victim.getUniqueId().equals(lastKiller.get(killer.getUniqueId()));
        lastKiller.put(victim.getUniqueId(),killer.getUniqueId());deathStreak.remove(killer.getUniqueId());
        double distance=killer.getLocation().distance(victim.getLocation());String weapon=killer.getInventory().getItemInMainHand().getType().name();
        db.logCombat(killer.getUniqueId(),killer.getName(),victim.getUniqueId(),victim.getName(),weapon,distance,String.join(",",assists),revenge);
        if(plugin.getConfig().getBoolean("platform.pvp.custom-killfeed",true)){
            String format=plugin.getConfig().getString("platform.pvp.killfeed-format","<red>%victim%</red> <gray>was killed by</gray> <green>%killer%</green> <dark_gray>[%weapon% • %distance%m%assists%%revenge%]</dark_gray>");
            String assistText=assists.isEmpty()?"":" • assists: "+String.join(", ",assists);String revengeText=revenge?" • REVENGE":"";
            format=format.replace("%victim%",Text.escapeMini(victim.getName())).replace("%killer%",Text.escapeMini(killer.getName())).replace("%weapon%",Text.escapeMini(weapon)).replace("%distance%",String.format(Locale.ROOT,"%.1f",distance)).replace("%assists%",Text.escapeMini(assistText)).replace("%revenge%",Text.escapeMini(revengeText)).replace("%deathstreak%",Integer.toString(deaths));
            event.deathMessage(Text.mm(format));
        }
    }

    public boolean handle(CommandSender sender,String[] args){
        Player target=args.length>0?Bukkit.getPlayerExact(args[0]):sender instanceof Player p?p:null;if(target==null){sender.sendMessage(Text.mm("<red>Player must be online.</red>"));return true;}
        db.combatStats(target.getUniqueId()).thenAccept(stats->plugin.sync(()->sender.sendMessage(Text.mm("<gold><bold>PvP — "+Text.escapeMini(target.getName())+"</bold></gold>\n<gray>Kills:</gray> <white>"+stats.kills()+"</white> <gray>Deaths:</gray> <white>"+stats.deaths()+"</white> <gray>Revenge kills:</gray> <white>"+stats.revengeKills()+"</white> <gray>Longest kill:</gray> <white>"+String.format(Locale.ROOT,"%.1fm",stats.longestKillDistance())+"</white>"))));return true;
    }

    private Player playerDamager(Entity entity){if(entity instanceof Player p)return p;if(entity instanceof Projectile projectile){ProjectileSource source=projectile.getShooter();if(source instanceof Player p)return p;}return null;}
    private record Hit(double damage,long time){}
}
