package io.github.xtx.smptoolkit.platform;

import io.github.xtx.smptoolkit.SMPToolkitPlugin;
import io.github.xtx.smptoolkit.core.Text;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.util.UUID;

public final class StaffActivityService implements Listener {
    private final SMPToolkitPlugin plugin;
    private final PlatformDatabase db;
    public StaffActivityService(SMPToolkitPlugin plugin,PlatformDatabase db){this.plugin=plugin;this.db=db;}

    @EventHandler(priority=EventPriority.MONITOR)
    public void onCommand(PlayerCommandPreprocessEvent event){
        if(!plugin.featureEnabled("staff-activity"))return;
        Player player=event.getPlayer();
        if(!player.hasPermission("smp.staff")&&!player.hasPermission("smp.admin")&&!player.hasPermission("smp.moderation.*"))return;
        String raw=event.getMessage();String command=raw.split("\\s+",2)[0].toLowerCase();
        if(plugin.getConfig().getStringList("platform.staff-activity.redact-commands").stream().anyMatch(s->command.equalsIgnoreCase("/"+s.replaceFirst("^/",""))))raw=command+" <redacted>";
        db.logStaff(player.getUniqueId(),player.getName(),"COMMAND",raw+(event.isCancelled()?" [cancelled]":""));
    }

    public boolean handle(CommandSender sender,String[] args){
        Player target=args.length>0?Bukkit.getPlayerExact(args[0]):sender instanceof Player p?p:null;
        if(target==null){sender.sendMessage(Text.mm("<red>Player must be online for /staffstats.</red>"));return true;}
        db.staffStats(target.getUniqueId()).thenAccept(stats->plugin.sync(()->{
            sender.sendMessage(Text.mm("<gold><bold>Staff Stats — "+Text.escapeMini(target.getName())+"</bold></gold>"));
            sender.sendMessage(Text.mm("<gray>Commands:</gray> <white>"+stats.commands()+"</white> <gray>Punishments:</gray> <white>"+stats.punishments()+"</white> <gray>Reports:</gray> <white>"+stats.reports()+"</white> <gray>Cases:</gray> <white>"+stats.cases()+"</white>"));
        }));return true;
    }

    public void log(CommandSender sender,String type,String detail){UUID id=sender instanceof Player p?p.getUniqueId():null;db.logStaff(id,sender.getName(),type,detail);}
}
