package io.github.xtx.smptoolkit.platform;

import io.github.xtx.smptoolkit.SMPToolkitPlugin;
import io.github.xtx.smptoolkit.api.events.SMPBountyClaimEvent;
import io.github.xtx.smptoolkit.core.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;

import java.util.*;

public final class BountyService implements Listener {
    private final SMPToolkitPlugin plugin;
    private final PlatformDatabase db;
    public BountyService(SMPToolkitPlugin plugin,PlatformDatabase db){this.plugin=plugin;this.db=db;}

    public boolean handle(CommandSender sender,String label,String[] args){
        if(label.equals("bounties")){list(sender);return true;}
        if(!label.equals("bounty"))return false;
        if(!(sender instanceof Player player)){sender.sendMessage(Text.mm("<red>Players only.</red>"));return true;}
        if(args.length==0){player.sendMessage(Text.mm("<yellow>/bounty add <player> <amount> | cancel <id> | list</yellow>"));return true;}
        switch(args[0].toLowerCase(Locale.ROOT)){
            case "add"->{
                if(args.length<3)return usage(player,"/bounty add <player> <amount>");
                Player target=Bukkit.getPlayerExact(args[1]);if(target==null||target.equals(player)){player.sendMessage(Text.mm("<red>Target must be another online player.</red>"));return true;}
                int amount;try{amount=Integer.parseInt(args[2]);}catch(NumberFormatException ex){return usage(player,"Amount must be a number.");}
                ItemStack held=player.getInventory().getItemInMainHand();if(held.getType().isAir()||amount<1||held.getAmount()<amount){player.sendMessage(Text.mm("<red>Hold at least that many reward items in your main hand.</red>"));return true;}
                if(!plugin.getConfig().getBoolean("platform.bounties.allow-enchanted-items",false)&&held.hasItemMeta()){player.sendMessage(Text.mm("<red>Bounties currently accept plain items only.</red>"));return true;}
                Material material=held.getType();held.setAmount(held.getAmount()-amount);
                db.createBounty(target.getUniqueId(),target.getName(),player.getUniqueId(),player.getName(),material.name(),amount).thenAccept(id->plugin.sync(()->plugin.messages().send(player,"bounties.created","id",id,"player",target.getName())));return true;
            }
            case "cancel"->{
                if(args.length<2)return usage(player,"/bounty cancel <id>");int id;try{id=Integer.parseInt(args[1]);}catch(NumberFormatException ex){return usage(player,"ID must be a number.");}
                db.cancelBounty(id,player.getUniqueId(),player.hasPermission("smp.bounties.admin")).thenAccept(opt->plugin.sync(()->{
                    if(opt.isEmpty()){player.sendMessage(Text.mm("<red>Active bounty not found or not owned by you.</red>"));return;}
                    refund(player,opt.get());player.sendMessage(Text.mm("<green>Bounty #"+id+" cancelled and refunded.</green>"));
                }));return true;
            }
            case "list"->{list(player);return true;}
            default->{return usage(player,"/bounty add <player> <amount> | cancel <id> | list");}
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event){
        if(!plugin.featureEnabled("bounties"))return;
        Player victim=event.getEntity(),killer=victim.getKiller();if(killer==null||killer.equals(victim))return;
        db.activeBounties(victim.getUniqueId()).thenAccept(rows->{for(PlatformDatabase.BountyRow row:rows)db.claimBounty(row.id(),killer.getUniqueId(),killer.getName()).thenAccept(ok->{if(!ok)return;plugin.sync(()->{
            Material material=Material.matchMaterial(row.material());if(material!=null)give(killer,new ItemStack(material,row.amount()));
            plugin.messages().send(Bukkit.getConsoleSender(),"bounties.claimed","killer",killer.getName(),"target",victim.getName());
            Bukkit.broadcast(plugin.messages().get(killer,"bounties.claimed","killer",killer.getName(),"target",victim.getName()));
            Bukkit.getPluginManager().callEvent(new SMPBountyClaimEvent(row.id(),killer.getUniqueId(),victim.getUniqueId()));
        });});});
    }

    private void list(CommandSender sender){db.activeBounties(null).thenAccept(rows->plugin.sync(()->{sender.sendMessage(Text.mm("<gold><bold>Active Bounties</bold></gold>"));if(rows.isEmpty()){sender.sendMessage(Text.mm("<gray>No active bounties.</gray>"));return;}for(PlatformDatabase.BountyRow b:rows.stream().limit(30).toList())sender.sendMessage(Text.mm("<gray>#"+b.id()+"</gray> <yellow>"+Text.escapeMini(b.targetName())+"</yellow> <white>"+b.amount()+"x "+Text.escapeMini(b.material())+"</white> <dark_gray>by "+Text.escapeMini(b.creatorName())+"</dark_gray>"));}));}
    private void refund(Player player,PlatformDatabase.BountyRow row){Material material=Material.matchMaterial(row.material());if(material!=null)give(player,new ItemStack(material,row.amount()));}
    private void give(Player player,ItemStack stack){Map<Integer,ItemStack> left=player.getInventory().addItem(stack);for(ItemStack item:left.values())player.getWorld().dropItemNaturally(player.getLocation(),item);}
    private boolean usage(CommandSender sender,String text){sender.sendMessage(Text.mm("<red>"+Text.escapeMini(text)+"</red>"));return true;}
}
