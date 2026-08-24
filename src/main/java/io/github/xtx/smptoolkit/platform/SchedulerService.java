package io.github.xtx.smptoolkit.platform;

import io.github.xtx.smptoolkit.SMPToolkitPlugin;
import io.github.xtx.smptoolkit.core.Text;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.scheduler.BukkitTask;

import java.time.*;
import java.util.*;

public final class SchedulerService {
    private final SMPToolkitPlugin plugin;
    private final PlatformDatabase db;
    private BukkitTask task;
    private final Set<String> running=Collections.synchronizedSet(new HashSet<>());
    public SchedulerService(SMPToolkitPlugin plugin,PlatformDatabase db){this.plugin=plugin;this.db=db;}

    public void enable(){if(plugin.featureEnabled("scheduler"))task=Bukkit.getScheduler().runTaskTimer(plugin,this::tick,100L,1200L);}
    public void disable(){if(task!=null)task.cancel();}

    public boolean handle(CommandSender sender,String[] args){
        if(args.length==0||args[0].equalsIgnoreCase("list")){
            ConfigurationSection jobs=plugin.getConfig().getConfigurationSection("platform.scheduler.jobs");sender.sendMessage(Text.mm("<gold><bold>Scheduled Jobs</bold></gold>"));if(jobs==null){sender.sendMessage(Text.mm("<gray>No jobs configured.</gray>"));return true;}for(String id:jobs.getKeys(false)){ConfigurationSection j=jobs.getConfigurationSection(id);sender.sendMessage(Text.mm("<yellow>"+Text.escapeMini(id)+"</yellow> <gray>"+(j!=null&&j.getBoolean("enabled",true)?"enabled":"disabled")+" | "+Text.escapeMini(scheduleDescription(j))+"</gray>"));}return true;
        }
        if(args[0].equalsIgnoreCase("run")){if(args.length<2){sender.sendMessage(Text.mm("<red>/scheduler run <job></red>"));return true;}runJob(args[1],true);sender.sendMessage(Text.mm("<green>Triggered job "+Text.escapeMini(args[1])+".</green>"));return true;}
        if(args[0].equalsIgnoreCase("check")){tick();sender.sendMessage(Text.mm("<green>Scheduler checked.</green>"));return true;}
        sender.sendMessage(Text.mm("<red>/scheduler <list|run|check></red>"));return true;
    }

    private void tick(){
        ConfigurationSection jobs=plugin.getConfig().getConfigurationSection("platform.scheduler.jobs");if(jobs==null)return;
        ZoneId zone=zone();ZonedDateTime now=ZonedDateTime.now(zone).withSecond(0).withNano(0);long minute=now.toInstant().toEpochMilli();
        for(String id:jobs.getKeys(false)){
            ConfigurationSection job=jobs.getConfigurationSection(id);if(job==null||!job.getBoolean("enabled",true)||running.contains(id))continue;
            db.lastScheduleRun(id).thenAccept(last->{boolean due=false;String every=job.getString("every","").trim();String cron=job.getString("cron","").trim();if(!every.isEmpty()){long duration=Text.parseDurationMillis(every);due=duration>0&&System.currentTimeMillis()-last>=duration;}else if(!cron.isEmpty())due=matchesCron(cron,now)&&last<minute;if(due)plugin.sync(()->runJob(id,false));});
        }
    }

    private void runJob(String id,boolean manual){
        ConfigurationSection job=plugin.getConfig().getConfigurationSection("platform.scheduler.jobs."+id);if(job==null){return;}if(!running.add(id))return;
        try{
            String message=job.getString("broadcast","");if(!message.isBlank())Bukkit.broadcast(Text.mm(message));
            for(String command:job.getStringList("commands"))Bukkit.dispatchCommand(Bukkit.getConsoleSender(),command);
            db.markScheduleRun(id,System.currentTimeMillis());
        }finally{running.remove(id);}
    }

    private boolean matchesCron(String expression,ZonedDateTime now){
        String[] p=expression.trim().split("\\s+");if(p.length!=5){plugin.getLogger().warning("Invalid scheduler cron '"+expression+"'. Expected: minute hour day-of-month month day-of-week");return false;}
        return field(p[0],now.getMinute(),0,59)&&field(p[1],now.getHour(),0,23)&&field(p[2],now.getDayOfMonth(),1,31)&&field(p[3],now.getMonthValue(),1,12)&&field(p[4],now.getDayOfWeek().getValue()%7,0,6);
    }
    private boolean field(String raw,int value,int min,int max){if(raw.equals("*"))return true;for(String token:raw.split(",")){if(token.startsWith("*/")){try{int step=Integer.parseInt(token.substring(2));if(step>0&&(value-min)%step==0)return true;}catch(NumberFormatException ignored){}}else if(token.contains("-")){String[] range=token.split("-",2);try{int a=Integer.parseInt(range[0]),b=Integer.parseInt(range[1]);if(value>=a&&value<=b)return true;}catch(NumberFormatException ignored){}}else try{if(Integer.parseInt(token)==value)return true;}catch(NumberFormatException ignored){}}return false;}
    private String scheduleDescription(ConfigurationSection job){if(job==null)return "invalid";if(!job.getString("every","").isBlank())return "every "+job.getString("every");if(!job.getString("cron","").isBlank())return "cron "+job.getString("cron");return "no schedule";}
    private ZoneId zone(){try{return ZoneId.of(plugin.getConfig().getString("platform.scheduler.timezone","Europe/Dublin"));}catch(Exception ex){return ZoneId.systemDefault();}}
}
