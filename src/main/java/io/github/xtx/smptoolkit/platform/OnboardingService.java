package io.github.xtx.smptoolkit.platform;

import io.github.xtx.smptoolkit.SMPToolkitPlugin;
import io.github.xtx.smptoolkit.core.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class OnboardingService implements Listener {
    private final SMPToolkitPlugin plugin;
    private final PlatformDatabase db;
    private final Set<UUID> pending=ConcurrentHashMap.newKeySet();
    public OnboardingService(SMPToolkitPlugin plugin,PlatformDatabase db){this.plugin=plugin;this.db=db;}

    @EventHandler
    public void onJoin(PlayerJoinEvent event){
        if(!plugin.featureEnabled("onboarding"))return;
        Player player=event.getPlayer();
        if(!player.hasPlayedBefore()){
            int delay=Math.max(1,plugin.getConfig().getInt("platform.onboarding.delay-ticks",40));
            Bukkit.getScheduler().runTaskLater(plugin,()->welcome(player),delay);
        }
        if(plugin.getConfig().getBoolean("platform.onboarding.require-rules-accept",false)){
            db.rulesAccepted(player.getUniqueId()).thenAccept(ok->{
                if(ok){pending.remove(player.getUniqueId());return;}
                pending.add(player.getUniqueId());
                if(plugin.getConfig().getBoolean("platform.onboarding.gui-enabled",true))plugin.sync(()->{if(player.isOnline()&&pending.contains(player.getUniqueId()))openGui(player);});
            });
        }
    }

    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void onCommand(PlayerCommandPreprocessEvent event){
        if(!pending.contains(event.getPlayer().getUniqueId())||!plugin.getConfig().getBoolean("platform.onboarding.require-rules-accept",false))return;
        String cmd=event.getMessage().substring(1).split("\\s+",2)[0].toLowerCase(Locale.ROOT);
        if(plugin.getConfig().getStringList("platform.onboarding.allowed-before-accept").stream().anyMatch(s->s.equalsIgnoreCase(cmd)))return;
        event.setCancelled(true);plugin.messages().send(event.getPlayer(),"onboarding.accept-rules");
    }

    @EventHandler
    public void onGui(InventoryClickEvent event){
        if(!(event.getWhoClicked() instanceof Player player)||!Text.plain(event.getView().title()).equalsIgnoreCase("SMP Welcome"))return;
        event.setCancelled(true);
        if(event.getRawSlot()==11){player.closeInventory();Bukkit.dispatchCommand(player,"rules");}
        else if(event.getRawSlot()==15){accept(player);player.closeInventory();}
    }

    public boolean handle(CommandSender sender,String label,String[] args){
        if(label.equals("rulesaccept")){if(sender instanceof Player p)accept(p);else sender.sendMessage("Players only.");return true;}
        if(!label.equals("onboarding"))return false;
        if(args.length<2||!args[0].equalsIgnoreCase("reset")){sender.sendMessage(Text.mm("<red>/onboarding reset <player></red>"));return true;}
        Player target=Bukkit.getPlayerExact(args[1]);if(target==null){sender.sendMessage(Text.mm("<red>Player must be online.</red>"));return true;}
        db.setRulesAccepted(target.getUniqueId(),false).thenRun(()->plugin.sync(()->{pending.add(target.getUniqueId());sender.sendMessage(Text.mm("<green>Reset onboarding for "+Text.escapeMini(target.getName())+".</green>"));if(plugin.getConfig().getBoolean("platform.onboarding.gui-enabled",true))openGui(target);}));return true;
    }

    private void welcome(Player player){
        if(!player.isOnline())return;
        plugin.messages().send(player,"onboarding.welcome","player",player.getName());
        for(String line:plugin.getConfig().getStringList("platform.onboarding.messages"))player.sendMessage(Text.mm(line.replace("%player%",Text.escapeMini(player.getName()))));
        for(String command:plugin.getConfig().getStringList("platform.onboarding.starter-commands"))Bukkit.dispatchCommand(Bukkit.getConsoleSender(),command.replace("%player%",player.getName()));
        if(plugin.getConfig().getBoolean("platform.onboarding.require-rules-accept",false)){plugin.messages().send(player,"onboarding.accept-rules");if(plugin.getConfig().getBoolean("platform.onboarding.gui-enabled",true)&&pending.contains(player.getUniqueId()))openGui(player);}
    }

    private void accept(Player player){
        db.setRulesAccepted(player.getUniqueId(),true).thenRun(()->plugin.sync(()->{pending.remove(player.getUniqueId());player.sendMessage(Text.mm("<green>Rules accepted. Welcome.</green>"));}));
    }

    private void openGui(Player player){Inventory inv=Bukkit.createInventory(null,27,Text.mm("<gold>SMP Welcome</gold>"));inv.setItem(11,item(Material.WRITABLE_BOOK,"<yellow>View Rules</yellow>"));inv.setItem(15,item(Material.LIME_WOOL,"<green>Accept Rules</green>"));player.openInventory(inv);}
    private ItemStack item(Material material,String name){ItemStack stack=new ItemStack(material);ItemMeta meta=stack.getItemMeta();meta.displayName(Text.mm(name));stack.setItemMeta(meta);return stack;}
}
