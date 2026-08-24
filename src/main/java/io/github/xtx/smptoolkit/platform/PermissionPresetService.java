package io.github.xtx.smptoolkit.platform;

import io.github.xtx.smptoolkit.SMPToolkitPlugin;
import io.github.xtx.smptoolkit.core.Text;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;

import java.util.Locale;

public final class PermissionPresetService {
    private final SMPToolkitPlugin plugin;
    public PermissionPresetService(SMPToolkitPlugin plugin){this.plugin=plugin;}

    public boolean handle(CommandSender sender,String[] args){
        if(args.length==0||args[0].equalsIgnoreCase("list")){
            ConfigurationSection presets=plugin.getConfig().getConfigurationSection("platform.permission-presets");sender.sendMessage(Text.mm("<gold><bold>Permission Presets</bold></gold>"));if(presets==null){sender.sendMessage(Text.mm("<gray>None configured.</gray>"));return true;}sender.sendMessage(Text.mm("<white>"+Text.escapeMini(String.join(", ",presets.getKeys(false)))+"</white>"));return true;
        }
        if(args.length<3||!args[0].equalsIgnoreCase("apply")){sender.sendMessage(Text.mm("<red>/smpermissions apply <group> <preset></red>"));return true;}
        if(!plugin.integrations().luckPerms().available()){sender.sendMessage(Text.mm("<red>LuckPerms is not available.</red>"));return true;}
        String group=safe(args[1]),preset=args[2].toLowerCase(Locale.ROOT);if(group.isBlank()){sender.sendMessage(Text.mm("<red>Invalid group name.</red>"));return true;}
        ConfigurationSection def=plugin.getConfig().getConfigurationSection("platform.permission-presets."+preset);if(def==null){sender.sendMessage(Text.mm("<red>Unknown preset.</red>"));return true;}
        if(def.getBoolean("create-group",true))Bukkit.dispatchCommand(Bukkit.getConsoleSender(),"lp creategroup "+group);
        for(String node:def.getStringList("permissions"))Bukkit.dispatchCommand(Bukkit.getConsoleSender(),"lp group "+group+" permission set "+node+" true");
        String parent=safe(def.getString("parent",""));if(!parent.isBlank())Bukkit.dispatchCommand(Bukkit.getConsoleSender(),"lp group "+group+" parent add "+parent);
        sender.sendMessage(Text.mm("<green>Applied preset "+Text.escapeMini(preset)+" to LuckPerms group "+Text.escapeMini(group)+".</green>"));return true;
    }
    private String safe(String raw){return raw==null?"":raw.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]","");}
}
