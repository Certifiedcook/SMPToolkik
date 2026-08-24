package io.github.xtx.smptoolkit.platform;

import io.github.xtx.smptoolkit.SMPToolkitPlugin;
import io.github.xtx.smptoolkit.core.Text;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.UUID;

public final class UndoService {
    private final SMPToolkitPlugin plugin;
    private final PlatformDatabase db;
    public UndoService(SMPToolkitPlugin plugin,PlatformDatabase db){this.plugin=plugin;this.db=db;}

    public void record(CommandSender actor,String type,String payload){
        UUID id=actor instanceof Player p?p.getUniqueId():null;
        db.addUndo(id,actor.getName(),type,payload);
    }

    public boolean handle(CommandSender sender,String[] args){
        if(args.length==0||args[0].equalsIgnoreCase("list")){
            db.undoRows(20).thenAccept(rows->plugin.sync(()->{
                sender.sendMessage(Text.mm("<gold><bold>Rollback History</bold></gold>"));
                for(PlatformDatabase.UndoRow row:rows)sender.sendMessage(Text.mm("<gray>#"+row.id()+"</gray> <yellow>"+Text.escapeMini(row.type())+"</yellow> <white>"+Text.escapeMini(row.actorName())+"</white> "+(row.undone()?"<dark_gray>[undone]</dark_gray>":"")));
            }));
            return true;
        }
        int id;
        try{id=Integer.parseInt(args[0]);}catch(NumberFormatException ex){sender.sendMessage(Text.mm("<red>Usage: /rollback <id|list></red>"));return true;}
        db.undoRow(id).thenAccept(opt->plugin.sync(()->{
            if(opt.isEmpty()||opt.get().undone()){plugin.messages().send(sender,"rollback.unavailable");return;}
            PlatformDatabase.UndoRow row=opt.get();
            switch(row.type()){
                case "WARNING_REMOVE","PUNISHMENT_PARDON"->db.reactivatePunishment(Integer.parseInt(row.payload())).thenAccept(ok->finish(sender,id,ok));
                case "REPORT_CLOSE"->db.reopenReport(Integer.parseInt(row.payload())).thenAccept(ok->finish(sender,id,ok));
                case "CASE_CLOSE"->db.reopenCase(Integer.parseInt(row.payload())).thenAccept(ok->finish(sender,id,ok));
                case "CONFIG_BOOL"->{String[] parts=row.payload().split("\\u001f",2);if(parts.length!=2){plugin.messages().send(sender,"rollback.unavailable");return;}boolean old=Boolean.parseBoolean(parts[1]);plugin.getConfig().set(parts[0],old);plugin.saveConfig();finish(sender,id,true);}
                default->plugin.messages().send(sender,"rollback.unavailable");
            }
        }));
        return true;
    }

    private void finish(CommandSender sender,int id,boolean ok){
        plugin.sync(()->{
            if(!ok){plugin.messages().send(sender,"rollback.unavailable");return;}
            db.markUndone(id);
            plugin.messages().send(sender,"rollback.success","id",id);
        });
    }
}
