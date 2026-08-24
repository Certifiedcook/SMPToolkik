package io.github.xtx.smptoolkit.platform;

import io.github.xtx.smptoolkit.SMPToolkitPlugin;
import io.github.xtx.smptoolkit.core.Text;
import io.github.xtx.smptoolkit.data.Database;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

public final class ModerationGuiService implements Listener {
    private final SMPToolkitPlugin plugin;
    private final PlatformDatabase db;
    private final UndoService undo;
    private final Map<UUID,GuiContext> contexts=new HashMap<>();

    public ModerationGuiService(SMPToolkitPlugin plugin,PlatformDatabase db,UndoService undo){this.plugin=plugin;this.db=db;this.undo=undo;}

    public boolean handle(CommandSender sender,String label,String[] args){
        switch(label){
            case "removewarn"->{if(args.length<1)return usage(sender,"/removewarn <warning-id>");Integer id=parse(sender,args[0]);if(id!=null)removeWarn(sender,id);return true;}
            case "notes"->{if(args.length<1)return usage(sender,"/notes <player>");if(!(sender instanceof Player viewer))return usage(sender,"Players only for the notes GUI. Use /note list <player> from console.");resolve(args[0],sender,(uuid,name)->openNotes(viewer,uuid,name));return true;}
            case "pardon"->{if(args.length<1)return usage(sender,"/pardon <punishment-id>");Integer id=parse(sender,args[0]);if(id!=null)pardon(sender,id);return true;}
            case "punish"->{return punishTemplate(sender,args);}
        }
        return false;
    }

    public void openReports(Player viewer,int page){
        plugin.database().openReports().thenAccept(rows->plugin.sync(()->{
            List<Integer> ids=rows.stream().map(Database.ReportRecord::id).toList();
            Inventory inv=baseInventory("platform.guis.reports.title","<dark_red>Reports</dark_red>");
            int start=Math.max(0,page)*45;
            for(int i=start;i<Math.min(start+45,rows.size());i++){
                Database.ReportRecord r=rows.get(i);
                inv.setItem(i-start,item(material("platform.guis.reports.material",Material.PAPER),"<red>Report #"+r.id()+"</red>",List.of("<gray>Reporter: <white>"+Text.escapeMini(r.reporterName())+"</white></gray>","<gray>Target: <yellow>"+Text.escapeMini(r.targetName())+"</yellow></gray>","<white>"+Text.escapeMini(r.reason())+"</white>","<gray>Status: <yellow>"+r.status()+"</yellow></gray>","<dark_gray>Left: view | Right: claim | Shift: close</dark_gray>")));
            }
            nav(inv,page,rows.size());contexts.put(viewer.getUniqueId(),new GuiContext(GuiType.REPORTS,null,null,page,ids));viewer.openInventory(inv);
        }));
    }

    public void openWarnings(Player viewer,UUID target,String name){
        db.warnings(target).thenAccept(rows->plugin.sync(()->{
            Inventory inv=baseInventory("platform.guis.warnings.title","<gold>Warnings: %player%</gold>".replace("%player%",Text.escapeMini(name)));
            List<Integer> ids=rows.stream().map(PlatformDatabase.PunishmentRow::id).toList();
            int slot=0;for(PlatformDatabase.PunishmentRow r:rows){if(slot>=45)break;inv.setItem(slot++,item(material("platform.guis.warnings.material",Material.WRITABLE_BOOK),"<yellow>Warning #"+r.id()+"</yellow>",List.of("<white>"+Text.escapeMini(r.reason())+"</white>","<gray>By: <white>"+Text.escapeMini(r.staffName())+"</white></gray>",r.active()?"<green>Active</green>":"<dark_gray>Inactive</dark_gray>","<dark_gray>Click an active warning to remove it.</dark_gray>")));}
            contexts.put(viewer.getUniqueId(),new GuiContext(GuiType.WARNINGS,target,name,0,ids));viewer.openInventory(inv);
        }));
    }

    public void openNotes(Player viewer,UUID target,String name){
        plugin.database().notes(target).thenAccept(rows->plugin.sync(()->{
            Inventory inv=baseInventory("platform.guis.notes.title","<gold>Notes: %player%</gold>".replace("%player%",Text.escapeMini(name)));
            List<Integer> ids=rows.stream().map(Database.Note::id).toList();
            int slot=0;for(Database.Note n:rows){if(slot>=45)break;inv.setItem(slot++,item(material("platform.guis.notes.material",Material.BOOK),"<aqua>Note #"+n.id()+"</aqua>",List.of("<white>"+Text.escapeMini(n.note())+"</white>","<gray>By: <white>"+Text.escapeMini(n.staffName())+"</white></gray>")));}
            contexts.put(viewer.getUniqueId(),new GuiContext(GuiType.NOTES,target,name,0,ids));viewer.openInventory(inv);
        }));
    }

    @EventHandler
    public void onClick(InventoryClickEvent event){
        if(!(event.getWhoClicked() instanceof Player player))return;
        GuiContext ctx=contexts.get(player.getUniqueId());if(ctx==null)return;
        event.setCancelled(true);
        int raw=event.getRawSlot();
        if(raw==49){player.closeInventory();contexts.remove(player.getUniqueId());return;}
        if(raw==45&&ctx.page()>0&&ctx.type()==GuiType.REPORTS){openReports(player,ctx.page()-1);return;}
        if(raw==53&&ctx.type()==GuiType.REPORTS){openReports(player,ctx.page()+1);return;}
        int index=ctx.type()==GuiType.REPORTS?ctx.page()*45+raw:raw;
        if(raw<0||raw>=45||index<0||index>=ctx.ids().size())return;
        int id=ctx.ids().get(index);
        switch(ctx.type()){
            case WARNINGS->{removeWarn(player,id);Bukkit.getScheduler().runTaskLater(plugin,()->openWarnings(player,ctx.target(),ctx.targetName()),2L);}
            case NOTES->{}
            case REPORTS->{
                if(event.isShiftClick()){closeReport(player,id);Bukkit.getScheduler().runTaskLater(plugin,()->openReports(player,ctx.page()),2L);}
                else if(event.isRightClick()){plugin.database().claimReport(id,player.getUniqueId(),player.getName()).thenAccept(ok->plugin.sync(()->{player.sendMessage(Text.mm(ok?"<green>Report #"+id+" claimed.</green>":"<red>Report could not be claimed.</red>"));if(ok)db.logStaff(player.getUniqueId(),player.getName(),"REPORT","claimed #"+id);openReports(player,ctx.page());}));}
                else{player.closeInventory();plugin.database().report(id).thenAccept(opt->plugin.sync(()->{if(opt.isEmpty())return;Database.ReportRecord r=opt.get();player.sendMessage(Text.mm("<gold><bold>Report #"+id+"</bold></gold>"));player.sendMessage(Text.mm("<gray>Reporter:</gray> <white>"+Text.escapeMini(r.reporterName())+"</white> <gray>Target:</gray> <yellow>"+Text.escapeMini(r.targetName())+"</yellow>"));player.sendMessage(Text.mm("<gray>Reason:</gray> <white>"+Text.escapeMini(r.reason())+"</white>"));}));}
            }
        }
    }

    private boolean punishTemplate(CommandSender sender,String[] args){
        if(args.length<2)return usage(sender,"/punish <player> <template> [reason override]");
        ConfigurationSection section=plugin.getConfig().getConfigurationSection("platform.moderation.templates."+args[1].toLowerCase(Locale.ROOT));
        if(section==null)return usage(sender,"Unknown punishment template.");
        resolve(args[0],sender,(uuid,name)->{
            String type=section.getString("type","WARN").toUpperCase(Locale.ROOT);
            long duration=Text.parseDurationMillis(section.getString("duration",""));
            Long expires=duration>0?System.currentTimeMillis()+duration:null;
            String reason=args.length>2?Text.join(args,2):section.getString("reason","No reason supplied");
            boolean silent=section.getBoolean("silent",false);
            UUID staff=sender instanceof Player p?p.getUniqueId():null;
            plugin.database().addPunishment(uuid,type,reason,staff,sender.getName(),expires).thenAccept(id->plugin.sync(()->{
                sender.sendMessage(Text.mm("<green>"+type+" #"+id+" issued to "+Text.escapeMini(name)+" using template "+Text.escapeMini(args[1])+".</green>"));
                db.logStaff(staff,sender.getName(),"PUNISHMENT",type+" #"+id+" to "+name);
                Player target=Bukkit.getPlayer(uuid);
                if(target!=null&&!silent){if(type.equals("WARN"))target.sendMessage(Text.mm("<yellow><bold>Warning #"+id+"</bold></yellow> <gray>"+Text.escapeMini(reason)+"</gray>"));if(type.equals("MUTE"))target.sendMessage(Text.mm("<red>You have been muted.</red> <gray>"+Text.escapeMini(reason)+"</gray>"));if(type.equals("BAN"))target.kick(Text.mm("<red><bold>You are banned.</bold></red>\n<gray>Reason: "+Text.escapeMini(reason)+"</gray>"));}
            }));
        });return true;
    }

    public void afterWarning(UUID uuid,String name,int warningId){
        db.warnings(uuid).thenAccept(rows->{long count=rows.stream().filter(PlatformDatabase.PunishmentRow::active).count();String command=plugin.getConfig().getString("platform.moderation.warning-escalation."+count);if(command!=null&&!command.isBlank())plugin.sync(()->Bukkit.dispatchCommand(Bukkit.getConsoleSender(),command.replace("%player%",name).replace("%warning_id%",Integer.toString(warningId)).replace("%count%",Long.toString(count))));});
    }

    private void removeWarn(CommandSender sender,int id){db.removeWarning(id).thenAccept(ok->plugin.sync(()->{if(ok){undo.record(sender,"WARNING_REMOVE",Integer.toString(id));plugin.messages().send(sender,"moderation.warning-removed","id",id);}else plugin.messages().send(sender,"moderation.warning-not-found","id",id);}));}
    private void pardon(CommandSender sender,int id){db.pardonPunishment(id).thenAccept(ok->plugin.sync(()->{if(ok){undo.record(sender,"PUNISHMENT_PARDON",Integer.toString(id));plugin.messages().send(sender,"moderation.punishment-pardoned","id",id);}else sender.sendMessage(Text.mm("<red>Active punishment not found.</red>"));}));}
    private void closeReport(Player sender,int id){plugin.database().closeReport(id,sender.getUniqueId(),sender.getName(),"Closed from reports GUI").thenAccept(ok->plugin.sync(()->{if(ok){undo.record(sender,"REPORT_CLOSE",Integer.toString(id));db.logStaff(sender.getUniqueId(),sender.getName(),"REPORT","closed #"+id);plugin.messages().send(sender,"moderation.report-closed","id",id);}else sender.sendMessage(Text.mm("<red>Report could not be closed.</red>"));}));}

    private Inventory baseInventory(String path,String fallback){String title=plugin.getConfig().getString(path,fallback);return Bukkit.createInventory(null,54,Text.mm(title));}
    private void nav(Inventory inv,int page,int total){if(page>0)inv.setItem(45,item(Material.ARROW,"<yellow>Previous</yellow>",List.of()));inv.setItem(49,item(Material.BARRIER,"<red>Close</red>",List.of()));if((page+1)*45<total)inv.setItem(53,item(Material.ARROW,"<yellow>Next</yellow>",List.of()));}
    private Material material(String path,Material fallback){Material m=Material.matchMaterial(plugin.getConfig().getString(path,fallback.name()));return m==null?fallback:m;}
    private ItemStack item(Material material,String name,List<String> lore){ItemStack stack=new ItemStack(material);ItemMeta meta=stack.getItemMeta();meta.displayName(Text.mm(name));meta.lore(lore.stream().map(Text::mm).toList());stack.setItemMeta(meta);return stack;}
    private Integer parse(CommandSender sender,String raw){try{return Integer.parseInt(raw);}catch(NumberFormatException ex){usage(sender,"ID must be a number.");return null;}}
    private boolean usage(CommandSender sender,String message){sender.sendMessage(Text.mm("<red>"+Text.escapeMini(message)+"</red>"));return true;}
    private void resolve(String name,CommandSender sender,java.util.function.BiConsumer<UUID,String> found){Player online=Bukkit.getPlayerExact(name);if(online!=null){found.accept(online.getUniqueId(),online.getName());return;}plugin.database().findPlayerByName(name).thenAccept(opt->plugin.sync(()->{if(opt.isEmpty())plugin.messages().send(sender,"general.player-not-found");else found.accept(opt.get().uuid(),opt.get().name());}));}
    private enum GuiType{REPORTS,WARNINGS,NOTES}
    private record GuiContext(GuiType type,UUID target,String targetName,int page,List<Integer> ids){}
}
