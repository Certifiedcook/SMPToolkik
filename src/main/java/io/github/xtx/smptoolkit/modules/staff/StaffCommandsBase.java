package io.github.xtx.smptoolkit.modules.staff;

import io.github.xtx.smptoolkit.SMPToolkitPlugin;
import io.github.xtx.smptoolkit.core.Module;
import io.github.xtx.smptoolkit.core.Text;
import io.github.xtx.smptoolkit.data.Database;
import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;

class StaffCommandsBase extends StaffEventsBase {
    StaffCommandsBase(SMPToolkitPlugin plugin) { super(plugin); }
    public boolean handle(CommandSender sender, String label, String[] args) {
        switch (label.toLowerCase(Locale.ROOT)) {
            case "staff" -> { if (sender instanceof Player p) toggleStaffMode(p); else sender.sendMessage("Players only."); return true; }
            case "freeze" -> { if (args.length<1) return usage(sender,"/freeze <player>"); Player t=Bukkit.getPlayerExact(args[0]); if(t==null)return notFound(sender); toggleFreeze(sender,t,true); return true; }
            case "unfreeze" -> { if (args.length<1) return usage(sender,"/unfreeze <player>"); resolveKnown(args[0], sender, id -> setFrozen(sender,id,args[0],false)); return true; }
            case "vanish", "v" -> { Player t = args.length>0 && sender.hasPermission("smp.admin") ? Bukkit.getPlayerExact(args[0]) : sender instanceof Player p ? p : null; if(t==null)return notFound(sender); toggleVanish(t); sender.sendMessage(Text.mm(isVanished(t.getUniqueId())?"<gray>Vanish enabled.</gray>":"<green>Vanish disabled.</green>")); return true; }
            case "invsee" -> { Player t=targetArg(sender,args); if(t!=null && sender instanceof Player p)p.openInventory(t.getInventory()); return true; }
            case "endersee", "ecsee" -> { Player t=targetArg(sender,args); if(t!=null && sender instanceof Player p)p.openInventory(t.getEnderChest()); return true; }
            case "heal" -> { Player t=targetOrSelf(sender,args); if(t!=null){var max=t.getAttribute(Attribute.MAX_HEALTH); t.setHealth(max == null ? 20.0 : max.getValue());t.setFireTicks(0);sender.sendMessage(Text.mm("<green>Healed " + Text.escapeMini(t.getName()) + ".</green>"));} return true; }
            case "feed" -> { Player t=targetOrSelf(sender,args); if(t!=null){t.setFoodLevel(20);t.setSaturation(20f);sender.sendMessage(Text.mm("<green>Fed " + Text.escapeMini(t.getName()) + ".</green>"));} return true; }
            case "fly" -> { Player t=targetOrSelf(sender,args); if(t!=null){t.setAllowFlight(!t.getAllowFlight()); if(!t.getAllowFlight())t.setFlying(false);sender.sendMessage(Text.mm("<gray>Flight " + (t.getAllowFlight()?"enabled":"disabled") + " for " + Text.escapeMini(t.getName()) + ".</gray>"));} return true; }
            case "god" -> { Player t=targetOrSelf(sender,args); if(t!=null){boolean on=!god.remove(t.getUniqueId());if(on)god.add(t.getUniqueId());sender.sendMessage(Text.mm("<gray>God mode " + (on?"enabled":"disabled") + " for " + Text.escapeMini(t.getName()) + ".</gray>"));} return true; }
            case "speed" -> { return speed(sender,args); }
            case "tpo" -> { if(!(sender instanceof Player p))return usage(sender,"Players only."); Player t=targetArg(sender,args);if(t!=null)p.teleport(t.getLocation());return true; }
            case "tpohere" -> { if(!(sender instanceof Player p))return usage(sender,"Players only."); Player t=targetArg(sender,args);if(t!=null)t.teleport(p.getLocation());return true; }
            case "playerinfo", "pinfo" -> { Player t=targetArg(sender,args); if(t!=null)showPlayerInfo(sender,t); else if(args.length>0) resolveKnown(args[0],sender,id->showPlayerInfo(sender,id,args[0])); return true; }
            case "maintenance" -> { return maintenance(sender,args); }
            case "restart" -> { return restart(sender,args); }
            case "staffgui" -> { if(!(sender instanceof Player p))return usage(sender,"Players only."); Player t=targetArg(sender,args);if(t!=null)openStaffGui(p,t);return true; }
        }
        return false;
    }
    private void toggleStaffMode(Player p) {
        boolean on = !staffMode.remove(p.getUniqueId());
        if (on) {
            staffMode.add(p.getUniqueId());
            p.setAllowFlight(true);
            if (!vanished.contains(p.getUniqueId())) toggleVanish(p);
            p.sendMessage(Text.mm("<aqua>Staff mode enabled.</aqua> <gray>Flight and vanish are active.</gray>"));
        } else {
            if (vanished.contains(p.getUniqueId())) toggleVanish(p);
            if (p.getGameMode()!=GameMode.CREATIVE && p.getGameMode()!=GameMode.SPECTATOR) { p.setFlying(false); p.setAllowFlight(false); }
            p.sendMessage(Text.mm("<gray>Staff mode disabled.</gray>"));
        }
    }
    private boolean speed(CommandSender sender,String[] args) {
        if(!(sender instanceof Player p))return usage(sender,"Players only.");
        if(args.length<1)return usage(sender,"/speed <1-10> [walk|fly]");
        try{int n=Integer.parseInt(args[0]);if(n<1||n>10)throw new NumberFormatException();float v=n/10f;String mode=args.length>1?args[1].toLowerCase(Locale.ROOT):(p.isFlying()?"fly":"walk");if(mode.equals("fly"))p.setFlySpeed(v);else p.setWalkSpeed(v);p.sendMessage(Text.mm("<green>"+mode+" speed set to "+n+".</green>"));}catch(Exception ex){sender.sendMessage(Text.mm("<red>Speed must be 1-10.</red>"));}return true;
    }
    private boolean maintenance(CommandSender sender,String[] args) {
        if(args.length<1){sender.sendMessage(Text.mm("<gray>Maintenance:</gray> <yellow>"+plugin.getConfig().getBoolean("staff.maintenance.enabled",false)+"</yellow>"));return true;}
        boolean on=switch(args[0].toLowerCase(Locale.ROOT)){case "on","true","enable"->true;case "off","false","disable"->false;default->{sender.sendMessage(Text.mm("<red>Usage: /maintenance <on|off></red>"));yield plugin.getConfig().getBoolean("staff.maintenance.enabled",false);}};
        plugin.getConfig().set("staff.maintenance.enabled",on);plugin.saveConfig();
        if(on) for(Player p:Bukkit.getOnlinePlayers())if(!p.hasPermission("smp.maintenance.bypass"))p.kick(Text.mm(plugin.getConfig().getString("staff.maintenance.kick-message","<red>Server maintenance is enabled.</red>")));
        Bukkit.broadcast(Text.mm(on?"<red>Maintenance mode enabled.</red>":"<green>Maintenance mode disabled.</green>"));return true;
    }
    private boolean restart(CommandSender sender,String[] args) {
        if(args.length>0&&args[0].equalsIgnoreCase("cancel")){if(restartTask!=-1){Bukkit.getScheduler().cancelTask(restartTask);restartTask=-1;restartRemaining=-1;Bukkit.broadcast(Text.mm("<green>Scheduled restart cancelled.</green>"));}else sender.sendMessage(Text.mm("<gray>No restart is scheduled.</gray>"));return true;}
        if(restartTask!=-1){sender.sendMessage(Text.mm("<red>A restart is already scheduled in "+restartRemaining+"s.</red>"));return true;}
        int seconds=plugin.getConfig().getInt("staff.restart.default-seconds",60);if(args.length>0)try{seconds=Math.max(1,Integer.parseInt(args[0]));}catch(NumberFormatException ex){return usage(sender,"/restart [seconds|cancel]");}
        restartRemaining=seconds;
        restartTask=Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin,()->{
            if(restartRemaining<=0){Bukkit.broadcast(Text.mm("<red><bold>Server restarting now.</bold></red>"));Bukkit.shutdown();return;}
            if(plugin.getConfig().getIntegerList("staff.restart.announce-at").contains(restartRemaining))Bukkit.broadcast(Text.mm("<yellow>Server restarting in <red>"+restartRemaining+"</red> second"+(restartRemaining==1?"":"s")+".</yellow>"));
            restartRemaining--;
        },0L,20L);return true;
    }
    private void openStaffGui(Player staff,Player target) {
        Inventory inv=Bukkit.createInventory(null,27,Text.mm("<dark_red>Staff: "+Text.escapeMini(target.getName())+"</dark_red>"));
        inv.setItem(10,item(Material.ENDER_PEARL,"<aqua>Teleport</aqua>"));
        inv.setItem(11,item(Material.ICE,"<red>Freeze / Unfreeze</red>"));
        inv.setItem(12,item(Material.CHEST,"<yellow>Inventory</yellow>"));
        inv.setItem(13,item(Material.ENDER_CHEST,"<light_purple>Ender Chest</light_purple>"));
        inv.setItem(14,item(Material.GLASS,"<gray>Vanish toggle</gray>"));
        inv.setItem(15,item(Material.BOOK,"<gold>Player info</gold>"));
        inv.setItem(16,item(Material.BARRIER,"<red>Kick</red>"));
        guiTargets.put(staff.getUniqueId(),target.getUniqueId());staff.openInventory(inv);
    }
    private ItemStack item(Material material,String name){ItemStack stack=new ItemStack(material);ItemMeta meta=stack.getItemMeta();meta.displayName(Text.mm(name));stack.setItemMeta(meta);return stack;}
    private Player targetArg(CommandSender sender,String[] args){if(args.length<1){usage(sender,"/<command> <player>");return null;}Player t=Bukkit.getPlayerExact(args[0]);if(t==null)notFound(sender);return t;}
    private Player targetOrSelf(CommandSender sender,String[] args){if(args.length>0){Player t=Bukkit.getPlayerExact(args[0]);if(t==null)notFound(sender);return t;}return sender instanceof Player p?p:null;}
    private boolean usage(CommandSender sender,String msg){sender.sendMessage(Text.mm("<red>"+Text.escapeMini(msg)+"</red>"));return true;}
    private boolean notFound(CommandSender sender){sender.sendMessage(Text.mm("<red>Player not found.</red>"));return true;}
}
