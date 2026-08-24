package io.github.xtx.smptoolkit.modules.announcements;

import io.github.xtx.smptoolkit.SMPToolkitPlugin;
import io.github.xtx.smptoolkit.core.Module;
import io.github.xtx.smptoolkit.core.Text;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public final class AnnouncementsModule implements Module, Listener {
    private final SMPToolkitPlugin plugin;
    private BukkitTask task;
    private int index;
    public AnnouncementsModule(SMPToolkitPlugin plugin){this.plugin=plugin;}
    @Override public String name(){return "announcements";}
    @Override public void enable(){
        Bukkit.getPluginManager().registerEvents(this,plugin);
        int minutes=Math.max(1,plugin.getConfig().getInt("announcements.interval-minutes",10));
        task=Bukkit.getScheduler().runTaskTimer(plugin,this::next,minutes*1200L,minutes*1200L);
    }
    @Override public void disable(){if(task!=null)task.cancel();}

    @EventHandler
    public void onJoin(PlayerJoinEvent event){
        if(plugin.getConfig().getBoolean("presence.custom-messages",true) && (plugin.staff()==null||!plugin.staff().isVanished(event.getPlayer().getUniqueId()))) event.joinMessage(Text.mm("<green>+</green> <gray>"+Text.escapeMini(event.getPlayer().getName())+" joined the server.</gray>"));
    }
    @EventHandler
    public void onQuit(PlayerQuitEvent event){
        if(plugin.getConfig().getBoolean("presence.custom-messages",true)&& (plugin.staff()==null||!plugin.staff().isVanished(event.getPlayer().getUniqueId())))event.quitMessage(Text.mm("<red>-</red> <gray>"+Text.escapeMini(event.getPlayer().getName())+" left the server.</gray>"));
    }

    public boolean handle(CommandSender sender,String label,String[] args){
        if(!label.equalsIgnoreCase("announce"))return false;
        if(args.length==0){sender.sendMessage(Text.mm("<red>Usage: /announce <message></red>"));return true;}
        String text=Text.join(args,0);
        Bukkit.broadcast(Text.mm("<gold><bold>ANNOUNCEMENT</bold></gold> <white>"+Text.escapeMini(text)+"</white>"));return true;
    }
    private void next(){
        List<String> messages=plugin.getConfig().getStringList("announcements.messages");if(messages.isEmpty())return;
        int selected=plugin.getConfig().getBoolean("announcements.random-order",true)?ThreadLocalRandom.current().nextInt(messages.size()):Math.floorMod(index++,messages.size());
        Bukkit.broadcast(Text.mm(messages.get(selected)));
    }
}
