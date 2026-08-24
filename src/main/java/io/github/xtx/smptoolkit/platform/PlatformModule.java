package io.github.xtx.smptoolkit.platform;

import io.github.xtx.smptoolkit.SMPToolkitPlugin;
import io.github.xtx.smptoolkit.api.SMPToolkitAPI;
import io.github.xtx.smptoolkit.api.SMPToolkitAPIImpl;
import io.github.xtx.smptoolkit.core.Module;
import io.github.xtx.smptoolkit.core.Text;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.event.Listener;
import org.bukkit.plugin.ServicePriority;

import java.sql.SQLException;
import java.util.Locale;

public final class PlatformModule implements Module {
    private final SMPToolkitPlugin plugin;
    private PlatformDatabase database;
    private UndoService undo;
    private CasesService cases;
    private ModerationGuiService moderation;
    private ModerationBridgeService moderationBridge;
    private ProgressionService progression;
    private BountyService bounties;
    private SchedulerService scheduler;
    private WorldProfileService worlds;
    private StaffActivityService staffActivity;
    private SeasonService seasons;
    private PvPService pvp;
    private OnboardingService onboarding;
    private WebApiService webApi;
    private PermissionPresetService permissions;
    private AdminGuiService adminGui;
    private SMPToolkitAPI api;

    public PlatformModule(SMPToolkitPlugin plugin){this.plugin=plugin;}
    @Override public String name(){return "platform";}

    @Override public void enable(){
        database=new PlatformDatabase(plugin);try{database.init();}catch(SQLException ex){throw new IllegalStateException("Could not initialize platform database",ex);}
        undo=new UndoService(plugin,database);
        cases=new CasesService(plugin,database,undo);
        moderation=new ModerationGuiService(plugin,database,undo);
        moderationBridge=new ModerationBridgeService(plugin,moderation);
        progression=new ProgressionService(plugin,database);
        bounties=new BountyService(plugin,database);
        scheduler=new SchedulerService(plugin,database);
        worlds=new WorldProfileService(plugin);
        staffActivity=new StaffActivityService(plugin,database);
        seasons=new SeasonService(plugin,database);
        pvp=new PvPService(plugin,database);
        onboarding=new OnboardingService(plugin,database);
        webApi=new WebApiService(plugin);
        permissions=new PermissionPresetService(plugin);
        adminGui=new AdminGuiService(plugin,database,moderation,cases,undo);
        register(cases);register(moderation);register(moderationBridge);register(progression);register(bounties);register(worlds);register(staffActivity);register(seasons);register(pvp);register(onboarding);register(adminGui);
        progression.enable();scheduler.enable();seasons.enable();webApi.enable();
        api=new SMPToolkitAPIImpl(plugin);Bukkit.getServicesManager().register(SMPToolkitAPI.class,api,plugin,ServicePriority.Normal);
    }

    @Override public void disable(){if(webApi!=null)webApi.disable();if(seasons!=null)seasons.disable();if(scheduler!=null)scheduler.disable();if(progression!=null)progression.disable();Bukkit.getServicesManager().unregisterAll(plugin);if(database!=null)database.close();}
    private void register(Listener listener){Bukkit.getPluginManager().registerEvents(listener,plugin);}

    public boolean handle(CommandSender sender,String label,String[] args){
        String l=label.toLowerCase(Locale.ROOT);
        if(adminGui.handle(sender,l,args))return true;
        if(moderation.handle(sender,l,args))return true;
        if(cases.handle(sender,l,args))return true;
        if(progression.handle(sender,l,args))return true;
        if(bounties.handle(sender,l,args))return true;
        switch(l){
            case "scheduler"->{return scheduler.handle(sender,args);}
            case "worldprofile"->{return worlds.handle(sender,args);}
            case "staffstats"->{return staffActivity.handle(sender,args);}
            case "rollback"->{return undo.handle(sender,args);}
            case "rulesaccept","onboarding"->{return onboarding.handle(sender,l,args);}
            case "season"->{return seasons.handle(sender,args);}
            case "pvpstats"->{return pvp.handle(sender,args);}
            case "webapi"->{return webApi.handle(sender,args);}
            case "smpermissions"->{return permissions.handle(sender,args);}
            case "smpapi"->{sender.sendMessage(Text.mm("<green>SMPToolkit API service is registered.</green> <gray>Version "+Text.escapeMini(plugin.getPluginMeta().getVersion())+"</gray>"));return true;}
        }
        return false;
    }

    public PlatformDatabase database(){return database;}
    public ModerationGuiService moderation(){return moderation;}
    public CasesService cases(){return cases;}
    public ProgressionService progression(){return progression;}
    public SeasonService seasons(){return seasons;}
    public WorldProfileService worlds(){return worlds;}
    public StaffActivityService staffActivity(){return staffActivity;}
    public UndoService undo(){return undo;}
}
