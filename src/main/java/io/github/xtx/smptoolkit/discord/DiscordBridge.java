package io.github.xtx.smptoolkit.discord;

import io.github.xtx.smptoolkit.SMPToolkitPlugin;
import io.github.xtx.smptoolkit.core.Text;
import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.server.ServerCommandEvent;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Locale;

public final class DiscordBridge implements Listener {
    private final SMPToolkitPlugin plugin;
    private final HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    public DiscordBridge(SMPToolkitPlugin plugin){this.plugin=plugin;}

    @EventHandler(priority=EventPriority.MONITOR)
    public void onCommand(PlayerCommandPreprocessEvent event){
        Player player=event.getPlayer();
        boolean shouldLog=(plugin.getConfig().getBoolean("discord.admin-command-log.log-op-players",true)&&player.isOp())||player.hasPermission(plugin.getConfig().getString("discord.admin-command-log.permission","smp.staff.commandlog"));
        if(!shouldLog)return;
        sendAdminLog("PLAYER",player.getName(),redact(event.getMessage()));
    }

    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void onConsoleCommand(ServerCommandEvent event){if(!plugin.getConfig().getBoolean("discord.admin-command-log.log-console",true))return;sendAdminLog("CONSOLE",event.getSender().getName(),redact("/"+event.getCommand()));}

    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void onChat(AsyncChatEvent event){
        if(!plugin.getConfig().getBoolean("discord.minecraft-chat-log.enabled",false))return;
        String channel=plugin.chat()==null?"GLOBAL":plugin.chat().channelOf(event.getPlayer()).toUpperCase(Locale.ROOT);
        sendMinecraftChat(event.getPlayer().getName(),channel,Text.plain(event.message()));
    }

    public void sendMinecraftChat(String actor,String channel,String message){if(!plugin.getConfig().getBoolean("discord.minecraft-chat-log.enabled",false))return;String content=plugin.getConfig().getBoolean("discord.minecraft-chat-log.include-channel",true)?"["+channel+"] **"+actor+"**: "+message:"**"+actor+"**: "+message;send(plugin.getConfig().getString("discord.minecraft-chat-log.webhook-url",""),content,"Minecraft Chat");}
    public void sendStaffAudit(String content){sendAdminLog("STAFF","SMPToolkit",content);}

    private void sendAdminLog(String source,String actor,String command){if(!plugin.getConfig().getBoolean("discord.admin-command-log.enabled",false))return;String text="**"+source+"** `"+actor+"` → `"+command.replace("`","'")+"`";send(plugin.getConfig().getString("discord.admin-command-log.webhook-url",""),text,"Admin Command Log");}
    private String redact(String raw){String trimmed=raw==null?"":raw.trim();String commandToken=trimmed.split("\\s+",2)[0].toLowerCase(Locale.ROOT);List<String> prefixes=plugin.getConfig().getStringList("discord.admin-command-log.redact-prefixes");for(String prefix:prefixes){String protectedCommand=prefix.trim().split("\\s+",2)[0].toLowerCase(Locale.ROOT);if(commandToken.equals(protectedCommand))return prefix+" <redacted>";}return raw;}

    private void send(String url,String content,String username){
        if(url==null||url.isBlank()||content==null||content.isBlank())return;
        String json="{\"username\":\""+json(username)+"\",\"content\":\""+json(truncate(content,1900))+"\",\"allowed_mentions\":{\"parse\":[]}}";
        try{
            HttpRequest request=HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(8)).header("Content-Type","application/json; charset=utf-8").header("User-Agent","SMPToolkit/0.2").POST(HttpRequest.BodyPublishers.ofString(json,StandardCharsets.UTF_8)).build();
            client.sendAsync(request,HttpResponse.BodyHandlers.discarding()).thenAccept(response->{if(response.statusCode()<200||response.statusCode()>=300)plugin.getLogger().warning("Discord webhook returned HTTP "+response.statusCode());}).exceptionally(ex->{plugin.getLogger().warning("Discord webhook failed: "+ex.getMessage());return null;});
        }catch(IllegalArgumentException ex){plugin.getLogger().warning("Invalid Discord webhook URL.");}
    }

    private static String truncate(String value,int max){return value.length()<=max?value:value.substring(0,max-1)+"…";}
    private static String json(String s){StringBuilder out=new StringBuilder();for(char c:s.toCharArray()){switch(c){case '"'->out.append("\\\"");case '\\'->out.append("\\\\");case '\n'->out.append("\\n");case '\r'->out.append("\\r");case '\t'->out.append("\\t");default->{if(c<0x20)out.append(String.format("\\u%04x",(int)c));else out.append(c);}}}return out.toString();}
}
