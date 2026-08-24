package io.github.xtx.smptoolkit.platform;

import io.github.xtx.smptoolkit.SMPToolkitPlugin;
import io.github.xtx.smptoolkit.core.Text;
import org.bukkit.GameRules;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.configuration.ConfigurationSection;

import java.util.*;

public final class WorldProfileService implements Listener {
    private final SMPToolkitPlugin plugin;
    public WorldProfileService(SMPToolkitPlugin plugin){this.plugin=plugin;}

    public boolean handle(CommandSender sender,String[] args){
        World world=args.length>0?plugin.getServer().getWorld(args[0]):sender instanceof Player p?p.getWorld():null;
        if(world==null){sender.sendMessage(Text.mm("<red>World not found.</red>"));return true;}
        sender.sendMessage(Text.mm("<gold><bold>World Profile — "+Text.escapeMini(world.getName())+"</bold></gold>"));
        sender.sendMessage(Text.mm("<gray>PvP:</gray> <yellow>"+enabled(world,"pvp",true)+"</yellow> <gray>Combat tag:</gray> <yellow>"+enabled(world,"combat-tag",true)+"</yellow> <gray>New-player protection:</gray> <yellow>"+enabled(world,"new-player-protection",true)+"</yellow>"));
        sender.sendMessage(Text.mm("<gray>TPA:</gray> <yellow>"+enabled(world,"tpa",true)+"</yellow> <gray>Homes:</gray> <yellow>"+enabled(world,"homes",true)+"</yellow> <gray>RTP:</gray> <yellow>"+enabled(world,"rtp",true)+"</yellow> <gray>Back:</gray> <yellow>"+enabled(world,"back",true)+"</yellow>"));
        return true;
    }

    public boolean combatTagEnabled(World world){return enabled(world,"combat-tag",true);}
    public boolean newPlayerProtectionEnabled(World world){return enabled(world,"new-player-protection",true);}
    public boolean enabled(World world,String key,boolean fallback){
        ConfigurationSection worlds=plugin.getConfig().getConfigurationSection("platform.world-profiles.worlds");
        if(worlds!=null){ConfigurationSection exact=worlds.getConfigurationSection(world.getName());if(exact!=null&&exact.contains(key))return exact.getBoolean(key);}
        return plugin.getConfig().getBoolean("platform.world-profiles.default."+key,fallback);
    }

    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void onPvp(EntityDamageByEntityEvent event){
        if(!plugin.featureEnabled("world-profiles")||!(event.getEntity() instanceof Player victim))return;
        Player attacker=event.getDamager() instanceof Player p?p:null;if(attacker==null)return;
        if(!enabled(victim.getWorld(),"pvp",true)){event.setCancelled(true);attacker.sendMessage(Text.mm("<red>PvP is disabled in this world.</red>"));}
    }

    @EventHandler(priority=EventPriority.HIGH,ignoreCancelled=true)
    public void onCommand(PlayerCommandPreprocessEvent event){
        if(!plugin.featureEnabled("world-profiles"))return;
        String command=event.getMessage().substring(1).split("\\s+",2)[0].toLowerCase(Locale.ROOT);
        String key=switch(command){case "tpa","tpaccept","tpdeny","tptoggle"->"tpa";case "home","sethome","delhome","homes"->"homes";case "rtp"->"rtp";case "back"->"back";case "spawn"->"spawn";default->null;};
        if(key!=null&&!enabled(event.getPlayer().getWorld(),key,true)){event.setCancelled(true);event.getPlayer().sendMessage(Text.mm("<red>That feature is disabled in this world.</red>"));}
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event){applyWorldRules(event.getPlayer().getWorld());}

    public void applyWorldRules(World world){
        ConfigurationSection worlds=plugin.getConfig().getConfigurationSection("platform.world-profiles.worlds");if(worlds==null)return;ConfigurationSection exact=worlds.getConfigurationSection(world.getName());if(exact==null)return;
        if(exact.contains("locator-bar"))world.setGameRule(GameRules.LOCATOR_BAR,exact.getBoolean("locator-bar"));
    }
}
