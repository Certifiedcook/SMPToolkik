package io.github.xtx.smptoolkit.platform;

import io.github.xtx.smptoolkit.SMPToolkitPlugin;
import io.github.xtx.smptoolkit.core.Text;
import io.github.xtx.smptoolkit.data.Database;
import org.bukkit.*;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.*;
import java.util.concurrent.CompletableFuture;

public final class AdminGuiService implements Listener {
    private final SMPToolkitPlugin plugin;
    private final PlatformDatabase db;
    private final ModerationGuiService moderation;
    private final CasesService cases;
    private final UndoService undo;
    private final Map<UUID,Context> contexts=new HashMap<>();

    public AdminGuiService(SMPToolkitPlugin plugin,PlatformDatabase db,ModerationGuiService moderation,CasesService cases,UndoService undo){this.plugin=plugin;this.db=db;this.moderation=moderation;this.cases=cases;this.undo=undo;}

    public boolean handle(CommandSender sender,String label,String[] args){
        if(label.equals("smpgui")){if(sender instanceof Player p)openMain(p);else sender.sendMessage("Players only.");return true;}
        if(label.equals("configgui")){if(sender instanceof Player p)openConfig(p,0);else sender.sendMessage("Players only.");return true;}
        if(label.equals("profilegui")){
            if(!(sender instanceof Player viewer)){sender.sendMessage("Players only.");return true;}
            if(args.length<1){openProfile(viewer,viewer);return true;}
            Player target=Bukkit.getPlayerExact(args[0]);if(target==null){sender.sendMessage(Text.mm("<red>Player must be online.</red>"));return true;}
            openProfile(viewer,target);return true;
        }
        return false;
    }

    public void openMain(Player player){
        Inventory inv=Bukkit.createInventory(null,54,Text.mm(plugin.getConfig().getString("platform.guis.admin.title","<dark_aqua>SMPToolkit Control Panel</dark_aqua>")));
        inv.setItem(10,item(Material.PLAYER_HEAD,"<aqua>Players</aqua>","<gray>Profiles and inspection</gray>"));
        inv.setItem(12,item(Material.ANVIL,"<red>Moderation</red>","<gray>Reports, warnings and notes</gray>"));
        inv.setItem(14,item(Material.WRITABLE_BOOK,"<gold>Cases</gold>","<gray>Moderation case files</gray>"));
        inv.setItem(16,item(Material.CLOCK,"<yellow>Events</yellow>","<gray>Event controls</gray>"));
        inv.setItem(28,item(Material.REPEATER,"<light_purple>Scheduler</light_purple>","<gray>Scheduled server jobs</gray>"));
        inv.setItem(30,item(Material.NETHER_STAR,"<gold>Season</gold>","<gray>Season status and leaderboards</gray>"));
        inv.setItem(32,item(Material.COMPARATOR,"<green>Configuration</green>","<gray>Toggle and tune common settings</gray>"));
        inv.setItem(34,item(Material.REDSTONE_TORCH,"<red>Rollback</red>","<gray>Recent reversible actions</gray>"));
        inv.setItem(49,item(Material.BARRIER,"<red>Close</red>"));
        contexts.put(player.getUniqueId(),new Context(Type.MAIN,null,null,0,List.of()));player.openInventory(inv);
    }

    private void openPlayers(Player player){
        Inventory inv=Bukkit.createInventory(null,54,Text.mm(plugin.getConfig().getString("platform.guis.players.title","<dark_aqua>Online Players</dark_aqua>")));
        List<String> ids=new ArrayList<>();int slot=0;
        for(Player target:Bukkit.getOnlinePlayers()){
            if(slot>=45)break;ids.add(target.getUniqueId().toString());
            ItemStack head=new ItemStack(Material.PLAYER_HEAD);SkullMeta meta=(SkullMeta)head.getItemMeta();meta.setOwningPlayer(target);meta.displayName(Text.mm("<yellow>"+Text.escapeMini(target.getName())+"</yellow>"));meta.lore(List.of(Text.mm("<gray>Click to open profile</gray>")));head.setItemMeta(meta);inv.setItem(slot++,head;
        }
        inv.setItem(49,item(Material.ARROW,"<yellow>Back</yellow>"));contexts.put(player.getUniqueId(),new Context(Type.PLAYERS,null,null,0,ids));player.openInventory(inv);
    }

    public void openProfile(Player viewer,Player target){
        UUID id=target.getUniqueId();var stats=plugin.database().stats(id);var history=plugin.database().history(id);var alts=plugin.database().findAlts(id);var notes=plugin.database().notes(id);var reports=db.openReportCount(id);var homes=plugin.database().homes(id);var combat=db.combatStats(id);
        CompletableFuture.allOf(stats,history,alts,notes,reports,homes,combat).thenRun(()->plugin.sync(()->{
            Database.Stats s=stats.join();List<Database.Punishment> h=history.join();long warnings=h.stream().filter(p->p.type().equals("WARN")&&p.active()).count();long activePun=h.stream().filter(Database.Punishment::active).count();PlatformDatabase.CombatStats pvp=combat.join();
            String title=plugin.getConfig().getString("platform.guis.profile.title","<gold>Profile: %player%</gold>").replace("%player%",Text.escapeMini(target.getName()));
            Inventory inv=Bukkit.createInventory(null,54,Text.mm(title));
            ItemStack head=new ItemStack(Material.PLAYER_HEAD);SkullMeta hm=(SkullMeta)head.getItemMeta();hm.setOwningPlayer(target);hm.displayName(Text.mm("<yellow>"+Text.escapeMini(target.getName())+"</yellow>"));hm.lore(List.of(Text.mm("<gray>UUID: "+id+"</gray>"),Text.mm("<gray>Playtime: <white>"+Text.formatDuration(s.playtimeMs())+"</white></gray>"),Text.mm("<gray>K/D: <white>"+String.format(Locale.ROOT,"%.2f",s.kd())+"</white></gray>")));head.setItemMeta(hm);inv.setItem(4,head);
            inv.setItem(19,item(Material.WRITABLE_BOOK,"<yellow>Warnings: "+warnings+"</yellow>","<gray>Click to open warnings</gray>"));
            inv.setItem(21,item(Material.BOOK,"<aqua>Notes: "+notes.join().size()+"</aqua>","<gray>Click to open notes</gray>"));
            inv.setItem(23,item(Material.PAPER,"<red>Open Reports: "+reports.join()+"</red>","<gray>Click to open report queue</gray>"));
            inv.setItem(25,item(Material.LEAD,"<light_purple>Possible Alts: "+alts.join().size()+"</light_purple>","<gray>Click to run alt check</gray>"));
            inv.setItem(29,item(Material.IRON_SWORD,"<red>Punishments: "+activePun+" active</red>","<gray>Click for punishment history</gray>"));
            inv.setItem(31,item(Material.CHEST,"<yellow>Homes: "+homes.join().size()+"</yellow>","<gray>Stored home count</gray>"));
            inv.setItem(33,item(Material.ENDER_CHEST,"<light_purple>Inventory</light_purple>","<gray>Click to inspect</gray>"));
            inv.setItem(35,item(Material.DIAMOND_SWORD,"<red>PvP</red>","<gray>Kills: <white>"+pvp.kills()+"</white></gray>","<gray>Longest kill: <white>"+String.format(Locale.ROOT,"%.1fm",pvp.longestKillDistance())+"</white></gray>"));
            inv.setItem(40,item(Material.ARROW,"<yellow>Back</yellow>"));contexts.put(viewer.getUniqueId(),new Context(Type.PROFILE,id,target.getName(),0,List.of()));viewer.openInventory(inv);
        }));
    }

    private void openConfig(Player player,int page){
        List<String> entries=configEntries();int start=page*45;if(start>=entries.size()&&page>0){openConfig(player,page-1);return;}
        Inventory inv=Bukkit.createInventory(null,54,Text.mm(plugin.getConfig().getString("platform.guis.config.title","<green>SMPToolkit Configuration</green>")));
        for(int i=start;i<Math.min(start+45,entries.size());i++){
            String entry=entries.get(i);String[] parts=entry.split("\\u001f");String kind=parts[0],path=parts[1];
            if(kind.equals("B")){boolean value=plugin.getConfig().getBoolean(path,true);inv.setItem(i-start,item(value?Material.LIME_WOOL:Material.RED_WOOL,(value?"<green>":"<red>")+Text.escapeMini(path)+"</"+(value?"green":"red")+">","<gray>"+value+" — click to toggle</gray>"));}
            else{double value=plugin.getConfig().getDouble(path);double step=parts.length>2?Double.parseDouble(parts[2]):1.0;inv.setItem(i-start,item(Material.REPEATER,"<yellow>"+Text.escapeMini(path)+"</yellow>","<gray>Value: <white>"+value+"</white></gray>","<gray>Left: +"+step+" | Right: -"+step+" | Shift: x10</gray>"));}
        }
        if(page>0)inv.setItem(45,item(Material.ARROW,"<yellow>Previous</yellow>"));inv.setItem(49,item(Material.ARROW,"<yellow>Back</yellow>"));if(start+45<entries.size())inv.setItem(53,item(Material.ARROW,"<yellow>Next</yellow>"));contexts.put(player.getUniqueId(),new Context(Type.CONFIG,null,null,page,entries));player.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event){
        if(!(event.getWhoClicked() instanceof Player player))return;Context ctx=contexts.get(player.getUniqueId());if(ctx==null)return;event.setCancelled(true);int slot=event.getRawSlot();
        switch(ctx.type()){
            case MAIN->{switch(slot){case 10->openPlayers(player);case 12->moderation.openReports(player,0);case 14->cases.openGui(player,null);case 16->{player.closeInventory();Bukkit.dispatchCommand(player,"event status");}case 28->{player.closeInventory();Bukkit.dispatchCommand(player,"scheduler list");}case 30->{player.closeInventory();Bukkit.dispatchCommand(player,"season status");}case 32->openConfig(player,0);case 34->{player.closeInventory();Bukkit.dispatchCommand(player,"rollback list");}case 49->player.closeInventory();}}
            case PLAYERS->{if(slot==49){openMain(player);return;}if(slot>=0&&slot<ctx.entries().size()){try{Player target=Bukkit.getPlayer(UUID.fromString(ctx.entries().get(slot)));if(target!=null)openProfile(player,target);}catch(IllegalArgumentException ignored){}}}
            case PROFILE->{if(slot==40){openPlayers(player);return;}if(ctx.target()==null)return;Player target=Bukkit.getPlayer(ctx.target());if(slot==19)moderation.openWarnings(player,ctx.target(),ctx.targetName());else if(slot==21)moderation.openNotes(player,ctx.target(),ctx.targetName());else if(slot==23)moderation.openReports(player,0);else if(slot==25){player.closeInventory();Bukkit.dispatchCommand(player,"altcheck "+ctx.targetName());}else if(slot==29){player.closeInventory();Bukkit.dispatchCommand(player,"history "+ctx.targetName());}else if(slot==33&&target!=null)player.openInventory(target.getInventory());else if(slot==35){player.closeInventory();Bukkit.dispatchCommand(player,"pvpstats "+ctx.targetName());}}
            case CONFIG->{
                if(slot==49){openMain(player);return;}if(slot==45&&ctx.page()>0){openConfig(player,ctx.page()-1);return;}if(slot==53){openConfig(player,ctx.page()+1);return;}
                int index=ctx.page()*45+slot;if(slot<0||slot>=45||index>=ctx.entries().size())return;String entry=ctx.entries().get(index);String[] parts=entry.split("\\u001f");String kind=parts[0],path=parts[1];
                if(kind.equals("B")){boolean old=plugin.getConfig().getBoolean(path,true);plugin.getConfig().set(path,!old);plugin.saveConfig();undo.record(player,"CONFIG_BOOL",path+"\u001f"+old);}
                else{double old=plugin.getConfig().getDouble(path),step=parts.length>2?Double.parseDouble(parts[2]):1.0;if(event.isShiftClick())step*=10;double next=event.isRightClick()?old-step:old+step;plugin.getConfig().set(path,next);plugin.saveConfig();}
                openConfig(player,ctx.page());
            }
        }
    }

    private List<String> configEntries(){
        List<String> out=new ArrayList<>();for(String path:plugin.getConfig().getStringList("platform.config-gui.toggles"))out.add("B\u001f"+path);
        if(plugin.getConfig().getBoolean("platform.config-gui.include-command-toggles",true)){var sec=plugin.getConfig().getConfigurationSection("features.commands");if(sec!=null)for(String key:sec.getKeys(false))out.add("B\u001ffeatures.commands."+key);}
        for(String raw:plugin.getConfig().getStringList("platform.config-gui.numeric")){String[] p=raw.split(":",2);String path=p[0];String step=p.length>1?p[1]:"1";try{Double.parseDouble(step);out.add("N\u001f"+path+"\u001f"+step);}catch(NumberFormatException ignored){}}
        return out.stream().distinct().sorted().toList();
    }
    private ItemStack item(Material material,String name,String... lore){ItemStack stack=new ItemStack(material);ItemMeta meta=stack.getItemMeta();meta.displayName(Text.mm(name));meta.lore(Arrays.stream(lore).map(Text::mm).toList());stack.setItemMeta(meta);return stack;}
    private enum Type{MAIN,PLAYERS,PROFILE,CONFIG}
    private record Context(Type type,UUID target,String targetName,int page,List<String> entries){}
}
