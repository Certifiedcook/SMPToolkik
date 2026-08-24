package io.github.xtx.smptoolkit.modules.gameplay;

import io.github.xtx.smptoolkit.SMPToolkitPlugin;
import io.github.xtx.smptoolkit.core.Module;
import io.github.xtx.smptoolkit.core.Text;
import io.github.xtx.smptoolkit.data.Database;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.scheduler.BukkitTask;

import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

class GameplayState implements Module {
    protected final SMPToolkitPlugin plugin;
    protected final Map<UUID,Integer> streaks=new ConcurrentHashMap<>();
    protected final Map<String,Long> recentPairs=new ConcurrentHashMap<>();
    protected final Map<UUID,Long> combatUntil=new ConcurrentHashMap<>();
    protected final Map<UUID,Set<UUID>> nightVotes=new ConcurrentHashMap<>();
    protected final Map<UUID,CoinflipChallenge> coinflipsByTarget=new ConcurrentHashMap<>();
    protected final Set<UUID> manhuntSignups=ConcurrentHashMap.newKeySet();
    protected final SecureRandom secureRandom=new SecureRandom();
    protected BukkitTask supplyTask;
    protected BukkitTask manhuntTask;
    protected boolean manhuntSignupOpen;
    protected UUID manhuntRunner;
    protected final Set<UUID> manhuntHunters=ConcurrentHashMap.newKeySet();

    GameplayState(SMPToolkitPlugin plugin){this.plugin=plugin;}
    @Override public String name(){return "gameplay";}
    @Override public void enable(){if(plugin.featureEnabled("supply-drops")&&plugin.getConfig().getBoolean("gameplay.supply-drops.enabled",true)){long hours=Math.max(1,plugin.getConfig().getLong("gameplay.supply-drops.interval-hours",4));supplyTask=Bukkit.getScheduler().runTaskTimer(plugin,this::spawnSupplyDrop,hours*60*60*20L,hours*60*60*20L);}}
    @Override public void disable(){if(supplyTask!=null)supplyTask.cancel();stopManhunt(false);}

    public int combatSeconds(UUID uuid){long remaining=combatUntil.getOrDefault(uuid,0L)-System.currentTimeMillis();return (int)Math.max(0,(remaining+999)/1000);}
    public boolean isProtected(Player player){if(!plugin.featureEnabled("new-player-protection"))return false;if(plugin.platform()!=null&&!plugin.platform().worlds().newPlayerProtectionEnabled(player.getWorld()))return false;long mins=plugin.getConfig().getLong("gameplay.new-player-protection.playtime-minutes",30);if(mins<=0||player.hasPermission("smp.gameplay.protection.bypass"))return false;Database.Stats stats=plugin.stats()==null?null:plugin.stats().cached(player.getUniqueId());return stats!=null&&stats.playtimeMs()<mins*60_000L;}

    public void handlePvPKill(Player killer,Player victim){
        combatUntil.remove(killer.getUniqueId());combatUntil.remove(victim.getUniqueId());streaks.remove(victim.getUniqueId());
        if(!plugin.getConfig().getBoolean("gameplay.streaks.enabled",true))return;
        long window=Math.max(0,plugin.getConfig().getLong("gameplay.streaks.anti-farm-minutes",5))*60_000L;String key=killer.getUniqueId()+":"+victim.getUniqueId();long now=System.currentTimeMillis(),previous=recentPairs.getOrDefault(key,0L);recentPairs.put(key,now);if(now-previous<window)return;
        int streak=streaks.merge(killer.getUniqueId(),1,Integer::sum);int every=Math.max(1,plugin.getConfig().getInt("gameplay.streaks.reward-every",5)),extra=Math.max(1,plugin.getConfig().getInt("gameplay.streaks.extra-every",10));
        if(streak%every==0)runRewardCommands(killer,"gameplay.streaks.reward-commands");if(streak%extra==0)runRewardCommands(killer,"gameplay.streaks.extra-commands");if(streak>0&&streak%every==0)Bukkit.broadcast(Text.mm("<gold>"+Text.escapeMini(killer.getName())+" is on a <yellow>"+streak+" kill streak</yellow>!</gold>"));
        if(plugin.getConfig().getBoolean("gameplay.head-drops.enabled",true)){ItemStack head=new ItemStack(Material.PLAYER_HEAD);SkullMeta meta=(SkullMeta)head.getItemMeta();meta.setOwningPlayer(victim);head.setItemMeta(meta);victim.getWorld().dropItemNaturally(victim.getLocation(),head);}
    }

    public void stopManhunt(boolean announce){if(manhuntTask!=null){manhuntTask.cancel();manhuntTask=null;}manhuntRunner=null;manhuntSignupOpen=false;manhuntHunters.clear();manhuntSignups.clear();if(announce)Bukkit.broadcast(Text.mm("<gray>Manhunt stopped.</gray>"));}

    public void spawnSupplyDrop(){if(!plugin.featureEnabled("supply-drops"))return;List<Player> online=new ArrayList<>(Bukkit.getOnlinePlayers());if(online.isEmpty())return;Player anchor=online.get(ThreadLocalRandom.current().nextInt(online.size()));World world=anchor.getWorld();int radius=Math.max(16,plugin.getConfig().getInt("gameplay.supply-drops.radius",500));int x=anchor.getLocation().getBlockX()+ThreadLocalRandom.current().nextInt(-radius,radius+1),z=anchor.getLocation().getBlockZ()+ThreadLocalRandom.current().nextInt(-radius,radius+1);world.getChunkAtAsync(x>>4,z>>4).thenRun(()->plugin.sync(()->{Block top=world.getHighestBlockAt(x,z,HeightMap.MOTION_BLOCKING_NO_LEAVES);Block chestBlock=world.getBlockAt(x,top.getY()+1,z);if(!chestBlock.getType().isAir())chestBlock=world.getBlockAt(x,top.getY()+2,z);chestBlock.setType(Material.CHEST);if(chestBlock.getState() instanceof Chest chest){ConfigurationSection loot=plugin.getConfig().getConfigurationSection("gameplay.supply-drops.loot");if(loot!=null)for(String lootKey:loot.getKeys(false)){Material mat=Material.matchMaterial(lootKey);int amount=loot.getInt(lootKey);if(mat!=null&&amount>0)chest.getBlockInventory().addItem(new ItemStack(mat,amount));}}String message=plugin.getConfig().getString("gameplay.supply-drops.message","<gold><bold>SUPPLY DROP</bold></gold> <gray>A supply chest landed at approximately</gray> <yellow>%x%, %z%</yellow><gray> in %world%.</gray>").replace("%x%",Integer.toString(x)).replace("%z%",Integer.toString(z)).replace("%world%",Text.escapeMini(world.getName()));Bukkit.broadcast(Text.mm(message));}));}

    protected void runRewardCommands(Player player,String path){for(String command:plugin.getConfig().getStringList(path))Bukkit.dispatchCommand(Bukkit.getConsoleSender(),command.replace("%player%",player.getName()).replace("%streak%",Integer.toString(streaks.getOrDefault(player.getUniqueId(),0))));}
    protected Player playerDamager(Entity damager){if(damager instanceof Player p)return p;if(damager instanceof Projectile projectile){ProjectileSource source=projectile.getShooter();if(source instanceof Player p)return p;}return null;}
    protected record CoinflipChallenge(UUID challenger,long expiresAt){}
}
