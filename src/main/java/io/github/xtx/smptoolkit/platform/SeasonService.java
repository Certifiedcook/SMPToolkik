package io.github.xtx.smptoolkit.platform;

import io.github.xtx.smptoolkit.SMPToolkitPlugin;
import io.github.xtx.smptoolkit.api.events.SMPSeasonChangeEvent;
import io.github.xtx.smptoolkit.core.Text;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class SeasonService implements Listener {
    private final SMPToolkitPlugin plugin;
    private final PlatformDatabase db;
    private final Map<UUID,Long> sessions=new ConcurrentHashMap<>();
    private volatile String activeId="";
    private volatile String activeDisplay="";
    public SeasonService(SMPToolkitPlugin plugin,PlatformDatabase db){this.plugin=plugin;this.db=db;}

    public void enable(){db.activeSeason().thenAccept(opt->{if(opt.isPresent()){activeId=opt.get().id();activeDisplay=opt.get().displayName();}});long now=System.currentTimeMillis();for(Player p:Bukkit.getOnlinePlayers())sessions.put(p.getUniqueId(),now);}
    public void disable(){flushSessions();}
    public String activeId(){return activeId;}
    public String activeDisplay(){return activeDisplay;}

    @EventHandler public void onJoin(PlayerJoinEvent e){sessions.put(e.getPlayer().getUniqueId(),System.currentTimeMillis());}
    @EventHandler public void onQuit(PlayerQuitEvent e){flush(e.getPlayer());}
    @EventHandler public void onDeath(PlayerDeathEvent e){if(activeId.isBlank()||!plugin.featureEnabled("seasons"))return;Player victim=e.getEntity();db.addSeasonStat(activeId,victim.getUniqueId(),victim.getName(),"deaths",1);Player killer=victim.getKiller();if(killer!=null&&!killer.equals(victim))db.addSeasonStat(activeId,killer.getUniqueId(),killer.getName(),"kills",1);}

    public void recordChatWin(Player player){if(!activeId.isBlank()&&plugin.featureEnabled("seasons"))db.addSeasonStat(activeId,player.getUniqueId(),player.getName(),"chat_wins",1);}

    public boolean handle(CommandSender sender,String[] args){
        if(args.length==0||args[0].equalsIgnoreCase("status")){
            if(activeId.isBlank())sender.sendMessage(Text.mm("<gray>No season is active.</gray>"));else sender.sendMessage(Text.mm("<gold><bold>"+Text.escapeMini(activeDisplay)+"</bold></gold> <gray>("+Text.escapeMini(activeId)+") is active.</gray>"));return true;
        }
        if(args[0].equalsIgnoreCase("top")){
            if(activeId.isBlank()){sender.sendMessage(Text.mm("<red>No active season.</red>"));return true;}String metric=args.length>1?args[1].toLowerCase(Locale.ROOT):"kills";if(!Set.of("kills","deaths","kd","playtime","chat_wins").contains(metric)){sender.sendMessage(Text.mm("<red>Metric: kills, deaths, kd, playtime, chat_wins</red>"));return true;}
            db.seasonTop(activeId,metric,10).thenAccept(rows->plugin.sync(()->{sender.sendMessage(Text.mm("<gold><bold>"+Text.escapeMini(activeDisplay)+" — "+Text.escapeMini(metric)+"</bold></gold>"));int i=1;for(PlatformDatabase.SeasonStat row:rows){String value=switch(metric){case "kills"->Integer.toString(row.kills());case "deaths"->Integer.toString(row.deaths());case "kd"->String.format(Locale.ROOT,"%.2f",row.kd());case "playtime"->Text.formatDuration(row.playtimeMs());default->Integer.toString(row.chatWins());};sender.sendMessage(Text.mm("<gray>"+(i++)+".</gray> <white>"+Text.escapeMini(row.name())+"</white> <yellow>"+value+"</yellow>"));}}));return true;
        }
        if(!sender.hasPermission("smp.seasons.admin")){plugin.messages().send(sender,"general.no-permission");return true;}
        if(args[0].equalsIgnoreCase("start")){
            if(args.length<2){sender.sendMessage(Text.mm("<red>/season start <id> [display name]</red>"));return true;}flushSessions();String id=args[1].toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]","-");String display=args.length>2?Text.join(args,2):args[1];db.startSeason(id,display).thenRun(()->plugin.sync(()->{activeId=id;activeDisplay=display;long now=System.currentTimeMillis();for(Player p:Bukkit.getOnlinePlayers())sessions.put(p.getUniqueId(),now);Bukkit.broadcast(plugin.messages().get(sender,"seasons.started","season",display));Bukkit.getPluginManager().callEvent(new SMPSeasonChangeEvent(id,display,true));}));return true;
        }
        if(args[0].equalsIgnoreCase("end")){
            if(activeId.isBlank()){sender.sendMessage(Text.mm("<gray>No active season.</gray>"));return true;}flushSessions();String oldId=activeId,oldDisplay=activeDisplay;db.endSeason().thenRun(()->plugin.sync(()->{activeId="";activeDisplay="";Bukkit.broadcast(plugin.messages().get(sender,"seasons.ended"));Bukkit.getPluginManager().callEvent(new SMPSeasonChangeEvent(oldId,oldDisplay,false));}));return true;
        }
        sender.sendMessage(Text.mm("<red>/season <status|top|start|end></red>"));return true;
    }

    private void flush(Player player){Long start=sessions.remove(player.getUniqueId());if(start!=null&&!activeId.isBlank())db.addSeasonStat(activeId,player.getUniqueId(),player.getName(),"playtime_ms",Math.max(0,System.currentTimeMillis()-start));}
    private void flushSessions(){for(Player p:Bukkit.getOnlinePlayers())flush(p);sessions.clear();}
}
