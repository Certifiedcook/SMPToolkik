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
        if(label.equals("profilegui")){if(!(sender instanceof Player viewer)){sender.sendMessage("Players only.");return true;}if(args.length<1){openProfile(viewer,viewer);return true;}Player target=Bukkit.getPlayerExact(args[0]);if(target==null){sender.sendMessage(Text.mm("<red>Player must be online.</red>"));return true;}openProfile(viewer,target);return true;}
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
        inv.setItem(32,item(Material.COMPARATOR,"<green>Configuration</green>","<gray>Toggle configured features</gray>"));
        inv.setItem(34,item(Material.REDSTONE_TORCH,"<red>Rollback</red>","<gray>Recent reversible actions</gray>"));
        inv.setItem(49,item(Material.BARRIER,"<red>Close</red>"));
        contexts.put(player.getUniqueId(),new Context(Type.MAIN,null,null,0,List.of()));player.openInventory(inv);
    }

    private void openPlayers(Player player){
        Inventory inv=Bukkit.createInventory(null,54,Text.mm("<dark_aqua>Online Players</dark_aqua>"));List<UUID> ids=new ArrayList<>();int slot=0;for(Player target:Bukkit.getOnlinePlayers()){if(slot>=45)break;ids.add(target.getUniqueId());ItemStack head=new ItemStack(Material.PLAYER_HEAD);SkullMeta meta=(SkullMeta)head.getItemMeta();meta.setOwningPlayer(target);meta.displayName(Text.mm("<yellow>"+Text.escapeMini(target.getName())+"</yellow>"));meta.lore(List.of(Text.mm("<gray>Click to open profile</gray>")));head.setItemMeta(meta);inv.setItem(slot++,head);}inv.setItem(49,item(Material.ARROW,"<yellow>Back</yellow>"));contexts.put(player.getUniqueId(),new Context(Type.PLAYERS,null,null,0,ids));player.openInventory(inv);
    }

    public void openProfile(Player viewer,Player target){
        UUID id=target.getUniqueId();var stats=plugin.database().stats(id);var history=plugin.database().history(id);var alts=plugin.database().findAlts(id);var notes=plugin.database().notes(id);var reports=db.openReportCount(id);var homes=plugin.database().homes(id);
        CompletableFuture.allOf(stats,history,alts,notes,reports,homes).thenRun(()->plugin.sync(()->{
            Database.Stats s=stats.join();List<Database.Punishment> h=history.join();long warnings=h.stream().filter(p->p.type().equals("WARN")&&p.active()).count();long activePun=h.stream().filter(Database.Punishment::active).count();
            Inventory inv=Bukkit.createInventory(null,54,Text.mm("<gold>Profile: "+Text.escapeMini(target.getName())+"</gold>"));
            ItemStack head=new ItemStack(Material.PLAYER_HEAD);SkullMeta hm=(SkullMeta)head.getItemMeta();hm.setOwningPlayer(target);hm.displayName(Text.mm("<yellow>"+Text.escapeMini(target.getName())+"</yellow>"));hm.lore(List.of(Text.mm("<gray>UUID: "+id+"</gray>"),Text.mm("<gray>Playtime: <white>"+Text.formatDuration(s.playtimeMs())+"</white></gray>"),Text.mm("<gray>K/D: <white>"+String.format(Locale.ROOT,"%.2f",s.kd())+"</white></gray>")));head.setItemMeta(hm);inv.setItem(4,head);
            inv.setItem(19,item(Material.WRITABLE_BOOK,"<yellow>Warnings: "+warnings+"</yellow>","<gray>Click to open warnings</gray>"));
            inv.setItem(21,item(Material.BOOK,"<aqua>Notes: "+notes.join().size()+"</aqua>","<gray>Click to open notes</gray>"));
            inv.setItem(23,item(Material.PAPER,"<red>Open Reports: "+reports.join()+"</red>","<gray>Click to open report queue</gray>"));
            inv.setItem(25,item(Material.CHAIN,"<light_purple>Possible Alts: "+alts.join().size()+"</light_purple>","<gray>/altcheck "+Text.escapeMini(target.getName())+"</gray>"));
            inv.setItem(29,item(Material.IRON_SWORD,"<red>Punishments: "+activePun+" active</red>","<gray>/history "+Text.escapeMini(target.getName())+"</gray>"));
            inv.setItem(31,item(Material.CHEST,"<yellow>Homes: "+homes.join().size()+"</yellow>","<gray>Stored home count</gray>"));
            inv.setItem(33,item(Material.ENDER_CHEST,"<light_purple>Inventory</light_purple>","<gray>Click to inspect</gray>"));
            inv.setItem(40,item(Material.ARROW,"<yellow>Back</yellow>"));
            contexts.put(viewer.getUniqueId(),new Context(Type.PROFILE,id,target.getName(),0,List.of()));viewer.openInventory(inv);
        }));
    }

    private void openConfig(Player player,int page){
        List<String> paths=configPaths();int start=page*45;if(start>=paths.size()&&page>0){openConfig(player,page-1);return;}
        Inventory inv=Bukkit.createInventory(null,54,Text.mm("<green>SMPToolkit Configuration</green>"));for(int i=start;i<Math.min(start+45,paths.size());i++){String path=paths.get(i);boolean value=plugin.getConfig().getBoolean(path,true);inv.setItem(i-start,item(value?Material.LIME_WOOL:Material.RED_WOOL,(value?"<green>":"<red>")+Text.escapeMini(path)+"</"+(value?"green":"red")+">","<gray>"+value+" — click to toggle</gray>"));}if(page>0)inv.setItem(45,item(Material.ARROW,"<yellow>Previous</yellow>"));inv.setItem(49,item(Material.ARROW,"<yellow>Back</yellow>"));if(start+45<paths.size())inv.setItem(53,item(Material.ARROW,"<yellow>Next</yellow>"));contexts.put(player.getUniqueId(),new Context(Type.CONFIG,null,null,page,paths));player.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event){
        if(!(event.getWhoClicked() instanceof Player player))return;Context ctx=contexts.get(player.getUniqueId());if(ctx==null)return;event.setCancelled(true);int slot=event.getRawSlot();
        switch(ctx.type()){
            case MAIN->{switch(slot){case 10->openPlayers(player);case 12->moderation.openReports(player,0);case 14->cases.openGui(player,null);case 16->{player.closeInventory();Bukkit.dispatchCommand(player,"event status");}case 28->{player.closeInventory();Bukkit.dispatchCommand(player,"scheduler list");}case 30->{player.closeInventory();Bukkit.dispatchCommand(player,"season status");}case 32->openConfig(player,0);case 34->{player.closeInventory();Bukkit.dispatchCommand(player,"rollback list");}case 49->player.closeInventory();}}
            case PLAYERS->{if(slot==49){openMain(player);return;}if(slot>=0&&slot<ctx.ids().size()){Player target=Bukkit.getPlayer(ctx.ids().get(slot));if(target!=null)openProfile(player,target);}}
            case PROFILE->{if(slot==40){openPlayers(player);return;}if(ctx.target()==null)return;Player target=Bukkit.getPlayer(ctx.target());if(slot==19)moderation.openWarnings(player,ctx.target(),ctx.targetName());else if(slot==21)moderation.openNotes(player,ctx.target(),ctx.targetName());else if(slot==23)moderation.openReports(player,0);else if(slot==25){player.closeInventory();Bukkit.dispatchCommand(player,"altcheck "+ctx.targetName());}else if(slot==29){player.closeInventory();Bukkit.dispatchCommand(player,"history "+ctx.targetName());}else if(slot==33&&target!=null)player.openInventory(target.getInventory());}
            case CONFIG->{if(slot==49){openMain(player);return;}if(slot==45&&ctx.page()>0){openConfig(player,ctx.page()-1);return;}if(slot==53){openConfig(player,ctx.page()+1);return;}int index=ctx.page()*45+slot;if(slot>=0&&slot<45&&index<ctx.ids().size()){String path=ctx.ids().get(index).toString();boolean old=plugin.getConfig().getBoolean(path,true);plugin.getConfig().set(path,!old);plugin.saveConfig();undo.record(player,"CONFIG_BOOL",path+"\u001f"+old);openConfig(player,ctx.page());}}
        }
    }

    private List<String> configPaths(){List<String> out=new ArrayList<>(plugin.getConfig().getStringList("platform.config-gui.toggles"));if(plugin.getConfig().getBoolean("platform.config-gui.include-command-toggles",true)){var sec=plugin.getConfig().getConfigurationSection("features.commands");if(sec!=null)for(String key:sec.getKeys(false))out.add("features.commands."+key);}return out.stream().distinct().sorted().toList();}
    private ItemStack item(Material material,String name,String... lore){ItemStack stack=new ItemStack(material);ItemMeta meta=stack.getItemMeta();meta.displayName(Text.mm(name));meta.lore(Arrays.stream(lore).map(Text::mm).toList());stack.setItemMeta(meta);return stack;}
    private enum Type{MAIN,PLAYERS,PROFILE,CONFIG}
    private record Context(Type type,UUID target,String targetName,int page,List<?> ids){}
}
