package io.github.xtx.smptoolkit.modules.moderation;

import io.github.xtx.smptoolkit.SMPToolkitPlugin;
import io.github.xtx.smptoolkit.core.Module;
import io.github.xtx.smptoolkit.core.Text;
import io.github.xtx.smptoolkit.data.Database;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.BiConsumer;

public final class ModerationModule implements Module, Listener {
    private final SMPToolkitPlugin plugin;

    public ModerationModule(SMPToolkitPlugin plugin) { this.plugin = plugin; }
    @Override public String name() { return "moderation"; }
    @Override public void enable() { Bukkit.getPluginManager().registerEvents(this, plugin); }
    @Override public void disable() {}

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        Optional<Database.Punishment> ban = plugin.database().activePunishmentNow(event.getUniqueId(), "BAN");
        if (ban.isEmpty()) return;
        Database.Punishment p = ban.get();
        String duration = p.expiresAt() == null ? "Permanent" : "Remaining: " + Text.formatDuration(p.expiresAt() - System.currentTimeMillis());
        event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED,
                Text.mm("<red><bold>You are banned.</bold></red>\n<gray>Reason:</gray> <white>" + Text.escapeMini(p.reason()) + "</white>\n<gray>" + duration + "</gray>\n<dark_gray>Punishment #" + p.id() + "</dark_gray>"));
    }

    public boolean handle(CommandSender sender, String label, String[] args) {
        switch (label.toLowerCase(Locale.ROOT)) {
            case "kick" -> { return kick(sender,args); }
            case "warn" -> { return punish(sender,args,"WARN",false,false); }
            case "warnings" -> { return warnings(sender,args); }
            case "mute" -> { return punish(sender,args,"MUTE",false,false); }
            case "tempmute" -> { return punish(sender,args,"MUTE",true,false); }
            case "unmute" -> { return unpunish(sender,args,"MUTE"); }
            case "ban" -> { return punish(sender,args,"BAN",false,true); }
            case "tempban" -> { return punish(sender,args,"BAN",true,true); }
            case "unban" -> { return unpunish(sender,args,"BAN"); }
            case "history" -> { return history(sender,args); }
            case "note" -> { return note(sender,args); }
            case "report" -> { return report(sender,args); }
            case "reports" -> { return reports(sender,args); }
        }
        return false;
    }

    private boolean kick(CommandSender sender, String[] args) {
        if (args.length < 1) return usage(sender,"/kick <player> [reason]");
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) return notFound(sender);
        String reason = args.length > 1 ? Text.join(args,1) : "Kicked by staff";
        target.kick(Text.mm("<red>You were kicked.</red>\n<gray>Reason:</gray> <white>" + Text.escapeMini(reason) + "</white>"));
        Bukkit.broadcast(Text.mm("<red>" + Text.escapeMini(target.getName()) + " was kicked:</red> <gray>" + Text.escapeMini(reason) + "</gray>"), "smp.staff");
        return true;
    }

    private boolean punish(CommandSender sender, String[] args, String type, boolean temporary, boolean kick) {
        int min = temporary ? 3 : 2;
        if (args.length < min) {
            String usage = temporary ? "/" + (type.equals("BAN") ? "tempban" : "tempmute") + " <player> <duration> <reason>" : "/" + type.toLowerCase(Locale.ROOT) + " <player> <reason>";
            return usage(sender, usage);
        }
        String targetName = args[0];
        long duration = -1;
        int reasonStart = 1;
        if (temporary) {
            duration = Text.parseDurationMillis(args[1]);
            if (duration <= 0) return usage(sender,"Invalid duration. Examples: 30m, 7d, 1h30m");
            reasonStart = 2;
        }
        String reason = Text.join(args, reasonStart);
        if (reason.isBlank()) reason = "No reason supplied";
        long finalDuration = duration;
        String finalReason = reason;
        resolveKnown(targetName, sender, (uuid, canonical) -> {
            Long expires = temporary ? System.currentTimeMillis() + finalDuration : null;
            UUID staffUuid = sender instanceof Player p ? p.getUniqueId() : null;
            plugin.database().addPunishment(uuid,type,finalReason,staffUuid,sender.getName(),expires).thenAccept(id -> plugin.sync(() -> {
                String time = expires == null ? "permanently" : "for " + Text.formatDuration(finalDuration);
                sender.sendMessage(Text.mm("<green>" + type + " #" + id + " issued to " + Text.escapeMini(canonical) + " " + time + ".</green>"));
                Player target = Bukkit.getPlayer(uuid);
                if (target != null) {
                    if (type.equals("WARN")) target.sendMessage(Text.mm("<yellow><bold>Warning #"+id+"</bold></yellow> <gray>"+Text.escapeMini(finalReason)+"</gray>"));
                    if (type.equals("MUTE")) target.sendMessage(Text.mm("<red>You have been muted " + time + ".</red> <gray>Reason: " + Text.escapeMini(finalReason) + "</gray>"));
                    if (type.equals("BAN")) target.kick(Text.mm("<red><bold>You are banned.</bold></red>\n<gray>Reason:</gray> <white>"+Text.escapeMini(finalReason)+"</white>\n<gray>"+time+"</gray>\n<dark_gray>Punishment #"+id+"</dark_gray>"));
                }
                staffBroadcast(Text.mm("<dark_gray>[MOD]</dark_gray> <white>"+Text.escapeMini(sender.getName())+"</white> <gray>issued</gray> <yellow>"+type+"</yellow> <gray>to</gray> <white>"+Text.escapeMini(canonical)+"</white> <dark_gray>#"+id+"</dark_gray>"));
            }));
        });
        return true;
    }

    private boolean unpunish(CommandSender sender, String[] args, String type) {
        if (args.length < 1) return usage(sender,"/un"+type.toLowerCase(Locale.ROOT)+" <player>");
        resolveKnown(args[0],sender,(uuid,name)->plugin.database().deactivatePunishments(uuid,type).thenAccept(count->plugin.sync(()->sender.sendMessage(Text.mm(count>0?"<green>Removed active "+type.toLowerCase(Locale.ROOT)+" from "+Text.escapeMini(name)+".</green>":"<gray>No active "+type.toLowerCase(Locale.ROOT)+" found.</gray>")))));
        return true;
    }

    private boolean warnings(CommandSender sender, String[] args) {
        if (args.length < 1) return usage(sender,"/warnings <player>");
        resolveKnown(args[0],sender,(uuid,name)->plugin.database().history(uuid).thenAccept(rows->plugin.sync(()->{
            sender.sendMessage(Text.mm("<gold><bold>Warnings — "+Text.escapeMini(name)+"</bold></gold>"));
            rows.stream().filter(p->p.type().equals("WARN")).limit(20).forEach(p->sender.sendMessage(Text.mm("<gray>#"+p.id()+"</gray> <white>"+Text.escapeMini(p.reason())+"</white> <dark_gray>by "+Text.escapeMini(p.staffName())+"</dark_gray>")));
        })));
        return true;
    }

    private boolean history(CommandSender sender, String[] args) {
        if (args.length < 1) return usage(sender,"/history <player>");
        resolveKnown(args[0],sender,(uuid,name)->plugin.database().history(uuid).thenAccept(rows->plugin.sync(()->{
            sender.sendMessage(Text.mm("<gold><bold>Punishment History — "+Text.escapeMini(name)+"</bold></gold>"));
            for(Database.Punishment p:rows){String exp=p.expiresAt()==null?"permanent":DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(p.expiresAt()));sender.sendMessage(Text.mm("<gray>#"+p.id()+"</gray> <yellow>"+p.type()+"</yellow> <white>"+Text.escapeMini(p.reason())+"</white> <dark_gray>by "+Text.escapeMini(p.staffName())+" | "+(p.active()?"active":"inactive")+" | "+exp+"</dark_gray>"));}
        })));
        return true;
    }

    private boolean note(CommandSender sender, String[] args) {
        if(args.length<2)return usage(sender,"/note <add|list> <player> [note]");
        String sub=args[0].toLowerCase(Locale.ROOT), name=args[1];
        resolveKnown(name,sender,(uuid,canonical)->{
            if(sub.equals("add")){
                if(args.length<3){usage(sender,"/note add <player> <note>");return;}
                UUID staff=sender instanceof Player p?p.getUniqueId():null;String note=Text.join(args,2);plugin.database().addNote(uuid,staff,sender.getName(),note).thenRun(()->plugin.sync(()->sender.sendMessage(Text.mm("<green>Staff note added.</green>"))));
            }else if(sub.equals("list"))plugin.database().notes(uuid).thenAccept(rows->plugin.sync(()->{sender.sendMessage(Text.mm("<gold><bold>Staff Notes — "+Text.escapeMini(canonical)+"</bold></gold>"));for(Database.Note n:rows)sender.sendMessage(Text.mm("<gray>#"+n.id()+" by "+Text.escapeMini(n.staffName())+":</gray> <white>"+Text.escapeMini(n.note())+"</white>"));}));
            else usage(sender,"/note <add|list> <player> [note]");
        });
        return true;
    }

    private boolean report(CommandSender sender, String[] args) {
        if(!(sender instanceof Player reporter)){sender.sendMessage("Players only.");return true;}
        if(args.length<2)return usage(sender,"/report <player> <reason>");
        Player target=Bukkit.getPlayerExact(args[0]);if(target==null)return notFound(sender);if(target.equals(reporter))return usage(sender,"You cannot report yourself.");
        String reason=Text.join(args,1);plugin.database().createReport(reporter.getUniqueId(),reporter.getName(),target.getUniqueId(),target.getName(),reason).thenAccept(id->plugin.sync(()->{reporter.sendMessage(Text.mm("<green>Report #"+id+" submitted.</green>"));staffBroadcast(Text.mm("<red>[REPORT #"+id+"]</red> <white>"+Text.escapeMini(reporter.getName())+"</white> <gray>reported</gray> <yellow>"+Text.escapeMini(target.getName())+"</yellow><gray>: "+Text.escapeMini(reason)+"</gray>"));}));return true;
    }

    private boolean reports(CommandSender sender,String[] args){
        if(args.length==0||args[0].equalsIgnoreCase("list")){plugin.database().openReports().thenAccept(rows->plugin.sync(()->{sender.sendMessage(Text.mm("<gold><bold>Open Reports</bold></gold>"));for(Database.ReportRecord r:rows)sender.sendMessage(Text.mm("<gray>#"+r.id()+"</gray> <white>"+Text.escapeMini(r.reporterName())+" → "+Text.escapeMini(r.targetName())+"</white> <yellow>"+r.status()+"</yellow> <dark_gray>"+Text.escapeMini(r.reason())+"</dark_gray>"));}));return true;}
        if(args.length<2)return usage(sender,"/reports <view|claim|close> <id> [result]");
        int id;try{id=Integer.parseInt(args[1]);}catch(NumberFormatException ex){return usage(sender,"Report ID must be a number.");}
        switch(args[0].toLowerCase(Locale.ROOT)){
            case "view"->plugin.database().report(id).thenAccept(opt->plugin.sync(()->{if(opt.isEmpty()){sender.sendMessage(Text.mm("<red>Report not found.</red>"));return;}Database.ReportRecord r=opt.get();sender.sendMessage(Text.mm("<gold><bold>Report #"+id+"</bold></gold>"));sender.sendMessage(Text.mm("<gray>Reporter:</gray> <white>"+Text.escapeMini(r.reporterName())+"</white> <gray>Target:</gray> <white>"+Text.escapeMini(r.targetName())+"</white>"));sender.sendMessage(Text.mm("<gray>Reason:</gray> <white>"+Text.escapeMini(r.reason())+"</white> <gray>Status:</gray> <yellow>"+r.status()+"</yellow>"));}));
            case "claim"->{if(!(sender instanceof Player p))return usage(sender,"Player staff only for claim.");plugin.database().claimReport(id,p.getUniqueId(),p.getName()).thenAccept(ok->plugin.sync(()->sender.sendMessage(Text.mm(ok?"<green>Report claimed.</green>":"<red>Report could not be claimed.</red>"))));}
            case "close"->{if(!(sender instanceof Player p))return usage(sender,"Player staff only for close.");String result=args.length>2?Text.join(args,2):"Resolved";plugin.database().closeReport(id,p.getUniqueId(),p.getName(),result).thenAccept(ok->plugin.sync(()->sender.sendMessage(Text.mm(ok?"<green>Report closed.</green>":"<red>Report could not be closed.</red>"))));}
            default->usage(sender,"/reports <view|claim|close> <id> [result]");
        }return true;
    }

    private void resolveKnown(String name, CommandSender sender, BiConsumer<UUID,String> found) {
        Player online=Bukkit.getPlayerExact(name);if(online!=null){found.accept(online.getUniqueId(),online.getName());return;}
        plugin.database().findPlayerByName(name).thenAccept(opt->plugin.sync(()->{if(opt.isEmpty())notFound(sender);else found.accept(opt.get().uuid(),opt.get().name());}));
    }

    private void staffBroadcast(Component msg){for(Player p:Bukkit.getOnlinePlayers())if(p.hasPermission("smp.moderation.reports")||p.hasPermission("smp.staff"))p.sendMessage(msg);Bukkit.getConsoleSender().sendMessage(msg);}
    private boolean usage(CommandSender s,String m){s.sendMessage(Text.mm("<red>"+Text.escapeMini(m)+"</red>"));return true;}
    private boolean notFound(CommandSender s){s.sendMessage(Text.mm("<red>Player not found.</red>"));return true;}
}
