package io.github.xtx.smptoolkit.modules.utilities;

import io.github.xtx.smptoolkit.SMPToolkitPlugin;
import io.github.xtx.smptoolkit.core.Locations;
import io.github.xtx.smptoolkit.core.Text;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

class UtilitiesTravelCommands extends UtilitiesEventsBase {
    UtilitiesTravelCommands(SMPToolkitPlugin plugin) { super(plugin); }
    protected boolean tpa(CommandSender sender, String[] args) {
        if (!(sender instanceof Player p)) { sender.sendMessage("Players only."); return true; }
        if (args.length < 1) { p.sendMessage(Text.mm("<red>Usage: /tpa <player></red>")); return true; }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null || target.equals(p)) { p.sendMessage(Text.mm("<red>Player not found.</red>")); return true; }
        if (tpaDisabled.contains(target.getUniqueId())) { p.sendMessage(Text.mm("<red>That player has TPA disabled.</red>")); return true; }
        long expiry = System.currentTimeMillis() + plugin.getConfig().getLong("utilities.tpa-expiry-seconds",60)*1000L;
        tpaIncoming.put(target.getUniqueId(), new TpaRequest(p.getUniqueId(), expiry));
        p.sendMessage(Text.mm("<green>Teleport request sent to " + Text.escapeMini(target.getName()) + ".</green>"));
        target.sendMessage(Text.mm("<yellow>" + Text.escapeMini(p.getName()) + " wants to teleport to you.</yellow> <gray>Use /tpaccept or /tpdeny.</gray>"));
        return true;
    }
    protected boolean tpaccept(CommandSender sender) {
        if (!(sender instanceof Player target)) { sender.sendMessage("Players only."); return true; }
        TpaRequest req = tpaIncoming.remove(target.getUniqueId());
        if (req == null || req.expiresAt() < System.currentTimeMillis()) { target.sendMessage(Text.mm("<red>No active teleport request.</red>")); return true; }
        Player requester = Bukkit.getPlayer(req.requester());
        if (requester == null) { target.sendMessage(Text.mm("<red>Requester is offline.</red>")); return true; }
        warmup(requester, () -> teleportInternal(requester, target.getLocation()), "Teleporting to " + target.getName());
        return true;
    }
    protected boolean tpdeny(CommandSender sender) {
        if (!(sender instanceof Player p)) { sender.sendMessage("Players only."); return true; }
        TpaRequest req = tpaIncoming.remove(p.getUniqueId());
        if (req == null) p.sendMessage(Text.mm("<red>No active teleport request.</red>"));
        else {
            p.sendMessage(Text.mm("<gray>Teleport request denied.</gray>"));
            Player r = Bukkit.getPlayer(req.requester()); if (r != null) r.sendMessage(Text.mm("<red>Your teleport request was denied.</red>"));
        }
        return true;
    }
    protected boolean tptoggle(CommandSender sender) {
        if (!(sender instanceof Player p)) { sender.sendMessage("Players only."); return true; }
        boolean disabled = !tpaDisabled.remove(p.getUniqueId());
        if (disabled) tpaDisabled.add(p.getUniqueId());
        p.sendMessage(Text.mm(disabled ? "<gray>TPA requests disabled.</gray>" : "<green>TPA requests enabled.</green>"));
        return true;
    }
    protected boolean sethome(CommandSender sender, String[] args) {
        if (!(sender instanceof Player p)) { sender.sendMessage("Players only."); return true; }
        String name = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "home";
        plugin.database().homes(p.getUniqueId()).thenAccept(homes -> plugin.sync(() -> {
            int limit = homeLimit(p);
            if (!homes.contains(name) && homes.size() >= limit) { p.sendMessage(Text.mm("<red>You have reached your home limit of " + limit + ".</red>")); return; }
            plugin.database().setHome(p.getUniqueId(), name, Locations.encode(p.getLocation()));
            p.sendMessage(Text.mm("<green>Home <yellow>" + Text.escapeMini(name) + "</yellow> set.</green>"));
        }));
        return true;
    }
    protected boolean home(CommandSender sender, String[] args) {
        if (!(sender instanceof Player p)) { sender.sendMessage("Players only."); return true; }
        String name = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "home";
        plugin.database().home(p.getUniqueId(), name).thenAccept(opt -> plugin.sync(() -> {
            Location loc = opt.map(Locations::decode).orElse(null);
            if (loc == null) { p.sendMessage(Text.mm("<red>Home not found.</red>")); return; }
            warmup(p, () -> teleportInternal(p, loc), "Teleporting home");
        }));
        return true;
    }
    protected boolean delhome(CommandSender sender, String[] args) {
        if (!(sender instanceof Player p)) { sender.sendMessage("Players only."); return true; }
        if (args.length < 1) { p.sendMessage(Text.mm("<red>Usage: /delhome <name></red>")); return true; }
        plugin.database().deleteHome(p.getUniqueId(), args[0]).thenAccept(ok -> plugin.sync(() -> p.sendMessage(Text.mm(ok ? "<green>Home deleted.</green>" : "<red>Home not found.</red>"))));
        return true;
    }
    protected boolean homes(CommandSender sender) {
        if (!(sender instanceof Player p)) { sender.sendMessage("Players only."); return true; }
        plugin.database().homes(p.getUniqueId()).thenAccept(list -> plugin.sync(() -> p.sendMessage(Text.mm("<gray>Homes:</gray> <yellow>" + Text.escapeMini(String.join(", ",list)) + "</yellow>"))));
        return true;
    }
    protected int homeLimit(Player p) {
        if (p.hasPermission("smp.homes.unlimited")) return 999;
        if (p.hasPermission("smp.homes.5")) return 5;
        if (p.hasPermission("smp.homes.3")) return 3;
        return plugin.getConfig().getInt("utilities.homes-default-limit",1);
    }
    protected boolean setspawn(CommandSender sender) {
        if (!(sender instanceof Player p)) { sender.sendMessage("Players only."); return true; }
        plugin.getConfig().set("utilities.spawn", Locations.encode(p.getLocation())); plugin.saveConfig();
        p.sendMessage(Text.mm("<green>Server spawn set.</green>")); return true;
    }
    protected boolean spawn(CommandSender sender) {
        if (!(sender instanceof Player p)) { sender.sendMessage("Players only."); return true; }
        Location loc = configuredSpawn();
        if (loc == null) { p.sendMessage(Text.mm("<red>Server spawn is not configured.</red>")); return true; }
        warmup(p, () -> teleportInternal(p, loc), "Teleporting to spawn"); return true;
    }
    protected boolean back(CommandSender sender) {
        if (!(sender instanceof Player p)) { sender.sendMessage("Players only."); return true; }
        plugin.database().location(p.getUniqueId(), "back").thenAccept(opt -> plugin.sync(() -> {
            Location loc = opt.map(Locations::decode).orElse(null);
            if (loc == null) { p.sendMessage(Text.mm("<red>No back location recorded.</red>")); return; }
            warmup(p, () -> teleportInternal(p, loc), "Returning to previous location");
        }));
        return true;
    }
    protected boolean rtp(CommandSender sender) {
        if (!(sender instanceof Player p)) { sender.sendMessage("Players only."); return true; }
        warmup(p, () -> attemptRtp(p, 0), "Random teleport"); return true;
    }
    protected void attemptRtp(Player p, int attempt) {
        if (!p.isOnline()) return;
        if (attempt >= 20) { p.sendMessage(Text.mm("<red>Could not find a safe random location.</red>")); return; }
        World world = Bukkit.getWorld(plugin.getConfig().getString("gameplay.rtp.world", "world"));
        if (world == null) { p.sendMessage(Text.mm("<red>RTP world is unavailable.</red>")); return; }
        int min = plugin.getConfig().getInt("gameplay.rtp.min-radius",250), max = plugin.getConfig().getInt("gameplay.rtp.max-radius",5000);
        double angle = ThreadLocalRandom.current().nextDouble(Math.PI*2);
        double radius = Math.sqrt(ThreadLocalRandom.current().nextDouble(min*min, (double)max*max));
        int x=(int)Math.round(Math.cos(angle)*radius), z=(int)Math.round(Math.sin(angle)*radius);
        world.getChunkAtAsync(x >> 4, z >> 4).thenRun(() -> plugin.sync(() -> {
            Block top = world.getHighestBlockAt(x,z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
            Material floor = top.getType();
            if (!floor.isSolid() || Set.of(Material.WATER,Material.LAVA,MATERIAL_CACTUS()).contains(floor)) { attemptRtp(p,attempt+1); return; }
            Location loc = new Location(world,x+0.5,top.getY()+1,z+0.5,p.getYaw(),p.getPitch());
            teleportInternal(p,loc); p.sendMessage(Text.mm("<green>Randomly teleported to <yellow>"+x+", "+z+"</yellow>.</green>"));
        }));
    }
    protected Material MATERIAL_CACTUS() { return Material.CACTUS; }
    protected void warmup(Player p, Runnable action, String label) {
        int seconds = plugin.getConfig().getInt("utilities.teleport-warmup-seconds", plugin.getConfig().getInt("gameplay.rtp.warmup-seconds",5));
        if (seconds <= 0) { action.run(); return; }
        Location origin = p.getLocation().clone();
        p.sendMessage(Text.mm("<yellow>" + Text.escapeMini(label) + " in " + seconds + "s. Do not move.</yellow>"));
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!p.isOnline() || p.getWorld()!=origin.getWorld() || p.getLocation().distanceSquared(origin) > 0.04) { p.sendMessage(Text.mm("<red>Teleport cancelled because you moved.</red>")); return; }
            action.run();
        }, seconds*20L);
    }
}
