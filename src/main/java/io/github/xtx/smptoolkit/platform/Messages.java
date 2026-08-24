package io.github.xtx.smptoolkit.platform;

import io.github.xtx.smptoolkit.SMPToolkitPlugin;
import io.github.xtx.smptoolkit.core.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class Messages {
    private final SMPToolkitPlugin plugin;
    private final Map<String,YamlConfiguration> locales = new ConcurrentHashMap<>();
    private YamlConfiguration defaults;

    public Messages(SMPToolkitPlugin plugin){this.plugin=plugin;}

    public void load(){
        File file=new File(plugin.getDataFolder(),"messages.yml");
        if(!file.exists()) plugin.saveResource("messages.yml",false);
        defaults=YamlConfiguration.loadConfiguration(file);
        locales.clear();
        File dir=new File(plugin.getDataFolder(),"locales");
        if(!dir.exists())dir.mkdirs();
        File[] files=dir.listFiles((d,n)->n.toLowerCase(Locale.ROOT).endsWith(".yml"));
        if(files!=null)for(File locale:files)locales.put(locale.getName().substring(0,locale.getName().length()-4).toLowerCase(Locale.ROOT),YamlConfiguration.loadConfiguration(locale));
    }

    public Component get(CommandSender sender,String key,Object... replacements){
        String raw=raw(sender,key);
        for(int i=0;i+1<replacements.length;i+=2)raw=raw.replace("%"+replacements[i]+"%",String.valueOf(replacements[i+1]));
        return Text.mm(raw);
    }

    public String raw(CommandSender sender,String key){
        String fallback=defaults==null?key:defaults.getString(key,"<red>Missing message: "+key+"</red>");
        if(!(sender instanceof Player player)||!plugin.getConfig().getBoolean("platform.localization.per-player",false))return fallback;
        String locale=player.locale().toString().toLowerCase(Locale.ROOT);
        YamlConfiguration cfg=locales.get(locale);
        if(cfg==null&&locale.contains("_"))cfg=locales.get(locale.substring(0,locale.indexOf('_')));
        return cfg==null?fallback:cfg.getString(key,fallback);
    }

    public void send(CommandSender sender,String key,Object... replacements){sender.sendMessage(get(sender,key,replacements));}
}
