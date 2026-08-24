package io.github.xtx.smptoolkit.modules.events;

import io.github.xtx.smptoolkit.SMPToolkitPlugin;
import io.github.xtx.smptoolkit.core.Module;
import io.github.xtx.smptoolkit.core.Text;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerExpChangeEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

public final class EventsModule implements Module, Listener {
    private final SMPToolkitPlugin plugin;
    private volatile String activeId="";
    private volatile String activeDisplay="";
    private volatile double xpMultiplier=1.0;
    private volatile long endsAt;
    private BukkitTask endTask;
    private BukkitTask recurringTask;

    public EventsModule(SMPToolkitPlugin plugin){this.plugin=plugin;}
    @Override public String name(){return "events";}
    @Override public void enable(){Bukkit.getPluginManager().registerEvents(this,plugin);}
    @Override public void disable(){stop(false);}

    @EventHandler(ignoreCancelled=true)
    public void onXp(PlayerExpChangeEvent event){if(!activeId.isBlank()&&xpMultiplier!=1.0&&event.getAmount()>0)event.setAmount((int)Math.max(0,Math.round(event.getAmount()*xpMultiplier)));}

    public boolean handle(CommandSender sender,String label,String[] args){
        if(!label.equalsIgnoreCase("event"))return false;
        if(args.length==0||args[0].equalsIgnoreCase("list")||args[0].equalsIgnoreCase("status")){
            if(activeId.isBlank())sender.sendMessage(Text.mm("<gray>No server event is active.</gray>"));else sender.sendMessage(Text.mm("<gold>Active event:</gold> <yellow>"+Text.escapeMini(activeDisplay)+"</yellow> <gray>for "+Text.formatDuration(Math.max(0,endsAt-System.currentTimeMillis()))+" more.</gray>"));
            ConfigurationSection defs=plugin.getConfig().getConfigurationSection("events.definitions");sender.sendMessage(Text.mm("<gray>Available:</gray> <white>"+Text.escapeMini(defs==null?"none":String.join(", ",defs.getKeys(false)))+"</white>"));return true;
        }
        if(!sender.hasPermission("smp.events.admin")){sender.sendMessage(Text.mm("<red>No permission.</red>"));return true;}
        if(args[0].equalsIgnoreCase("stop")){stop(true);return true;}
        if(!args[0].equalsIgnoreCase("start")||args.length<2){sender.sendMessage(Text.mm("<red>/event start <event> [duration]</red>"));return true;}
        ConfigurationSection def=definition(args[1]);if(def==null||!def.getBoolean("enabled",true)){sender.sendMessage(Text.mm("<red>Unknown or disabled event.</red>"));return true;}
        long duration=args.length>2?Text.parseDurationMillis(args[2]):Text.parseDurationMillis(def.getString("default-duration","30m"));if(duration<=0){sender.sendMessage(Text.mm("<red>Invalid event duration.</red>"));return true;}start(args[1].toLowerCase(Locale.ROOT),def,duration);return true;
    }

    private void start(String id,ConfigurationSection def,long duration){
        stop(false);activeId=id;activeDisplay=def.getString("display",id);endsAt=System.currentTimeMillis()+duration;xpMultiplier=Math.max(0,def.getDouble("xp-multiplier",1.0));
        if(def.getBoolean("locator",false)&&plugin.locator()!=null)plugin.locator().setEventOverride(true);
        int chatSeconds=def.getInt("chat-games-interval-seconds",0);if(chatSeconds>0&&plugin.chatGames()!=null){plugin.chatGames().startRandom(null);recurringTask=Bukkit.getScheduler().runTaskTimer(plugin,()->plugin.chatGames().startRandom(null),chatSeconds*20L,chatSeconds*20L);}
        for(String command:def.getStringList("start-commands"))Bukkit.dispatchCommand(Bukkit.getConsoleSender(),command.replace("%duration%",Long.toString(duration/1000)).replace("%event%",id));
        String message=def.getString("start-message","<gold><bold>EVENT:</bold></gold> <yellow>%display%</yellow> <gray>started for %duration%.</gray>").replace("%display%",Text.escapeMini(activeDisplay)).replace("%duration%",Text.formatDuration(duration));if(!message.isBlank())Bukkit.broadcast(Text.mm(message));
        endTask=Bukkit.getScheduler().runTaskLater(plugin,()->stop(true),Math.max(1,duration/50));
    }

    public void stop(boolean announce){
        if(activeId.isBlank())return;String old=activeId,display=activeDisplay;ConfigurationSection def=definition(old);
        if(endTask!=null){endTask.cancel();endTask=null;}if(recurringTask!=null){recurringTask.cancel();recurringTask=null;}
        if(def!=null&&def.getBoolean("locator",false)&&plugin.locator()!=null)plugin.locator().setEventOverride(false);
        if(def!=null)for(String command:def.getStringList("end-commands"))Bukkit.dispatchCommand(Bukkit.getConsoleSender(),command.replace("%event%",old));
        activeId="";activeDisplay="";endsAt=0;xpMultiplier=1.0;
        if(announce){String message=def==null?"<gray>The event has ended.</gray>":def.getString("end-message","<gray>The <yellow>%display%</yellow> event has ended.</gray>").replace("%display%",Text.escapeMini(display));if(!message.isBlank())Bukkit.broadcast(Text.mm(message));}
    }

    public List<String> eventIds(){ConfigurationSection defs=plugin.getConfig().getConfigurationSection("events.definitions");return defs==null?List.of():defs.getKeys(false).stream().sorted().toList();}
    private ConfigurationSection definition(String id){return plugin.getConfig().getConfigurationSection("events.definitions."+id.toLowerCase(Locale.ROOT));}
}
