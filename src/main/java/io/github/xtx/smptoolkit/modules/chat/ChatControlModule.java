package io.github.xtx.smptoolkit.modules.chat;

import io.github.xtx.smptoolkit.SMPToolkitPlugin;
import io.github.xtx.smptoolkit.core.Module;
import io.github.xtx.smptoolkit.core.Text;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ChatControlModule implements Module, Listener {
    private static final Pattern URL=Pattern.compile("(?i)\\b(?:https?://|www\\.)?([a-z0-9-]+(?:\\.[a-z0-9-]+)+)(?:/\\S*)?");
    private final SMPToolkitPlugin plugin;
    private final Map<UUID,String> channels=new ConcurrentHashMap<>();
    private final Set<UUID> staffChat=ConcurrentHashMap.newKeySet();
    private final Set<UUID> chatSpy=ConcurrentHashMap.newKeySet();
    private final Set<UUID> socialSpy=ConcurrentHashMap.newKeySet();
    private final Map<UUID,Long> lastChatAt=new ConcurrentHashMap<>();
    private final Map<UUID,LastMessage> lastMessages=new ConcurrentHashMap<>();

    public ChatControlModule(SMPToolkitPlugin plugin){this.plugin=plugin;}
    @Override public String name(){return "chat-control";}
    @Override public void enable(){Bukkit.getPluginManager().registerEvents(this,plugin);}
    @Override public void disable(){}

    @EventHandler(priority=EventPriority.NORMAL,ignoreCancelled=true)
    public void onChat(AsyncChatEvent event){
        Player player=event.getPlayer();String plain=Text.plain(event.message());
        if(plugin.database().activePunishmentNow(player.getUniqueId(),"MUTE").isPresent()){event.setCancelled(true);player.sendMessage(Text.mm("<red>You are muted.</red>"));return;}
        String channel=channelOf(player);ConfigurationSection def=channelDef(channel);
        if(def==null||!def.getBoolean("enabled",true)){channel=defaultChannel();def=channelDef(channel);channels.put(player.getUniqueId(),channel);}
        if(channel.equalsIgnoreCase("staff")||staffChat.contains(player.getUniqueId())){event.setCancelled(true);sendStaff(player,plain);return;}
        if(def!=null&&def.getBoolean("muted",false)&&!player.hasPermission("smp.chat.admin")){event.setCancelled(true);player.sendMessage(Text.mm("<red>That chat channel is muted.</red>"));return;}
        long now=System.currentTimeMillis();int slow=def==null?plugin.getConfig().getInt("chat.slowmode-seconds",0):def.getInt("slowmode-seconds",plugin.getConfig().getInt("chat.slowmode-seconds",0));
        if(slow>0&&!player.hasPermission("smp.chat.slow.bypass")){long previous=lastChatAt.getOrDefault(player.getUniqueId(),0L);long remaining=slow*1000L-(now-previous);if(remaining>0){event.setCancelled(true);player.sendMessage(Text.mm("<red>Slow mode: wait "+Math.max(1,(remaining+999)/1000)+"s.</red>"));return;}}
        if(plugin.getConfig().getBoolean("chat.filters.enabled",true)&&!player.hasPermission("smp.chat.admin")){
            if(isRepeated(player,plain,now)){event.setCancelled(true);player.sendMessage(Text.mm("<red>Please do not repeat the same message.</red>"));return;}
            if(tooManyCaps(plain)){event.setCancelled(true);player.sendMessage(Text.mm("<red>Please reduce excessive capital letters.</red>"));return;}
            if(containsBlockedLink(plain)&&!player.hasPermission("smp.chat.links")){event.setCancelled(true);player.sendMessage(Text.mm("<red>Links are not allowed in chat.</red>"));return;}
        }
        lastChatAt.put(player.getUniqueId(),now);lastMessages.put(player.getUniqueId(),new LastMessage(plain,now));
        double radius=def==null?-1:def.getDouble("radius",-1);
        String permission=def==null?"":def.getString("permission","");
        final String finalChannel=channel;final ConfigurationSection finalDef=def;
        event.viewers().removeIf(audience->{if(!(audience instanceof Player target))return false;if(!permission.isBlank()&&!target.hasPermission(permission))return true;if(plugin.utilities()!=null&&plugin.utilities().isIgnoring(target.getUniqueId(),player.getUniqueId()))return true;if(radius>0&&(!target.getWorld().equals(player.getWorld())||target.getLocation().distanceSquared(player.getLocation())>radius*radius))return true;return false;});
        event.renderer((source,displayName,message,viewer)->render(source,finalChannel,finalDef,message,viewer instanceof Player p?p:null));
        if(plugin.getConfig().getBoolean("chat.mentions.enabled",true))for(Player target:Bukkit.getOnlinePlayers())if(!target.equals(player)&&plain.toLowerCase(Locale.ROOT).contains("@"+target.getName().toLowerCase(Locale.ROOT))){Player mentioned=target;plugin.sync(()->mentioned.playSound(mentioned.getLocation(),Sound.BLOCK_NOTE_BLOCK_PLING,0.6f,1.6f));}
        sendChatSpy(player,channel,plain);
        if(def==null||def.getBoolean("discord-log",true))plugin.discord().sendMinecraftChat(player.getName(),channel.toUpperCase(Locale.ROOT),plain);
    }

    private Component render(Player source,String channel,ConfigurationSection def,Component message,Player viewer){
        String format=def==null?plugin.getConfig().getString("chat.format","<dark_gray>[<channel>]</dark_gray> <prefix><white><name></white><suffix><gray>: </gray><message>"):def.getString("format",plugin.getConfig().getString("chat.format","<dark_gray>[<channel>]</dark_gray> <prefix><white><name></white><suffix><gray>: </gray><message>"));
        String display=def==null?channel:def.getString("display",channel);String prefix=plugin.integrations().luckPerms().prefix(source),suffix=plugin.integrations().luckPerms().suffix(source);String msg=Text.escapeMini(Text.plain(message));
        if(viewer!=null&&plugin.getConfig().getBoolean("chat.mentions.enabled",true)){String mention="@"+viewer.getName();msg=msg.replace(mention,"<yellow><bold>"+Text.escapeMini(mention)+"</bold></yellow>");}
        String assembled=format.replace("<channel>",Text.escapeMini(display)).replace("<prefix>",prefix==null?"":prefix).replace("<suffix>",suffix==null?"":suffix).replace("<name>",Text.escapeMini(source.getName())).replace("<message>",msg);return MiniMessage.miniMessage().deserialize(assembled);
    }

    public String channelOf(Player player){if(staffChat.contains(player.getUniqueId()))return "staff";return channels.getOrDefault(player.getUniqueId(),defaultChannel());}
    public List<String> channelNames(){ConfigurationSection sec=plugin.getConfig().getConfigurationSection("chat.channels");if(sec==null)return List.of("global","local","trade","staff");return sec.getKeys(false).stream().sorted().toList();}

    public void spyPrivateMessage(Player from,Player to,String message){if(socialSpy.isEmpty())return;Component c=Text.mm("<dark_gray>[SocialSpy]</dark_gray> <white>"+Text.escapeMini(from.getName())+" → "+Text.escapeMini(to.getName())+":</white> <gray>"+Text.escapeMini(message)+"</gray>");for(UUID id:socialSpy){Player spy=Bukkit.getPlayer(id);if(spy!=null&&!spy.equals(from)&&!spy.equals(to))spy.sendMessage(c);}}
    private void sendChatSpy(Player from,String channel,String message){if(chatSpy.isEmpty())return;Component c=Text.mm("<dark_gray>[ChatSpy:"+Text.escapeMini(channel)+"]</dark_gray> <white>"+Text.escapeMini(from.getName())+":</white> <gray>"+Text.escapeMini(message)+"</gray>");for(UUID id:chatSpy){Player spy=Bukkit.getPlayer(id);if(spy!=null&&!spy.equals(from))spy.sendMessage(c);}}
    private void sendStaff(Player player,String plain){Component msg=Text.mm("<dark_gray>[</dark_gray><aqua>Staff</aqua><dark_gray>]</dark_gray> <white>"+Text.escapeMini(player.getName())+"</white><gray>: </gray><white>"+Text.escapeMini(plain)+"</white>");for(Player target:Bukkit.getOnlinePlayers())if(target.hasPermission("smp.staff.chat"))target.sendMessage(msg);Bukkit.getConsoleSender().sendMessage(msg);if(plugin.getConfig().getBoolean("discord.minecraft-chat-log.include-staff-chat",true))plugin.discord().sendMinecraftChat(player.getName(),"STAFF",plain);}

    public boolean handle(CommandSender sender,String label,String[] args){
        switch(label.toLowerCase(Locale.ROOT)){
            case "staffchat","sc"->{if(!(sender instanceof Player p)){sender.sendMessage("Players only.");return true;}if(args.length>0)sendStaff(p,Text.join(args,0));else{boolean on=!staffChat.remove(p.getUniqueId());if(on)staffChat.add(p.getUniqueId());p.sendMessage(Text.mm(on?"<aqua>Staff chat enabled.</aqua>":"<gray>Staff chat disabled.</gray>"));}return true;}
            case "chatspy"->{if(!(sender instanceof Player p)){sender.sendMessage("Players only.");return true;}boolean on=!chatSpy.remove(p.getUniqueId());if(on)chatSpy.add(p.getUniqueId());p.sendMessage(Text.mm(on?"<green>Chat spy enabled.</green>":"<gray>Chat spy disabled.</gray>"));return true;}
            case "socialspy"->{if(!(sender instanceof Player p)){sender.sendMessage("Players only.");return true;}boolean on=!socialSpy.remove(p.getUniqueId());if(on)socialSpy.add(p.getUniqueId());p.sendMessage(Text.mm(on?"<green>Social spy enabled.</green>":"<gray>Social spy disabled.</gray>"));return true;}
            case "channel"->{if(!(sender instanceof Player p)){sender.sendMessage("Players only.");return true;}if(args.length==0){p.sendMessage(Text.mm("<gray>Channel:</gray> <yellow>"+Text.escapeMini(channelOf(p))+"</yellow>"));return true;}String requested=args[0].toLowerCase(Locale.ROOT);ConfigurationSection def=channelDef(requested);if(def==null||!def.getBoolean("enabled",true)){p.sendMessage(Text.mm("<red>Unknown or disabled channel. Available: "+Text.escapeMini(String.join(", ",channelNames()))+"</red>"));return true;}String permission=def.getString("permission","");if(!permission.isBlank()&&!p.hasPermission(permission)){p.sendMessage(Text.mm("<red>No permission for that channel.</red>"));return true;}channels.put(p.getUniqueId(),requested);staffChat.remove(p.getUniqueId());p.sendMessage(Text.mm("<green>Chat channel set to "+Text.escapeMini(def.getString("display",requested))+".</green>"));return true;}
            case "chat"->{return chatAdmin(sender,args);}
        }return false;
    }

    private boolean chatAdmin(CommandSender sender,String[] args){
        if(args.length==0){sender.sendMessage(Text.mm("<yellow>/chat clear | mute [channel] | unmute [channel] | slow <seconds> [channel]</yellow>"));return true;}
        switch(args[0].toLowerCase(Locale.ROOT)){
            case "clear"->{for(Player p:Bukkit.getOnlinePlayers())for(int i=0;i<80;i++)p.sendMessage(Component.empty());Bukkit.broadcast(Text.mm("<gray>Chat was cleared by <white>"+Text.escapeMini(sender.getName())+"</white>.</gray>"));}
            case "mute","unmute"->{String channel=args.length>1?args[1].toLowerCase(Locale.ROOT):defaultChannel();ConfigurationSection def=channelDef(channel);if(def==null){sender.sendMessage(Text.mm("<red>Unknown channel.</red>"));return true;}boolean muted=args[0].equalsIgnoreCase("mute");plugin.getConfig().set("chat.channels."+channel+".muted",muted);plugin.saveConfig();Bukkit.broadcast(Text.mm(muted?"<red>"+Text.escapeMini(channel)+" chat has been muted.</red>":"<green>"+Text.escapeMini(channel)+" chat has been unmuted.</green>"));}
            case "slow"->{if(args.length<2){sender.sendMessage(Text.mm("<red>/chat slow <seconds> [channel]</red>"));return true;}try{int seconds=Math.max(0,Integer.parseInt(args[1]));String channel=args.length>2?args[2].toLowerCase(Locale.ROOT):defaultChannel();if(channelDef(channel)==null){sender.sendMessage(Text.mm("<red>Unknown channel.</red>"));return true;}plugin.getConfig().set("chat.channels."+channel+".slowmode-seconds",seconds);plugin.saveConfig();sender.sendMessage(Text.mm("<green>Slowmode for "+Text.escapeMini(channel)+" set to "+seconds+"s.</green>"));}catch(NumberFormatException ex){sender.sendMessage(Text.mm("<red>Seconds must be a number.</red>"));}}
            default->sender.sendMessage(Text.mm("<red>Unknown chat subcommand.</red>"));
        }return true;
    }

    private String defaultChannel(){String configured=plugin.getConfig().getString("chat.default-channel","global").toLowerCase(Locale.ROOT);return channelDef(configured)==null?"global":configured;}
    private ConfigurationSection channelDef(String channel){return plugin.getConfig().getConfigurationSection("chat.channels."+channel.toLowerCase(Locale.ROOT));}
    private boolean isRepeated(Player player,String message,long now){int window=plugin.getConfig().getInt("chat.filters.repeat-window-seconds",8);LastMessage old=lastMessages.get(player.getUniqueId());return old!=null&&now-old.time<=window*1000L&&old.text.equalsIgnoreCase(message.trim());}
    private boolean tooManyCaps(String message){if(!plugin.getConfig().getBoolean("chat.filters.caps-enabled",true))return false;int min=plugin.getConfig().getInt("chat.filters.caps-min-length",10);if(message.length()<min)return false;int letters=0,upper=0;for(char c:message.toCharArray())if(Character.isLetter(c)){letters++;if(Character.isUpperCase(c))upper++;}if(letters<min)return false;return (int)Math.round(100.0*upper/Math.max(1,letters))>=plugin.getConfig().getInt("chat.filters.caps-max-percent",75);}
    private boolean containsBlockedLink(String message){if(!plugin.getConfig().getBoolean("chat.filters.links-enabled",true))return false;Matcher matcher=URL.matcher(message);List<String> whitelist=plugin.getConfig().getStringList("chat.filters.link-whitelist");while(matcher.find()){String domain=matcher.group(1).toLowerCase(Locale.ROOT);boolean allowed=whitelist.stream().map(s->s.toLowerCase(Locale.ROOT)).anyMatch(w->domain.equals(w)||domain.endsWith("."+w));if(!allowed)return true;}return false;}
    private record LastMessage(String text,long time){}
}
