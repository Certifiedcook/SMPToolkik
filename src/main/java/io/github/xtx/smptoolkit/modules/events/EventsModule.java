package io.github.xtx.smptoolkit.modules.events;

import io.github.xtx.smptoolkit.SMPToolkitPlugin;
import io.github.xtx.smptoolkit.core.Module;
import io.github.xtx.smptoolkit.core.Text;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerExpChangeEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.Locale;

public final class EventsModule implements Module, Listener {
    private final SMPToolkitPlugin plugin;
    private volatile EventType active = EventType.NONE;
    private volatile long endsAt;
    private BukkitTask endTask;
    private BukkitTask marathonTask;

    public EventsModule(SMPToolkitPlugin plugin) { this.plugin = plugin; }
    @Override public String name() { return "events"; }
    @Override public void enable() { Bukkit.getPluginManager().registerEvents(this, plugin); }
    @Override public void disable() { stop(false); }

    @EventHandler(ignoreCancelled = true)
    public void onXp(PlayerExpChangeEvent event) {
        if (active == EventType.DOUBLE_XP && event.getAmount() > 0) event.setAmount(event.getAmount() * 2);
    }

    public boolean handle(CommandSender sender, String label, String[] args) {
        if (!label.equalsIgnoreCase("event")) return false;
        if (args.length == 0 || args[0].equalsIgnoreCase("list") || args[0].equalsIgnoreCase("status")) {
            if (active == EventType.NONE) sender.sendMessage(Text.mm("<gray>No server event is active.</gray>"));
            else sender.sendMessage(Text.mm("<gold>Active event:</gold> <yellow>"+active.display+"</yellow> <gray>for "+Text.formatDuration(Math.max(0,endsAt-System.currentTimeMillis()))+" more.</gray>"));
            sender.sendMessage(Text.mm("<gray>Available:</gray> <white>doublexp, locator, chatgames</white>"));
            return true;
        }
        if (!sender.hasPermission("smp.events.admin")) { sender.sendMessage(Text.mm("<red>No permission.</red>")); return true; }
        if (args[0].equalsIgnoreCase("stop")) { stop(true); return true; }
        if (!args[0].equalsIgnoreCase("start") || args.length < 2) { sender.sendMessage(Text.mm("<red>Usage: /event start <doublexp|locator|chatgames> [duration]</red>")); return true; }
        EventType type=EventType.from(args[1]);
        if(type==EventType.NONE){sender.sendMessage(Text.mm("<red>Unknown event type.</red>"));return true;}
        long duration=args.length>2?Text.parseDurationMillis(args[2]):30*60_000L;
        if(duration<=0){sender.sendMessage(Text.mm("<red>Invalid duration. Examples: 30m, 2h.</red>"));return true;}
        start(type,duration);return true;
    }

    private void start(EventType type,long duration){
        stop(false);active=type;endsAt=System.currentTimeMillis()+duration;
        if(type==EventType.LOCATOR&&plugin.locator()!=null)plugin.locator().setEventOverride(true);
        if(type==EventType.CHAT_GAMES&&plugin.chatGames()!=null){plugin.chatGames().startRandom(null);marathonTask=Bukkit.getScheduler().runTaskTimer(plugin,()->plugin.chatGames().startRandom(null),2*60*20L,2*60*20L);}
        endTask=Bukkit.getScheduler().runTaskLater(plugin,()->stop(true),Math.max(1,duration/50));
        Bukkit.broadcast(Text.mm("<gold><bold>SERVER EVENT:</bold></gold> <yellow>"+type.display+"</yellow> <gray>has started for "+Text.formatDuration(duration)+".</gray>"));
    }

    public void stop(boolean announce){
        EventType old=active;
        if(endTask!=null){endTask.cancel();endTask=null;}
        if(marathonTask!=null){marathonTask.cancel();marathonTask=null;}
        if(old==EventType.LOCATOR&&plugin.locator()!=null)plugin.locator().setEventOverride(false);
        active=EventType.NONE;endsAt=0;
        if(announce&&old!=EventType.NONE)Bukkit.broadcast(Text.mm("<gray>The <yellow>"+old.display+"</yellow> event has ended.</gray>"));
    }

    private enum EventType {
        NONE("None"), DOUBLE_XP("Double XP"), LOCATOR("Locator Hour"), CHAT_GAMES("Chat Games Marathon");
        final String display;EventType(String display){this.display=display;}
        static EventType from(String raw){return switch(raw.toLowerCase(Locale.ROOT)){case "doublexp","xp","2xp"->DOUBLE_XP;case "locator","locatorhour"->LOCATOR;case "chatgames","chat"->CHAT_GAMES;default->NONE;};}
    }
}
