package io.github.xtx.smptoolkit.platform;

import io.github.xtx.smptoolkit.SMPToolkitPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.util.Locale;

public final class ModerationBridgeService implements Listener {
    private final SMPToolkitPlugin plugin;
    private final ModerationGuiService gui;
    public ModerationBridgeService(SMPToolkitPlugin plugin,ModerationGuiService gui){this.plugin=plugin;this.gui=gui;}

    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void onGuiCommands(PlayerCommandPreprocessEvent event){
        String[] parts=event.getMessage().substring(1).trim().split("\\s+");if(parts.length==0)return;String cmd=parts[0].toLowerCase(Locale.ROOT);Player player=event.getPlayer();
        if(cmd.equals("reports")&&parts.length==1&&player.hasPermission("smp.moderation.reports")){event.setCancelled(true);gui.openReports(player,0);return;}
        if(cmd.equals("warnings")&&parts.length>=2&&player.hasPermission("smp.moderation.warnings")){event.setCancelled(true);String name=parts[1];Player target=Bukkit.getPlayerExact(name);if(target!=null){gui.openWarnings(player,target.getUniqueId(),target.getName());return;}plugin.database().findPlayerByName(name).thenAccept(opt->plugin.sync(()->{if(opt.isPresent())gui.openWarnings(player,opt.get().uuid(),opt.get().name());else plugin.messages().send(player,"general.player-not-found");}));}
    }

    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void onWarn(PlayerCommandPreprocessEvent event){
        String[] parts=event.getMessage().substring(1).trim().split("\\s+");if(parts.length<2||!parts[0].equalsIgnoreCase("warn")||!event.getPlayer().hasPermission("smp.moderation.warn"))return;String name=parts[1];Bukkit.getScheduler().runTaskLater(plugin,()->{Player target=Bukkit.getPlayerExact(name);if(target!=null)gui.afterWarning(target.getUniqueId(),target.getName(),-1);else plugin.database().findPlayerByName(name).thenAccept(opt->opt.ifPresent(p->gui.afterWarning(p.uuid(),p.name(),-1)));},2L);
    }
}
