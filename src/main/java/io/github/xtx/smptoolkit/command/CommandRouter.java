package io.github.xtx.smptoolkit.command;

import io.github.xtx.smptoolkit.SMPToolkitPlugin;
import io.github.xtx.smptoolkit.core.Text;
import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public final class CommandRouter implements CommandExecutor, TabCompleter, Listener {
    private static final List<String> COMMANDS = List.of(
            "smp","staff","staffchat","staffgui","freeze","unfreeze","vanish","invsee","endersee","heal","feed","fly","god","speed","tpo","tpohere","playerinfo","maintenance","restart",
            "kick","warn","warnings","mute","tempmute","unmute","ban","tempban","unban","history","note","report","reports","altcheck",
            "chat","channel","ignore","msg","reply","chatgames","stats","topkills","topdeaths","topkd","topplaytime","locator",
            "tpa","tpaccept","tpdeny","tptoggle","sethome","home","delhome","homes","setspawn","spawn","back","seen","realname","afk","rtp",
            "voteskipnight","streak","profile","coinflip","manhunt","signup","event","announce","discord","rules","smphelp","help"
    );

    private final SMPToolkitPlugin plugin;

    public CommandRouter(SMPToolkitPlugin plugin){this.plugin=plugin;}

    public void register(){
        for(String name:COMMANDS){
            PluginCommand command=plugin.getCommand(name);
            if(command!=null){
                command.setExecutor(this);
                command.setTabCompleter(this);
            }
        }
        Bukkit.getPluginManager().registerEvents(this,plugin);
    }

    /**
     * Bukkit already owns /help, so plugin.yml alone cannot be relied on to replace it.
     * Intercept the plain player /help command so the result is deterministic.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onHelpCommand(PlayerCommandPreprocessEvent event){
        String message=event.getMessage().trim();
        if(message.length()<2)return;
        String command=message.substring(1).split("\\s+",2)[0];
        if(!command.equalsIgnoreCase("help"))return;
        event.setCancelled(true);
        showHelp(event.getPlayer());
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender,@NotNull Command command,@NotNull String label,@NotNull String[] args){
        String l=label.toLowerCase(Locale.ROOT);
        if(l.equals("help")||l.equals("smphelp"))return showHelp(sender);
        if(l.equals("smp"))return smp(sender,args);
        if(plugin.isModuleEnabled("staff")&&plugin.staff().handle(sender,l,args))return true;
        if(plugin.isModuleEnabled("moderation")&&plugin.moderation().handle(sender,l,args))return true;
        if(plugin.isModuleEnabled("altcheck")&&plugin.altCheck().handle(sender,l,args))return true;
        if(plugin.isModuleEnabled("chat-control")&&plugin.chat().handle(sender,l,args))return true;
        if(plugin.isModuleEnabled("chat-games")&&plugin.chatGames().handle(sender,l,args))return true;
        if(plugin.isModuleEnabled("statistics")&&plugin.stats().handle(sender,l,args))return true;
        if(plugin.isModuleEnabled("locator")&&plugin.locator().handle(sender,l,args))return true;
        if(plugin.isModuleEnabled("player-utilities")&&plugin.utilities().handle(sender,l,args))return true;
        if(plugin.isModuleEnabled("gameplay")&&plugin.gameplay().handle(sender,l,args))return true;
        if(plugin.isModuleEnabled("events")&&plugin.events().handle(sender,l,args))return true;
        if(plugin.isModuleEnabled("announcements")&&plugin.announcements().handle(sender,l,args))return true;
        sender.sendMessage(Text.mm("<red>That feature is unavailable or its module is disabled.</red>"));
        return true;
    }

    private boolean showHelp(CommandSender sender){
        boolean operator=!(sender instanceof Player)||sender.isOp();
        sender.sendMessage(Text.mm("<gold><bold>SMPToolkit Help</bold></gold> <gray>v"+Text.escapeMini(plugin.getPluginMeta().getVersion())+"</gray>"));

        if(!operator){
            sender.sendMessage(Text.mm("<yellow>Travel:</yellow> <white>/tpa /tpaccept /tpdeny /tptoggle /sethome /home /delhome /homes /spawn /back /rtp</white>"));
            sender.sendMessage(Text.mm("<yellow>Chat:</yellow> <white>/channel /ignore /msg /reply</white>"));
            sender.sendMessage(Text.mm("<yellow>Stats:</yellow> <white>/stats /topkills /topdeaths /topkd /topplaytime /profile /streak</white>"));
            sender.sendMessage(Text.mm("<yellow>SMP:</yellow> <white>/voteskipnight /coinflip /chatgames /locator /signup</white>"));
            sender.sendMessage(Text.mm("<yellow>Player:</yellow> <white>/report /seen /realname /afk /rules /discord</white>"));
            sender.sendMessage(Text.mm("<gray>Only player-facing SMP commands are shown here.</gray>"));
            return true;
        }

        sender.sendMessage(Text.mm("<aqua>Core:</aqua> <white>/smp /help /smphelp</white>"));
        sender.sendMessage(Text.mm("<aqua>Staff:</aqua> <white>/staff /staffchat /staffgui /freeze /unfreeze /vanish /invsee /endersee /heal /feed /fly /god /speed /tpo /tpohere /playerinfo /maintenance /restart</white>"));
        sender.sendMessage(Text.mm("<aqua>Moderation:</aqua> <white>/kick /warn /warnings /mute /tempmute /unmute /ban /tempban /unban /history /note /report /reports /altcheck</white>"));
        sender.sendMessage(Text.mm("<aqua>Chat:</aqua> <white>/chat /channel /ignore /msg /reply</white>"));
        sender.sendMessage(Text.mm("<aqua>Games & Stats:</aqua> <white>/chatgames /stats /topkills /topdeaths /topkd /topplaytime /locator</white>"));
        sender.sendMessage(Text.mm("<aqua>Utilities:</aqua> <white>/tpa /tpaccept /tpdeny /tptoggle /sethome /home /delhome /homes /setspawn /spawn /back /seen /realname /afk /rtp</white>"));
        sender.sendMessage(Text.mm("<aqua>Gameplay:</aqua> <white>/voteskipnight /streak /profile /coinflip /manhunt /signup</white>"));
        sender.sendMessage(Text.mm("<aqua>Events & Info:</aqua> <white>/event /announce /discord /rules</white>"));
        return true;
    }

    private boolean smp(CommandSender sender,String[] args){
        if(args.length==0||args[0].equalsIgnoreCase("info")){
            sender.sendMessage(Text.mm("<gold><bold>SMPToolkit "+Text.escapeMini(plugin.getPluginMeta().getVersion())+"</bold></gold> <gray>Paper 26.2 modular SMP suite.</gray>"));
            sender.sendMessage(Text.mm("<gray>Enabled modules:</gray> <white>"+Text.escapeMini(String.join(", ",plugin.enabledModuleNames()))+"</white>"));
            return true;
        }
        switch(args[0].toLowerCase(Locale.ROOT)){
            case "modules"->sender.sendMessage(Text.mm("<gray>Enabled:</gray> <white>"+Text.escapeMini(String.join(", ",plugin.enabledModuleNames()))+"</white>"));
            case "reload"->{plugin.reloadConfig();sender.sendMessage(Text.mm("<green>Configuration reloaded.</green> <gray>Module enable/disable changes require a server restart.</gray>"));}
            case "debug"->{sender.sendMessage(Text.mm("<gray>Online:</gray> <white>"+Bukkit.getOnlinePlayers().size()+"</white> <gray>Locator:</gray> <white>"+plugin.locator().isActive()+"</white>"));sender.sendMessage(Text.mm("<gray>LuckPerms:</gray> <white>"+plugin.integrations().luckPerms().available()+"</white>"));}
            default->sender.sendMessage(Text.mm("<red>Usage: /smp <info|modules|reload|debug></red>"));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender,@NotNull Command command,@NotNull String alias,@NotNull String[] args){
        String l=command.getName().toLowerCase(Locale.ROOT);
        if(l.equals("smp")&&args.length==1)return match(args[0],List.of("info","modules","reload","debug"));
        if(l.equals("chat")&&args.length==1)return match(args[0],List.of("clear","mute","unmute","slow"));
        if(l.equals("channel")&&args.length==1)return match(args[0],List.of("global","local","trade","staff"));
        if(l.equals("locator")&&args.length==1)return match(args[0],List.of("status","on","off","auto"));
        if(l.equals("event")&&args.length==1)return match(args[0],List.of("status","start","stop"));
        if(l.equals("event")&&args.length==2&&args[0].equalsIgnoreCase("start"))return match(args[1],List.of("doublexp","locator","chatgames"));
        if(l.equals("manhunt")&&args.length==1)return match(args[0],List.of("start","begin","stop"));
        if(l.equals("chatgames")&&args.length==1)return match(args[0],List.of("start","stop","stats","top"));
        if(l.equals("maintenance")&&args.length==1)return match(args[0],List.of("on","off"));
        if(l.equals("restart")&&args.length==1)return match(args[0],List.of("60","30","10","cancel"));
        if(args.length==1 && Set.of("freeze","unfreeze","vanish","invsee","endersee","heal","feed","fly","god","tpo","tpohere","playerinfo","kick","warn","warnings","mute","tempmute","ban","tempban","history","note","report","altcheck","tpa","ignore","seen","realname","streak","profile","coinflip").contains(l))
            return match(args[0],Bukkit.getOnlinePlayers().stream().map(Player::getName).toList());
        return List.of();
    }

    private List<String> match(String prefix,List<String> options){String p=prefix.toLowerCase(Locale.ROOT);return options.stream().filter(s->s.toLowerCase(Locale.ROOT).startsWith(p)).sorted().toList();}
}
