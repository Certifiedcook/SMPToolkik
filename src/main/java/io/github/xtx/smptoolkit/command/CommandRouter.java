package io.github.xtx.smptoolkit.command;

import io.github.xtx.smptoolkit.SMPToolkitPlugin;
import io.github.xtx.smptoolkit.core.Text;
import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public final class CommandRouter implements CommandExecutor, TabCompleter, Listener {
    private static final List<String> COMMANDS=List.of(
            "smp","smpgui","configgui","profilegui","smpapi","help","smphelp",
            "staff","staffchat","staffgui","freeze","unfreeze","vanish","invsee","endersee","heal","feed","fly","god","speed","tpo","tpohere","playerinfo","maintenance","restart","staffstats",
            "kick","warn","removewarn","warnings","mute","tempmute","unmute","ban","tempban","unban","pardon","punish","history","note","notes","report","reports","altcheck","case","cases","rollback",
            "chat","channel","chatspy","socialspy","ignore","msg","reply","chatgames",
            "stats","topkills","topdeaths","topkd","topplaytime","locator","pvpstats","season","quests","quest","achievements","bounty","bounties",
            "tpa","tpaccept","tpdeny","tptoggle","sethome","home","delhome","homes","setspawn","spawn","back","seen","realname","afk","rtp",
            "voteskipnight","streak","profile","coinflip","manhunt","signup","event","announce","scheduler","worldprofile","webapi","smpermissions","onboarding","rulesaccept","discord","rules"
    );
    private final SMPToolkitPlugin plugin;
    public CommandRouter(SMPToolkitPlugin plugin){this.plugin=plugin;}

    public void register(){for(String name:COMMANDS){PluginCommand command=plugin.getCommand(name);if(command!=null){command.setExecutor(this);command.setTabCompleter(this);}}Bukkit.getPluginManager().registerEvents(this,plugin);}

    @EventHandler(priority=EventPriority.LOWEST)
    public void onHelpCommand(PlayerCommandPreprocessEvent event){String message=event.getMessage().trim();if(message.length()<2)return;String command=message.substring(1).split("\\s+",2)[0];if(!command.equalsIgnoreCase("help"))return;event.setCancelled(true);showHelp(event.getPlayer());}

    @Override public boolean onCommand(@NotNull CommandSender sender,@NotNull Command command,@NotNull String label,@NotNull String[] args){
        String l=command.getName().toLowerCase(Locale.ROOT);
        if(l.equals("help")||l.equals("smphelp"))return showHelp(sender);
        if(l.equals("smp"))return smp(sender,args);
        if(!plugin.commandEnabled(l)){plugin.messages().send(sender,"general.feature-disabled");return true;}
        if(plugin.isModuleEnabled("platform")&&plugin.platform().handle(sender,l,args))return true;
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
        sender.sendMessage(Text.mm("<red>That feature is unavailable or its module is disabled.</red>"));return true;
    }

    private boolean showHelp(CommandSender sender){
        boolean operator=!(sender instanceof Player)||sender.isOp();sender.sendMessage(Text.mm("<gold><bold>SMPToolkit Help</bold></gold> <gray>v"+Text.escapeMini(plugin.getPluginMeta().getVersion())+"</gray>"));
        if(!operator){
            sendGroup(sender,"Travel","tpa","tpaccept","tpdeny","tptoggle","sethome","home","delhome","homes","spawn","back","rtp");
            sendGroup(sender,"Chat","channel","ignore","msg","reply");
            sendGroup(sender,"Stats","stats","topkills","topdeaths","topkd","topplaytime","profile","streak","pvpstats","season");
            sendGroup(sender,"SMP","voteskipnight","coinflip","chatgames","locator","signup","quests","quest","achievements","bounty","bounties");
            sendGroup(sender,"Player","report","seen","realname","afk","rules","rulesaccept","discord");return true;
        }
        sendGroup(sender,"Core","smp","smpgui","configgui","profilegui","smpapi","help","smphelp");
        sendGroup(sender,"Staff","staff","staffchat","staffgui","freeze","unfreeze","vanish","invsee","endersee","heal","feed","fly","god","speed","tpo","tpohere","playerinfo","maintenance","restart","staffstats");
        sendGroup(sender,"Moderation","kick","warn","removewarn","warnings","mute","tempmute","unmute","ban","tempban","unban","pardon","punish","history","note","notes","report","reports","altcheck","case","cases","rollback");
        sendGroup(sender,"Chat","chat","channel","chatspy","socialspy","ignore","msg","reply");
        sendGroup(sender,"Games & Stats","chatgames","stats","topkills","topdeaths","topkd","topplaytime","locator","pvpstats","season","quests","quest","achievements","bounty","bounties");
        sendGroup(sender,"Utilities","tpa","tpaccept","tpdeny","tptoggle","sethome","home","delhome","homes","setspawn","spawn","back","seen","realname","afk","rtp");
        sendGroup(sender,"Gameplay","voteskipnight","streak","profile","coinflip","manhunt","signup");
        sendGroup(sender,"Events & Server","event","announce","scheduler","worldprofile","webapi","smpermissions","onboarding","rulesaccept","discord","rules");return true;
    }

    private void sendGroup(CommandSender sender,String title,String... names){List<String> enabled=Arrays.stream(names).filter(plugin::commandEnabled).map(n->"/"+n).toList();if(!enabled.isEmpty())sender.sendMessage(Text.mm("<aqua>"+Text.escapeMini(title)+":</aqua> <white>"+Text.escapeMini(String.join(" ",enabled))+"</white>"));}

    private boolean smp(CommandSender sender,String[] args){
        if(args.length==0||args[0].equalsIgnoreCase("info")){sender.sendMessage(Text.mm("<gold><bold>SMPToolkit "+Text.escapeMini(plugin.getPluginMeta().getVersion())+"</bold></gold> <gray>Paper 26.2 modular SMP suite.</gray>"));sender.sendMessage(Text.mm("<gray>Enabled modules:</gray> <white>"+Text.escapeMini(String.join(", ",plugin.enabledModuleNames()))+"</white>"));return true;}
        switch(args[0].toLowerCase(Locale.ROOT)){
            case "modules"->sender.sendMessage(Text.mm("<gray>Enabled:</gray> <white>"+Text.escapeMini(String.join(", ",plugin.enabledModuleNames()))+"</white>"));
            case "reload"->{plugin.reloadAllConfiguration();sender.sendMessage(Text.mm("<green>Configuration and messages reloaded.</green> <gray>Module enable/disable changes require a server restart.</gray>"));}
            case "debug"->{sender.sendMessage(Text.mm("<gray>Online:</gray> <white>"+Bukkit.getOnlinePlayers().size()+"</white> <gray>Locator:</gray> <white>"+plugin.locator().isActive()+"</white>"));sender.sendMessage(Text.mm("<gray>LuckPerms:</gray> <white>"+plugin.integrations().luckPerms().available()+"</white> <gray>Season:</gray> <white>"+(plugin.platform()==null?"":Text.escapeMini(plugin.platform().seasons().activeId()))+"</white>"));}
            case "gui"->{if(sender instanceof Player p)Bukkit.dispatchCommand(p,"smpgui");else sender.sendMessage("Players only.");}
            default->sender.sendMessage(Text.mm("<red>Usage: /smp <info|modules|reload|debug|gui></red>"));
        }return true;
    }

    @Override public List<String> onTabComplete(@NotNull CommandSender sender,@NotNull Command command,@NotNull String alias,@NotNull String[] args){
        String l=command.getName().toLowerCase(Locale.ROOT);
        if(l.equals("smp")&&args.length==1)return match(args[0],List.of("info","modules","reload","debug","gui"));
        if(l.equals("chat")&&args.length==1)return match(args[0],List.of("clear","mute","unmute","slow"));
        if(l.equals("channel")&&args.length==1)return match(args[0],plugin.chat().channelNames());
        if(l.equals("locator")&&args.length==1)return match(args[0],List.of("status","on","off","auto"));
        if(l.equals("event")&&args.length==1)return match(args[0],List.of("status","start","stop"));
        if(l.equals("event")&&args.length==2&&args[0].equalsIgnoreCase("start"))return match(args[1],plugin.events().eventIds());
        if(l.equals("case")&&args.length==1)return match(args[0],List.of("create","view","claim","close","reopen","comment","evidence","link","list"));
        if(l.equals("season")&&args.length==1)return match(args[0],List.of("status","top","start","end"));
        if(l.equals("scheduler")&&args.length==1)return match(args[0],List.of("list","run","check"));
        if(l.equals("bounty")&&args.length==1)return match(args[0],List.of("add","cancel","list"));
        if(l.equals("quest")&&args.length==1)return match(args[0],List.of("claim"));
        if(l.equals("smpermissions")&&args.length==1)return match(args[0],List.of("list","apply"));
        if(l.equals("webapi")&&args.length==1)return match(args[0],List.of("status","start","stop","restart"));
        if(l.equals("onboarding")&&args.length==1)return match(args[0],List.of("reset"));
        if(l.equals("manhunt")&&args.length==1)return match(args[0],List.of("start","begin","stop"));
        if(l.equals("chatgames")&&args.length==1)return match(args[0],List.of("start","stop","stats","top"));
        if(l.equals("maintenance")&&args.length==1)return match(args[0],List.of("on","off"));
        if(l.equals("restart")&&args.length==1)return match(args[0],List.of("60","30","10","cancel"));
        if(l.equals("punish")&&args.length==2){ConfigurationSection s=plugin.getConfig().getConfigurationSection("platform.moderation.templates");return match(args[1],s==null?List.of():s.getKeys(false).stream().toList());}
        if(args.length==1&&Set.of("freeze","unfreeze","vanish","invsee","endersee","heal","feed","fly","god","tpo","tpohere","playerinfo","profilegui","kick","warn","warnings","mute","tempmute","ban","tempban","history","note","notes","report","altcheck","tpa","ignore","seen","realname","streak","profile","coinflip","pvpstats","achievements","bounty","worldprofile","staffstats").contains(l))return match(args[0],Bukkit.getOnlinePlayers().stream().map(Player::getName).toList());
        return List.of();
    }
    private List<String> match(String prefix,List<String> options){String p=prefix.toLowerCase(Locale.ROOT);return options.stream().filter(s->s.toLowerCase(Locale.ROOT).startsWith(p)).sorted().toList();}
}
