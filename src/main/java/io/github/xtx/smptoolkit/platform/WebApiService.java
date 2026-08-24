package io.github.xtx.smptoolkit.platform;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.github.xtx.smptoolkit.SMPToolkitPlugin;
import io.github.xtx.smptoolkit.core.Text;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class WebApiService {
    private final SMPToolkitPlugin plugin;
    private HttpServer server;
    private ExecutorService executor;
    public WebApiService(SMPToolkitPlugin plugin){this.plugin=plugin;}

    public void enable(){if(plugin.featureEnabled("web-api")&&plugin.getConfig().getBoolean("platform.web-api.enabled",false))start();}
    public void disable(){stop();}

    public boolean handle(CommandSender sender,String[] args){
        String sub=args.length==0?"status":args[0].toLowerCase(Locale.ROOT);
        switch(sub){
            case "status"->sender.sendMessage(Text.mm(server==null?"<gray>Web API is stopped.</gray>":"<green>Web API is running.</green>"));
            case "start"->{start();sender.sendMessage(Text.mm(server==null?"<red>Web API did not start. Check config/log.</red>":"<green>Web API started.</green>"));}
            case "stop"->{stop();sender.sendMessage(Text.mm("<gray>Web API stopped.</gray>"));}
            case "restart"->{stop();start();sender.sendMessage(Text.mm(server==null?"<red>Web API did not start.</red>":"<green>Web API restarted.</green>"));}
            default->sender.sendMessage(Text.mm("<red>/webapi <status|start|stop|restart></red>"));
        }return true;
    }

    private synchronized void start(){
        if(server!=null)return;
        String token=plugin.getConfig().getString("platform.web-api.token","");boolean require=plugin.getConfig().getBoolean("platform.web-api.require-token",true);
        if(require&&token.isBlank()){plugin.getLogger().warning("Web API is enabled but no token is configured; refusing to start.");return;}
        String bind=plugin.getConfig().getString("platform.web-api.bind","127.0.0.1");int port=plugin.getConfig().getInt("platform.web-api.port",8765);
        try{
            server=HttpServer.create(new InetSocketAddress(bind,port),0);executor=Executors.newVirtualThreadPerTaskExecutor();server.setExecutor(executor);
            server.createContext("/status",ex->serve(ex,this::statusJson));
            server.createContext("/players",ex->serve(ex,this::playersJson));
            server.createContext("/leaderboard",ex->serve(ex,()->leaderboardJson(ex)));
            server.createContext("/season",ex->serve(ex,this::seasonJson));
            server.start();plugin.getLogger().info("SMPToolkit web API listening on "+bind+":"+port);
        }catch(IOException ex){plugin.getLogger().severe("Could not start web API: "+ex.getMessage());server=null;if(executor!=null)executor.shutdownNow();executor=null;}
    }

    private synchronized void stop(){if(server!=null){server.stop(1);server=null;}if(executor!=null){executor.shutdownNow();executor=null;}}

    private void serve(HttpExchange exchange,JsonSupplier body)throws IOException{
        try{
            if(!exchange.getRequestMethod().equalsIgnoreCase("GET")){respond(exchange,405,"{\"error\":\"method_not_allowed\"}");return;}
            if(!authorized(exchange)){respond(exchange,401,"{\"error\":\"unauthorized\"}");return;}
            respond(exchange,200,body.get());
        }catch(Exception ex){respond(exchange,500,"{\"error\":\"internal_error\"}");}
    }

    private boolean authorized(HttpExchange exchange){if(!plugin.getConfig().getBoolean("platform.web-api.require-token",true))return true;String expected=plugin.getConfig().getString("platform.web-api.token","");String auth=exchange.getRequestHeaders().getFirst("Authorization");return auth!=null&&auth.equals("Bearer "+expected);}
    private String statusJson(){return "{\"plugin\":\"SMPToolkit\",\"version\":\""+json(plugin.getPluginMeta().getVersion())+"\",\"online\":"+Bukkit.getOnlinePlayers().size()+",\"max\":"+Bukkit.getMaxPlayers()+",\"season\":\""+json(plugin.platform()==null?"":plugin.platform().seasons().activeId())+"\"}";}
    private String playersJson(){if(!plugin.getConfig().getBoolean("platform.web-api.expose-online-names",false))return "{\"online\":"+Bukkit.getOnlinePlayers().size()+"}";String names=Bukkit.getOnlinePlayers().stream().map(Player::getName).map(n->"\""+json(n)+"\"").reduce((a,b)->a+","+b).orElse("");return "{\"online\":"+Bukkit.getOnlinePlayers().size()+",\"players\":["+names+"]}";}
    private String seasonJson(){if(plugin.platform()==null)return "{}";SeasonService s=plugin.platform().seasons();return "{\"id\":\""+json(s.activeId())+"\",\"display\":\""+json(s.activeDisplay())+"\"}";}
    private String leaderboardJson(HttpExchange ex){String metric="kills";String q=ex.getRequestURI().getRawQuery();if(q!=null)for(String part:q.split("&")){String[] kv=part.split("=",2);if(kv.length==2&&kv[0].equals("metric"))metric=kv[1];}if(!Set.of("kills","deaths","kd","playtime","chat_wins").contains(metric))metric="kills";var rows=plugin.database().leaderboard(metric,10).join();StringBuilder out=new StringBuilder("{\"metric\":\"").append(json(metric)).append("\",\"entries\":[");for(int i=0;i<rows.size();i++){if(i>0)out.append(',');var r=rows.get(i);String value=switch(metric){case "deaths"->Integer.toString(r.deaths());case "kd"->Double.toString(r.kd());case "playtime"->Long.toString(r.playtimeMs());case "chat_wins"->Integer.toString(r.chatWins());default->Integer.toString(r.kills());};out.append("{\"name\":\"").append(json(r.name())).append("\",\"value\":").append(value).append('}');}return out.append("]}").toString();}
    private void respond(HttpExchange ex,int status,String body)throws IOException{byte[] bytes=body.getBytes(StandardCharsets.UTF_8);ex.getResponseHeaders().set("Content-Type","application/json; charset=utf-8");ex.getResponseHeaders().set("Cache-Control","no-store");ex.sendResponseHeaders(status,bytes.length);try(var os=ex.getResponseBody()){os.write(bytes);}}
    private String json(String raw){if(raw==null)return "";return raw.replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n").replace("\r","\\r");}
    @FunctionalInterface private interface JsonSupplier{String get()throws Exception;}
}
