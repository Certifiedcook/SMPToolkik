package io.github.xtx.smptoolkit;

import io.github.xtx.smptoolkit.command.CommandRouter;
import io.github.xtx.smptoolkit.core.Module;
import io.github.xtx.smptoolkit.data.Database;
import io.github.xtx.smptoolkit.discord.DiscordBridge;
import io.github.xtx.smptoolkit.integrations.Integrations;
import io.github.xtx.smptoolkit.modules.announcements.AnnouncementsModule;
import io.github.xtx.smptoolkit.modules.chat.ChatControlModule;
import io.github.xtx.smptoolkit.modules.chatgames.ChatGamesModule;
import io.github.xtx.smptoolkit.modules.events.EventsModule;
import io.github.xtx.smptoolkit.modules.gameplay.GameplayModule;
import io.github.xtx.smptoolkit.modules.locator.LocatorModule;
import io.github.xtx.smptoolkit.modules.moderation.AltCheckModule;
import io.github.xtx.smptoolkit.modules.moderation.ModerationModule;
import io.github.xtx.smptoolkit.modules.staff.StaffModule;
import io.github.xtx.smptoolkit.modules.stats.StatsModule;
import io.github.xtx.smptoolkit.modules.utilities.UtilitiesModule;
import io.github.xtx.smptoolkit.platform.Messages;
import io.github.xtx.smptoolkit.platform.PlatformModule;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.security.SecureRandom;
import java.sql.SQLException;
import java.util.*;

public final class SMPToolkitPlugin extends JavaPlugin {
    private Database database;
    private DiscordBridge discord;
    private Integrations integrations;
    private Messages messages;
    private PlatformModule platform;
    private StaffModule staff;
    private ModerationModule moderation;
    private AltCheckModule altCheck;
    private ChatControlModule chat;
    private ChatGamesModule chatGames;
    private StatsModule stats;
    private LocatorModule locator;
    private UtilitiesModule utilities;
    private GameplayModule gameplay;
    private EventsModule events;
    private AnnouncementsModule announcements;
    private final List<Module> enabledModules=new ArrayList<>();

    @Override public void onEnable(){
        saveDefaultConfig();ensurePrivacySalt();
        messages=new Messages(this);messages.load();
        database=new Database(this);
        try{database.init();}catch(SQLException ex){getLogger().severe("Could not initialize SQLite: "+ex.getMessage());getServer().getPluginManager().disablePlugin(this);return;}
        discord=new DiscordBridge(this);Bukkit.getPluginManager().registerEvents(discord,this);
        stats=new StatsModule(this);locator=new LocatorModule(this);utilities=new UtilitiesModule(this);gameplay=new GameplayModule(this);staff=new StaffModule(this);moderation=new ModerationModule(this);altCheck=new AltCheckModule(this);chat=new ChatControlModule(this);chatGames=new ChatGamesModule(this);events=new EventsModule(this);announcements=new AnnouncementsModule(this);integrations=new Integrations(this);platform=new PlatformModule(this);
        if(getConfig().getBoolean("modules.integrations",true))integrations.enable();
        enableIfConfigured(stats);enableIfConfigured(locator);enableIfConfigured(utilities);enableIfConfigured(gameplay);enableIfConfigured(staff);enableIfConfigured(moderation);enableIfConfigured(altCheck);enableIfConfigured(chat);enableIfConfigured(chatGames);enableIfConfigured(events);enableIfConfigured(announcements);enableIfConfigured(platform);
        new CommandRouter(this).register();
        getLogger().info("SMPToolkit enabled with "+enabledModules.size()+" modules: "+String.join(", ",enabledModules.stream().map(Module::name).toList()));
    }

    @Override public void onDisable(){ListIterator<Module> it=enabledModules.listIterator(enabledModules.size());while(it.hasPrevious()){Module module=it.previous();try{module.disable();}catch(Throwable ex){getLogger().warning("Error disabling "+module.name()+": "+ex.getMessage());}}enabledModules.clear();if(database!=null)database.close();}

    private void enableIfConfigured(Module module){if(!getConfig().getBoolean("modules."+module.name(),true)){getLogger().info("Module disabled by config: "+module.name());return;}try{module.enable();enabledModules.add(module);}catch(Throwable ex){getLogger().severe("Failed to enable module "+module.name()+": "+ex.getMessage());ex.printStackTrace();}}
    private void ensurePrivacySalt(){String salt=getConfig().getString("privacy.ip-hash-salt","");if(salt!=null&&!salt.isBlank())return;byte[] bytes=new byte[32];new SecureRandom().nextBytes(bytes);getConfig().set("privacy.ip-hash-salt",Base64.getUrlEncoder().withoutPadding().encodeToString(bytes));saveConfig();}

    public void sync(Runnable runnable){if(Bukkit.isPrimaryThread())runnable.run();else Bukkit.getScheduler().runTask(this,runnable);}
    public boolean isModuleEnabled(String name){return enabledModules.stream().anyMatch(m->m.name().equalsIgnoreCase(name));}
    public List<String> enabledModuleNames(){return enabledModules.stream().map(Module::name).toList();}
    public boolean featureEnabled(String key){return getConfig().getBoolean("features.systems."+key,true);}
    public boolean commandEnabled(String command){return getConfig().getBoolean("features.commands."+command.toLowerCase(Locale.ROOT),true);}
    public void reloadAllConfiguration(){reloadConfig();if(messages!=null)messages.load();}

    public Database database(){return database;}
    public DiscordBridge discord(){return discord;}
    public Integrations integrations(){return integrations;}
    public Messages messages(){return messages;}
    public PlatformModule platform(){return platform;}
    public StaffModule staff(){return staff;}
    public ModerationModule moderation(){return moderation;}
    public AltCheckModule altCheck(){return altCheck;}
    public ChatControlModule chat(){return chat;}
    public ChatGamesModule chatGames(){return chatGames;}
    public StatsModule stats(){return stats;}
    public LocatorModule locator(){return locator;}
    public UtilitiesModule utilities(){return utilities;}
    public GameplayModule gameplay(){return gameplay;}
    public EventsModule events(){return events;}
    public AnnouncementsModule announcements(){return announcements;}
}
