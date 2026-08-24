package io.github.xtx.smptoolkit.platform;

import io.github.xtx.smptoolkit.SMPToolkitPlugin;
import io.github.xtx.smptoolkit.api.events.SMPCaseUpdateEvent;
import io.github.xtx.smptoolkit.core.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

public final class CasesService implements Listener {
    private final SMPToolkitPlugin plugin;
    private final PlatformDatabase db;
    private final UndoService undo;
    private final Map<UUID,List<Integer>> openGuiRows=new HashMap<>();

    public CasesService(SMPToolkitPlugin plugin,PlatformDatabase db,UndoService undo){this.plugin=plugin;this.db=db;this.undo=undo;}

    public boolean handle(CommandSender sender,String label,String[] args){
        if(label.equals("cases")){
            if(sender instanceof Player p){openGui(p,null);return true;}
            list(sender,null);return true;
        }
        if(!label.equals("case"))return false;
        if(args.length==0){sender.sendMessage(Text.mm("<yellow>/case create <player> <title> | view <id> | claim <id> | close <id> | reopen <id> | comment <id> <text> | evidence <id> <url> | link <id> <report|punishment|note> <record-id></yellow>"));return true;}
        String sub=args[0].toLowerCase(Locale.ROOT);
        switch(sub){
            case "create"->{
                if(args.length<3)return usage(sender,"/case create <player> <title>");
                resolve(args[1],sender,(id,name)->{
                    UUID staff=sender instanceof Player p?p.getUniqueId():null;
                    db.createCase(id,name,Text.join(args,2),staff,sender.getName()).thenAccept(caseId->plugin.sync(()->{
                        plugin.messages().send(sender,"cases.created","id",caseId,"player",name);
                        db.logStaff(staff,sender.getName(),"CASE","created #"+caseId+" for "+name);
                        Bukkit.getPluginManager().callEvent(new SMPCaseUpdateEvent(caseId,"CREATED"));
                    }));
                });return true;
            }
            case "view"->{if(args.length<2)return usage(sender,"/case view <id>");Integer id=parseId(sender,args[1]);if(id!=null)view(sender,id);return true;}
            case "claim"->{if(args.length<2||!(sender instanceof Player p))return usage(sender,"/case claim <id>");Integer id=parseId(sender,args[1]);if(id!=null)db.claimCase(id,p.getUniqueId(),p.getName()).thenAccept(ok->plugin.sync(()->{if(ok){plugin.messages().send(sender,"cases.claimed","id",id);db.logStaff(p.getUniqueId(),p.getName(),"CASE","claimed #"+id);Bukkit.getPluginManager().callEvent(new SMPCaseUpdateEvent(id,"CLAIMED"));}else sender.sendMessage(Text.mm("<red>Case could not be claimed.</red>"));}));return true;}
            case "close"->{if(args.length<2)return usage(sender,"/case close <id>");Integer id=parseId(sender,args[1]);if(id!=null)close(sender,id);return true;}
            case "reopen"->{if(args.length<2)return usage(sender,"/case reopen <id>");Integer id=parseId(sender,args[1]);if(id!=null)db.reopenCase(id).thenAccept(ok->plugin.sync(()->{if(ok){plugin.messages().send(sender,"cases.reopened","id",id);Bukkit.getPluginManager().callEvent(new SMPCaseUpdateEvent(id,"REOPENED"));}else sender.sendMessage(Text.mm("<red>Case not found.</red>"));}));return true;}
            case "comment","evidence"->{if(args.length<3)return usage(sender,"/case "+sub+" <id> <text>");Integer id=parseId(sender,args[1]);if(id==null)return true;UUID staff=sender instanceof Player p?p.getUniqueId():null;db.addCaseComment(id,staff,sender.getName(),sub.toUpperCase(Locale.ROOT),Text.join(args,2));sender.sendMessage(Text.mm("<green>Added to case #"+id+".</green>"));Bukkit.getPluginManager().callEvent(new SMPCaseUpdateEvent(id,sub.toUpperCase(Locale.ROOT)));return true;}
            case "link"->{if(args.length<4)return usage(sender,"/case link <id> <report|punishment|note> <record-id>");Integer id=parseId(sender,args[1]),record=parseId(sender,args[3]);if(id==null||record==null)return true;String type=args[2].toUpperCase(Locale.ROOT);if(!Set.of("REPORT","PUNISHMENT","NOTE").contains(type))return usage(sender,"Record type must be report, punishment or note.");db.linkCase(id,type,record);sender.sendMessage(Text.mm("<green>Linked "+type.toLowerCase(Locale.ROOT)+" #"+record+" to case #"+id+".</green>"));return true;}
            case "list"->{list(sender,null);return true;}
            default->{return usage(sender,"Unknown case subcommand.");}
        }
    }

    public void openGui(Player viewer,UUID target){
        db.cases(null,target).thenAccept(rows->plugin.sync(()->{
            int size=54;
            String title=plugin.getConfig().getString("platform.guis.cases.title","<dark_red>Moderation Cases</dark_red>");
            Inventory inv=Bukkit.createInventory(null,size,Text.mm(title));
            List<Integer> ids=new ArrayList<>();
            int slot=0;
            for(PlatformDatabase.CaseRow row:rows){if(slot>=45)break;ids.add(row.id());inv.setItem(slot++,caseItem(row));}
            inv.setItem(49,item(Material.BARRIER,"<red>Close</red>",List.of()));
            openGuiRows.put(viewer.getUniqueId(),ids);viewer.openInventory(inv);
        }));
    }

    @EventHandler
    public void onClick(InventoryClickEvent event){
        if(!(event.getWhoClicked() instanceof Player player))return;
        List<Integer> ids=openGuiRows.get(player.getUniqueId());
        if(ids==null||!Text.plain(event.getView().title()).toLowerCase(Locale.ROOT).contains("case"))return;
        event.setCancelled(true);
        int raw=event.getRawSlot();
        if(raw==49){player.closeInventory();return;}
        if(raw<0||raw>=ids.size())return;
        int id=ids.get(raw);
        if(event.isShiftClick()){close(player,id);player.closeInventory();return;}
        if(event.isRightClick()){
            db.claimCase(id,player.getUniqueId(),player.getName()).thenAccept(ok->plugin.sync(()->{player.sendMessage(Text.mm(ok?"<green>Case #"+id+" claimed.</green>":"<red>Case could not be claimed.</red>"));openGui(player,null);}));
        }else{player.closeInventory();view(player,id);}
    }

    private void close(CommandSender sender,int id){
        db.closeCase(id).thenAccept(ok->plugin.sync(()->{
            if(!ok){sender.sendMessage(Text.mm("<red>Case could not be closed.</red>"));return;}
            undo.record(sender,"CASE_CLOSE",Integer.toString(id));
            UUID staff=sender instanceof Player p?p.getUniqueId():null;db.logStaff(staff,sender.getName(),"CASE","closed #"+id);
            plugin.messages().send(sender,"cases.closed","id",id);Bukkit.getPluginManager().callEvent(new SMPCaseUpdateEvent(id,"CLOSED"));
        }));
    }

    private void view(CommandSender sender,int id){
        var row=db.caseById(id);var comments=db.caseComments(id);var links=db.caseLinks(id);
        java.util.concurrent.CompletableFuture.allOf(row,comments,links).thenRun(()->plugin.sync(()->{
            if(row.join().isEmpty()){sender.sendMessage(Text.mm("<red>Case not found.</red>"));return;}
            PlatformDatabase.CaseRow c=row.join().get();
            sender.sendMessage(Text.mm("<gold><bold>Case #"+c.id()+" — "+Text.escapeMini(c.targetName())+"</bold></gold>"));
            sender.sendMessage(Text.mm("<gray>Status:</gray> <yellow>"+c.status()+"</yellow> <gray>Assigned:</gray> <white>"+Text.escapeMini(String.valueOf(c.staffName()))+"</white>"));
            sender.sendMessage(Text.mm("<gray>Title:</gray> <white>"+Text.escapeMini(c.title())+"</white>"));
            for(PlatformDatabase.CaseLink link:links.join())sender.sendMessage(Text.mm("<dark_gray>Linked "+link.type()+" #"+link.recordId()+"</dark_gray>"));
            for(PlatformDatabase.CaseComment comment:comments.join())sender.sendMessage(Text.mm("<gray>["+comment.kind()+"] "+Text.escapeMini(comment.staffName())+":</gray> <white>"+Text.escapeMini(comment.body())+"</white>"));
        }));
    }

    private void list(CommandSender sender,UUID target){db.cases(null,target).thenAccept(rows->plugin.sync(()->{sender.sendMessage(Text.mm("<gold><bold>Cases</bold></gold>"));for(PlatformDatabase.CaseRow c:rows.stream().limit(30).toList())sender.sendMessage(Text.mm("<gray>#"+c.id()+"</gray> <white>"+Text.escapeMini(c.targetName())+"</white> <yellow>"+c.status()+"</yellow> <dark_gray>"+Text.escapeMini(c.title())+"</dark_gray>"));}));}

    private ItemStack caseItem(PlatformDatabase.CaseRow row){return item(Material.WRITABLE_BOOK,"<gold>Case #"+row.id()+"</gold>",List.of("<gray>Target: <white>"+Text.escapeMini(row.targetName())+"</white></gray>","<gray>Status: <yellow>"+row.status()+"</yellow></gray>","<white>"+Text.escapeMini(row.title())+"</white>","<dark_gray>Left: view | Right: claim | Shift: close</dark_gray>"));}
    private ItemStack item(Material material,String name,List<String> lore){ItemStack stack=new ItemStack(material);ItemMeta meta=stack.getItemMeta();meta.displayName(Text.mm(name));meta.lore(lore.stream().map(Text::mm).toList());stack.setItemMeta(meta);return stack;}
    private Integer parseId(CommandSender sender,String raw){try{return Integer.parseInt(raw);}catch(NumberFormatException ex){sender.sendMessage(Text.mm("<red>ID must be a number.</red>"));return null;}}
    private boolean usage(CommandSender sender,String text){sender.sendMessage(Text.mm("<red>"+Text.escapeMini(text)+"</red>"));return true;}
    private void resolve(String name,CommandSender sender,java.util.function.BiConsumer<UUID,String> found){Player online=Bukkit.getPlayerExact(name);if(online!=null){found.accept(online.getUniqueId(),online.getName());return;}plugin.database().findPlayerByName(name).thenAccept(opt->plugin.sync(()->{if(opt.isEmpty())plugin.messages().send(sender,"general.player-not-found");else found.accept(opt.get().uuid(),opt.get().name());}));}
}
