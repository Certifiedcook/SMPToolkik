package io.github.xtx.smptoolkit.modules.staff;

import io.github.xtx.smptoolkit.SMPToolkitPlugin;
import io.github.xtx.smptoolkit.core.Module;
import io.github.xtx.smptoolkit.core.Text;
import io.github.xtx.smptoolkit.data.Database;
import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;

class StaffState implements Module {
    protected final SMPToolkitPlugin plugin;
    protected final Set<UUID> frozen = ConcurrentHashMap.newKeySet();
    protected final Set<UUID> vanished = ConcurrentHashMap.newKeySet();
    protected final Set<UUID> god = ConcurrentHashMap.newKeySet();
    protected final Set<UUID> staffMode = ConcurrentHashMap.newKeySet();
    protected final Map<UUID, UUID> guiTargets = new ConcurrentHashMap<>();
    protected volatile int restartTask = -1;
    protected volatile int restartRemaining = -1;
    StaffState(SMPToolkitPlugin plugin) { this.plugin = plugin; }
    @Override public String name() { return "staff"; }
    @Override public void enable() {
        plugin.database().flagsWithValue("frozen", "true").thenAccept(frozen::addAll);
        plugin.database().flagsWithValue("vanished", "true").thenAccept(ids -> {
            vanished.addAll(ids);
            plugin.sync(this::refreshVanish);
        });
    }
    @Override public void disable() {
        if (restartTask != -1) Bukkit.getScheduler().cancelTask(restartTask);
        for (UUID id : vanished) {
            Player target = Bukkit.getPlayer(id);
            if (target != null) for (Player viewer : Bukkit.getOnlinePlayers()) viewer.showPlayer(plugin, target);
        }
    }
    public boolean isFrozen(UUID uuid) { return frozen.contains(uuid); }
    public boolean isVanished(UUID uuid) { return vanished.contains(uuid); }
    protected void toggleFreeze(CommandSender actor, Player target) { toggleFreeze(actor,target,!frozen.contains(target.getUniqueId())); }
    protected void toggleFreeze(CommandSender actor, Player target, boolean state) { setFrozen(actor,target.getUniqueId(),target.getName(),state); }
    protected void setFrozen(CommandSender actor, UUID id, String name, boolean state) {
        if (state) frozen.add(id); else frozen.remove(id);
        plugin.database().setFlag(id,"frozen",Boolean.toString(state));
        Player target=Bukkit.getPlayer(id);
        if(target!=null) target.sendMessage(Text.mm(state?"<red><bold>You have been frozen.</bold></red> <gray>Do not disconnect. Wait for staff instructions.</gray>":"<green>You have been unfrozen.</green>"));
        actor.sendMessage(Text.mm(state?"<yellow>Frozen " + Text.escapeMini(name) + ".</yellow>":"<green>Unfrozen " + Text.escapeMini(name) + ".</green>"));
    }
    protected void toggleVanish(Player target) {
        boolean on=!vanished.remove(target.getUniqueId()); if(on)vanished.add(target.getUniqueId());
        plugin.database().setFlag(target.getUniqueId(),"vanished",Boolean.toString(on));
        refreshVanish();
    }
    protected void refreshVanish() {
        for(Player viewer:Bukkit.getOnlinePlayers()) for(UUID id:vanished){Player hidden=Bukkit.getPlayer(id);if(hidden!=null){if(viewer.equals(hidden)||viewer.hasPermission("smp.staff.vanish"))viewer.showPlayer(plugin,hidden);else viewer.hidePlayer(plugin,hidden);}}
    }
    protected void staffBroadcast(Component component) { for(Player p:Bukkit.getOnlinePlayers())if(p.hasPermission("smp.staff"))p.sendMessage(component); Bukkit.getConsoleSender().sendMessage(component); }
    protected void showPlayerInfo(CommandSender sender, Player target){showPlayerInfo(sender,target.getUniqueId(),target.getName());}
    protected void showPlayerInfo(CommandSender sender, UUID id,String name){
        var stats=plugin.database().stats(id);var hist=plugin.database().history(id);var alts=plugin.database().findAlts(id);
        CompletableFuture.allOf(stats,hist,alts).thenRun(()->plugin.sync(()->{
            Database.Stats s=stats.join();List<Database.Punishment> h=hist.join();List<Database.AltMatch> a=alts.join();
            long warns=h.stream().filter(x->x.type().equals("WARN")).count(),mutes=h.stream().filter(x->x.type().equals("MUTE")).count(),bans=h.stream().filter(x->x.type().equals("BAN")).count();
            sender.sendMessage(Text.mm("<gold><bold>Player Info — "+Text.escapeMini(name)+"</bold></gold>"));
            sender.sendMessage(Text.mm("<gray>UUID:</gray> <white>"+id+"</white>"));
            sender.sendMessage(Text.mm("<gray>Playtime:</gray> <white>"+Text.formatDuration(s.playtimeMs())+"</white> <gray>K/D:</gray> <white>"+String.format("%.2f",s.kd())+"</white>"));
            sender.sendMessage(Text.mm("<gray>Punishments:</gray> <yellow>"+warns+" warnings, "+mutes+" mutes, "+bans+" bans</yellow>"));
            sender.sendMessage(Text.mm("<gray>Possible alts:</gray> <yellow>"+a.size()+"</yellow> <gray>Frozen:</gray> <yellow>"+frozen.contains(id)+"</yellow> <gray>Vanished:</gray> <yellow>"+vanished.contains(id)+"</yellow>"));
        }));
    }
    protected void resolveKnown(String name,CommandSender sender,java.util.function.Consumer<UUID> found){Player p=Bukkit.getPlayerExact(name);if(p!=null){found.accept(p.getUniqueId());return;}plugin.database().findPlayerByName(name).thenAccept(opt->plugin.sync(()->{if(opt.isEmpty())sender.sendMessage(Text.mm("<red>Player not found.</red>"));else found.accept(opt.get().uuid());}));}
}
