package io.github.xtx.smptoolkit.platform;

import io.github.xtx.smptoolkit.SMPToolkitPlugin;
import io.github.xtx.smptoolkit.core.Text;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.scheduler.BukkitTask;

import java.time.*;
import java.time.temporal.WeekFields;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class ProgressionService implements Listener {
    private final SMPToolkitPlugin plugin;
    private final PlatformDatabase db;
    private final Set<String> completionNotices=ConcurrentHashMap.newKeySet();
    private BukkitTask minuteTask;

    public ProgressionService(SMPToolkitPlugin plugin,PlatformDatabase db){this.plugin=plugin;this.db=db;}

    public void enable(){
        if(plugin.featureEnabled("progression"))minuteTask=Bukkit.getScheduler().runTaskTimer(plugin,()->{for(Player p:Bukkit.getOnlinePlayers())increment(p,"play_minutes",1);},1200L,1200L);
    }
    public void disable(){if(minuteTask!=null)minuteTask.cancel();}

    @EventHandler(ignoreCancelled=true) public void onBlockBreak(BlockBreakEvent e){if(plugin.featureEnabled("progression")){increment(e.getPlayer(),"blocks_broken",1);increment(e.getPlayer(),"break_"+e.getBlock().getType().name().toLowerCase(Locale.ROOT),1);}}
    @EventHandler public void onPlayerDeath(PlayerDeathEvent e){Player killer=e.getEntity().getKiller();if(killer!=null&&plugin.featureEnabled("progression"))increment(killer,"player_kills",1);}
    @EventHandler public void onMobDeath(EntityDeathEvent e){if(e.getEntity() instanceof Player)return;Player killer=e.getEntity().getKiller();if(killer!=null&&plugin.featureEnabled("progression")){increment(killer,"mob_kills",1);increment(killer,"kill_"+e.getEntityType().name().toLowerCase(Locale.ROOT),1);}}
    @EventHandler(ignoreCancelled=true) public void onFish(PlayerFishEvent e){if(e.getState()==PlayerFishEvent.State.CAUGHT_FISH&&plugin.featureEnabled("progression"))increment(e.getPlayer(),"fish_caught",1);}

    public void recordChatWin(Player player){if(plugin.featureEnabled("progression"))increment(player,"chat_wins",1);}

    public boolean handle(CommandSender sender,String label,String[] args){
        if(label.equals("quests")){if(!(sender instanceof Player p)){sender.sendMessage("Players only.");return true;}showQuests(p);return true;}
        if(label.equals("quest")){
            if(!(sender instanceof Player p)){sender.sendMessage("Players only.");return true;}
            if(args.length<2||!args[0].equalsIgnoreCase("claim")){p.sendMessage(Text.mm("<yellow>/quest claim <id></yellow>"));return true;}
            claim(p,args[1]);return true;
        }
        if(label.equals("achievements")){
            Player target=args.length>0?Bukkit.getPlayerExact(args[0]):sender instanceof Player p?p:null;
            if(target==null){sender.sendMessage(Text.mm("<red>Player must be online.</red>"));return true;}
            db.achievements(target.getUniqueId()).thenAccept(ids->plugin.sync(()->{
                sender.sendMessage(Text.mm("<gold><bold>Achievements — "+Text.escapeMini(target.getName())+"</bold></gold>"));
                ConfigurationSection defs=plugin.getConfig().getConfigurationSection("platform.progression.achievements");
                if(defs==null||ids.isEmpty()){sender.sendMessage(Text.mm("<gray>None unlocked yet.</gray>"));return;}
                for(String id:ids){ConfigurationSection def=defs.getConfigurationSection(id);String display=def==null?id:def.getString("display",id);sender.sendMessage(Text.mm("<green>✓</green> <white>"+Text.escapeMini(display)+"</white>"));}
            }));return true;
        }
        return false;
    }

    private void increment(Player player,String counter,long amount){
        db.incrementCounter(player.getUniqueId(),counter,amount).thenAccept(total->{checkAchievements(player,counter,total);checkQuests(player,counter,amount);});
    }

    private void checkAchievements(Player player,String counter,long total){
        ConfigurationSection defs=plugin.getConfig().getConfigurationSection("platform.progression.achievements");if(defs==null)return;
        for(String id:defs.getKeys(false)){
            ConfigurationSection def=defs.getConfigurationSection(id);if(def==null||!def.getBoolean("enabled",true)||!counter.equalsIgnoreCase(def.getString("counter","")))continue;
            long target=def.getLong("target",1);if(total<target)continue;
            db.unlockAchievement(player.getUniqueId(),id).thenAccept(unlocked->{if(unlocked)plugin.sync(()->{String display=def.getString("display",id);plugin.messages().send(player,"achievements.unlocked","achievement",display);runRewards(player,def.getStringList("reward-commands"));});});
        }
    }

    private void checkQuests(Player player,String counter,long amount){
        ConfigurationSection defs=plugin.getConfig().getConfigurationSection("platform.progression.quests");if(defs==null)return;
        for(String id:defs.getKeys(false)){
            ConfigurationSection def=defs.getConfigurationSection(id);if(def==null||!def.getBoolean("enabled",true)||!counter.equalsIgnoreCase(def.getString("counter","")))continue;
            String period=periodKey(def.getString("period","DAILY"));
            db.incrementQuest(player.getUniqueId(),id,period,amount).thenAccept(progress->{long target=def.getLong("target",1);String noticeKey=player.getUniqueId()+":"+id+":"+period;if(progress>=target&&completionNotices.add(noticeKey))plugin.sync(()->plugin.messages().send(player,"quests.completed","quest",def.getString("display",id)));});
        }
    }

    private void showQuests(Player player){
        ConfigurationSection defs=plugin.getConfig().getConfigurationSection("platform.progression.quests");
        player.sendMessage(Text.mm("<gold><bold>Challenges</bold></gold>"));
        if(defs==null){player.sendMessage(Text.mm("<gray>No challenges configured.</gray>"));return;}
        for(String id:defs.getKeys(false)){
            ConfigurationSection def=defs.getConfigurationSection(id);if(def==null||!def.getBoolean("enabled",true))continue;
            String period=periodKey(def.getString("period","DAILY"));long target=def.getLong("target",1);String display=def.getString("display",id);
            db.questProgress(player.getUniqueId(),id,period).thenAccept(progress->plugin.sync(()->player.sendMessage(Text.mm("<yellow>"+Text.escapeMini(id)+"</yellow> <white>"+Text.escapeMini(display)+"</white> <gray>"+Math.min(progress.progress(),target)+"/"+target+(progress.claimed()?" [claimed]":progress.progress()>=target?" [ready]":"")+"</gray>"))));
        }
    }

    private void claim(Player player,String id){
        ConfigurationSection def=plugin.getConfig().getConfigurationSection("platform.progression.quests."+id);if(def==null||!def.getBoolean("enabled",true)){player.sendMessage(Text.mm("<red>Unknown quest.</red>"));return;}
        String period=periodKey(def.getString("period","DAILY"));long target=def.getLong("target",1);
        db.questProgress(player.getUniqueId(),id,period).thenAccept(progress->{if(progress.claimed()||progress.progress()<target){plugin.sync(()->player.sendMessage(Text.mm("<red>That quest is not ready to claim.</red>")));return;}db.claimQuest(player.getUniqueId(),id,period).thenAccept(ok->{if(ok)plugin.sync(()->{runRewards(player,def.getStringList("reward-commands"));plugin.messages().send(player,"quests.claimed");});});});
    }

    private String periodKey(String type){
        ZoneId zone;try{zone=ZoneId.of(plugin.getConfig().getString("platform.progression.timezone","Europe/Dublin"));}catch(Exception ex){zone=ZoneId.systemDefault();}
        LocalDate now=LocalDate.now(zone);return switch(type.toUpperCase(Locale.ROOT)){case "WEEKLY"->now.getYear()+"-W"+now.get(WeekFields.ISO.weekOfWeekBasedYear());case "MONTHLY"->now.getYear()+"-M"+now.getMonthValue();case "PERMANENT"->"PERMANENT";default->now.toString();};
    }
    private void runRewards(Player player,List<String> commands){for(String command:commands)Bukkit.dispatchCommand(Bukkit.getConsoleSender(),command.replace("%player%",player.getName()));}
}
