package io.github.xtx.smptoolkit.modules.gameplay;

import io.github.xtx.smptoolkit.SMPToolkitPlugin;
import io.github.xtx.smptoolkit.core.Module;
import io.github.xtx.smptoolkit.core.Text;
import io.github.xtx.smptoolkit.data.Database;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.scheduler.BukkitTask;

import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

class GameplayCommandsBase extends GameplayEventsBase {
    GameplayCommandsBase(SMPToolkitPlugin plugin) { super(plugin); }

public boolean handle(CommandSender sender, String label, String[] args) {
        switch (label.toLowerCase(Locale.ROOT)) {
            case "voteskipnight" -> { return voteSkip(sender); }
            case "streak" -> { return streak(sender,args); }
            case "profile" -> { return profile(sender,args); }
            case "coinflip" -> { return coinflip(sender,args); }
            case "manhunt" -> { return manhunt(sender,args); }
            case "signup" -> { return signup(sender,args); }
            case "discord" -> { sender.sendMessage(Text.mm("<aqua>Discord:</aqua> <white>"+Text.escapeMini(plugin.getConfig().getString("server-info.discord","Not configured"))+"</white>")); return true; }
            case "rules" -> { return rules(sender); }
            case "help", "smphelp" -> { return help(sender); }
        }
        return false;
    }

private boolean voteSkip(CommandSender sender) {
        if (!(sender instanceof Player p)) { sender.sendMessage("Players only."); return true; }
        World world=p.getWorld();
        if (world.getTime()<12542 || world.getTime()>23460) { nightVotes.remove(world.getUID()); p.sendMessage(Text.mm("<gray>It is not currently night.</gray>")); return true; }
        Set<UUID> votes=nightVotes.computeIfAbsent(world.getUID(), k->ConcurrentHashMap.newKeySet());
        Set<UUID> onlineHere = new HashSet<>(); for (Player player : world.getPlayers()) onlineHere.add(player.getUniqueId()); votes.retainAll(onlineHere);
        if (!votes.add(p.getUniqueId())) { p.sendMessage(Text.mm("<yellow>You have already voted.</yellow>")); return true; }
        int eligible=Math.max(1,world.getPlayers().size());
        double percent=Math.max(1,Math.min(100,plugin.getConfig().getDouble("gameplay.vote-skip-night.required-percent",50)));
        int required=Math.max(1,(int)Math.ceil(eligible*(percent/100.0)));
        Bukkit.broadcast(Text.mm("<gray>Night-skip vote:</gray> <yellow>"+votes.size()+"/"+required+"</yellow>"));
        if(votes.size()>=required){world.setTime(0);world.setStorm(false);world.setThundering(false);votes.clear();Bukkit.broadcast(Text.mm("<green>The night was skipped by vote.</green>"));}
        return true;
    }

private boolean streak(CommandSender sender,String[] args){
        Player target=args.length>0?Bukkit.getPlayerExact(args[0]):sender instanceof Player p?p:null;
        if(target==null){sender.sendMessage(Text.mm("<red>Player not found.</red>"));return true;}
        sender.sendMessage(Text.mm("<gold>"+Text.escapeMini(target.getName())+"</gold><gray>'s current kill streak:</gray> <yellow>"+streaks.getOrDefault(target.getUniqueId(),0)+"</yellow>"));return true;
    }

private boolean profile(CommandSender sender,String[] args){
        Player target=args.length>0?Bukkit.getPlayerExact(args[0]):sender instanceof Player p?p:null;
        if(target==null){sender.sendMessage(Text.mm("<red>Player must be online for /profile.</red>"));return true;}
        Database.Stats s=plugin.stats().cached(target.getUniqueId());
        sender.sendMessage(Text.mm("<gold><bold>SMP Profile — "+Text.escapeMini(target.getName())+"</bold></gold>"));
        sender.sendMessage(Text.mm("<gray>Group:</gray> <white>"+Text.escapeMini(plugin.integrations().luckPerms().primaryGroup(target))+"</white> <gray>Playtime:</gray> <white>"+Text.formatDuration(s.playtimeMs())+"</white>"));
        sender.sendMessage(Text.mm("<gray>Kills:</gray> <white>"+s.kills()+"</white> <gray>Deaths:</gray> <white>"+s.deaths()+"</white> <gray>K/D:</gray> <white>"+String.format(Locale.ROOT,"%.2f",s.kd())+"</white>"));
        sender.sendMessage(Text.mm("<gray>Streak:</gray> <yellow>"+streaks.getOrDefault(target.getUniqueId(),0)+"</yellow> <gray>Chat wins:</gray> <yellow>"+s.chatWins()+"</yellow>"));
        if(isProtected(target))sender.sendMessage(Text.mm("<green>New-player PvP protection active.</green>"));
        return true;
    }

private boolean coinflip(CommandSender sender,String[] args){
        if(!(sender instanceof Player p)){sender.sendMessage("Players only.");return true;}
        if(args.length<1){p.sendMessage(Text.mm("<red>Usage: /coinflip <player> | /coinflip accept <player> | /coinflip deny</red>"));return true;}
        if(args[0].equalsIgnoreCase("accept")){
            CoinflipChallenge c=coinflipsByTarget.remove(p.getUniqueId());
            if(c==null||c.expiresAt()<System.currentTimeMillis()){p.sendMessage(Text.mm("<red>No active coinflip challenge.</red>"));return true;}
            if(args.length>1){Player named=Bukkit.getPlayerExact(args[1]);if(named==null||!named.getUniqueId().equals(c.challenger())){p.sendMessage(Text.mm("<red>That is not your active challenger.</red>"));coinflipsByTarget.put(p.getUniqueId(),c);return true;}}
            Player challenger=Bukkit.getPlayer(c.challenger());if(challenger==null){p.sendMessage(Text.mm("<red>Challenger is offline.</red>"));return true;}
            resolveCoinflip(challenger,p);return true;
        }
        if(args[0].equalsIgnoreCase("deny")){coinflipsByTarget.remove(p.getUniqueId());p.sendMessage(Text.mm("<gray>Coinflip challenge denied.</gray>"));return true;}
        Player target=Bukkit.getPlayerExact(args[0]);if(target==null||target.equals(p)){p.sendMessage(Text.mm("<red>Player not found.</red>"));return true;}
        if(oneHeld(p)==null){p.sendMessage(Text.mm("<red>Hold the item you want to wager in your main hand.</red>"));return true;}
        coinflipsByTarget.put(target.getUniqueId(),new CoinflipChallenge(p.getUniqueId(),System.currentTimeMillis()+60_000L));
        p.sendMessage(Text.mm("<green>Coinflip challenge sent to "+Text.escapeMini(target.getName())+".</green>"));
        target.sendMessage(Text.mm("<yellow>"+Text.escapeMini(p.getName())+" challenged you to a one-item coinflip.</yellow> <gray>Hold your wager and use /coinflip accept "+Text.escapeMini(p.getName())+".</gray>"));return true;
    }

private void resolveCoinflip(Player challenger,Player target){
        ItemStack a=oneHeld(challenger),b=oneHeld(target);
        if(a==null||b==null){challenger.sendMessage(Text.mm("<red>Both players must hold a wager item.</red>"));target.sendMessage(Text.mm("<red>Both players must hold a wager item.</red>"));return;}
        takeOne(challenger);takeOne(target);
        boolean challengerWins=secureRandom.nextInt(100)<49;
        Player winner=challengerWins?challenger:target;
        giveOrDrop(winner,a);giveOrDrop(winner,b);
        Bukkit.broadcast(Text.mm("<gold><bold>Coinflip:</bold></gold> <white>"+Text.escapeMini(winner.getName())+"</white> <gray>won the wager between "+Text.escapeMini(challenger.getName())+" and "+Text.escapeMini(target.getName())+".</gray>"));
    }

private ItemStack oneHeld(Player p){ItemStack held=p.getInventory().getItemInMainHand();if(held.getType().isAir()||held.getAmount()<1)return null;ItemStack one=held.clone();one.setAmount(1);return one;}

private void takeOne(Player p){ItemStack held=p.getInventory().getItemInMainHand();if(held.getAmount()<=1)p.getInventory().setItemInMainHand(null);else held.setAmount(held.getAmount()-1);}

private void giveOrDrop(Player p,ItemStack item){Map<Integer,ItemStack> left=p.getInventory().addItem(item);for(ItemStack i:left.values())p.getWorld().dropItemNaturally(p.getLocation(),i);}

private boolean manhunt(CommandSender sender,String[] args){
        if(args.length==0){sender.sendMessage(Text.mm("<gray>Manhunt:</gray> "+(manhuntRunner==null?(manhuntSignupOpen?"<yellow>signup open</yellow>":"<red>inactive</red>"):"<green>running</green>")));return true;}
        if(!sender.hasPermission("smp.events.admin")){sender.sendMessage(Text.mm("<red>No permission.</red>"));return true;}
        switch(args[0].toLowerCase(Locale.ROOT)){
            case "start"->{manhuntSignups.clear();manhuntSignupOpen=true;Bukkit.broadcast(Text.mm("<red><bold>MANHUNT SIGNUPS OPEN</bold></red> <gray>Use <yellow>/signup manhunt</yellow> to join as a hunter.</gray>"));return true;}
            case "begin"->{if(args.length<2){sender.sendMessage(Text.mm("<red>Usage: /manhunt begin <runner></red>"));return true;}Player runner=Bukkit.getPlayerExact(args[1]);if(runner==null){sender.sendMessage(Text.mm("<red>Runner not found.</red>"));return true;}beginManhunt(runner);return true;}
            case "stop"->{stopManhunt(true);return true;}
            default->{sender.sendMessage(Text.mm("<red>Usage: /manhunt <start|begin|stop></red>"));return true;}
        }
    }

private boolean signup(CommandSender sender,String[] args){
        if(!(sender instanceof Player p)){sender.sendMessage("Players only.");return true;}
        if(args.length<1||!args[0].equalsIgnoreCase("manhunt")){p.sendMessage(Text.mm("<red>Usage: /signup manhunt</red>"));return true;}
        if(!manhuntSignupOpen){p.sendMessage(Text.mm("<red>Manhunt signups are not open.</red>"));return true;}
        manhuntSignups.add(p.getUniqueId());p.sendMessage(Text.mm("<green>You signed up for Manhunt.</green>"));return true;
    }

private void beginManhunt(Player runner){
        manhuntRunner=runner.getUniqueId();manhuntSignupOpen=false;manhuntHunters.clear();manhuntHunters.addAll(manhuntSignups);manhuntHunters.remove(runner.getUniqueId());
        int seconds=Math.max(1,plugin.getConfig().getInt("gameplay.manhunt.compass-update-seconds",5));
        if(manhuntTask!=null)manhuntTask.cancel();
        manhuntTask=Bukkit.getScheduler().runTaskTimer(plugin,()->{
            Player r=Bukkit.getPlayer(manhuntRunner);if(r==null){Bukkit.broadcast(Text.mm("<gray>Manhunt paused: runner is offline.</gray>"));return;}
            for(UUID id:manhuntHunters){Player hunter=Bukkit.getPlayer(id);if(hunter!=null&&hunter.getWorld().equals(r.getWorld()))hunter.setCompassTarget(r.getLocation());}
        },0L,seconds*20L);
        Bukkit.broadcast(Text.mm("<red><bold>MANHUNT STARTED</bold></red> <gray>Runner:</gray> <white>"+Text.escapeMini(runner.getName())+"</white> <gray>Hunters:</gray> <white>"+manhuntHunters.size()+"</white>"));
    }

private boolean rules(CommandSender sender){sender.sendMessage(Text.mm("<gold><bold>Server Rules</bold></gold>"));int i=1;for(String line:plugin.getConfig().getStringList("server-info.rules"))sender.sendMessage(Text.mm("<gray>"+(i++)+".</gray> <white>"+Text.escapeMini(line)+"</white>"));return true;}

private boolean help(CommandSender sender){sender.sendMessage(Text.mm("<gold><bold>SMP Commands</bold></gold>"));sender.sendMessage(Text.mm("<yellow>/tpa /home /spawn /back /rtp</yellow> <gray>— travel</gray>"));sender.sendMessage(Text.mm("<yellow>/msg /reply /ignore /channel</yellow> <gray>— communication</gray>"));sender.sendMessage(Text.mm("<yellow>/stats /profile /streak /topkills /topplaytime</yellow> <gray>— stats</gray>"));sender.sendMessage(Text.mm("<yellow>/voteskipnight /coinflip /chatgames /locator</yellow> <gray>— SMP features</gray>"));sender.sendMessage(Text.mm("<yellow>/report /rules /discord</yellow> <gray>— support/info</gray>"));return true;}
}
