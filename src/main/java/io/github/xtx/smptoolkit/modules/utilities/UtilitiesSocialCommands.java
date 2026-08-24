package io.github.xtx.smptoolkit.modules.utilities;

import io.github.xtx.smptoolkit.SMPToolkitPlugin;
import io.github.xtx.smptoolkit.core.Text;
import io.github.xtx.smptoolkit.data.Database;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

class UtilitiesSocialCommands extends UtilitiesTravelCommands {
    UtilitiesSocialCommands(SMPToolkitPlugin plugin) { super(plugin); }
    protected boolean msg(CommandSender sender, String[] args) {
        if (!(sender instanceof Player p)) { sender.sendMessage("Players only."); return true; }
        if (args.length < 2) { p.sendMessage(Text.mm("<red>Usage: /msg <player> <message></red>")); return true; }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) { p.sendMessage(Text.mm("<red>Player not found.</red>")); return true; }
        if (isIgnoring(target.getUniqueId(), p.getUniqueId())) { p.sendMessage(Text.mm("<red>That player is ignoring you.</red>")); return true; }
        String message = Text.escapeMini(Text.join(args,1));
        p.sendMessage(Text.mm("<gray>[You → " + Text.escapeMini(target.getName()) + "]</gray> <white>" + message + "</white>"));
        target.sendMessage(Text.mm("<gray>[" + Text.escapeMini(p.getName()) + " → You]</gray> <white>" + message + "</white>"));
        if (plugin.getConfig().getBoolean("discord.minecraft-chat-log.include-private-messages", true))
            plugin.discord().sendMinecraftChat(p.getName(), "PRIVATE → " + target.getName(), Text.join(args,1));
        lastMessagePartner.put(p.getUniqueId(), target.getUniqueId());
        lastMessagePartner.put(target.getUniqueId(), p.getUniqueId());
        return true;
    }
    protected boolean reply(CommandSender sender, String[] args) {
        if (!(sender instanceof Player p)) { sender.sendMessage("Players only."); return true; }
        UUID id = lastMessagePartner.get(p.getUniqueId()); Player target = id == null ? null : Bukkit.getPlayer(id);
        if (target == null) { p.sendMessage(Text.mm("<red>No online player to reply to.</red>")); return true; }
        if (args.length < 1) { p.sendMessage(Text.mm("<red>Usage: /r <message></red>")); return true; }
        String[] forwarded = new String[args.length+1]; forwarded[0]=target.getName(); System.arraycopy(args,0,forwarded,1,args.length);
        return msg(sender, forwarded);
    }
    protected boolean ignore(CommandSender sender, String[] args) {
        if (!(sender instanceof Player p)) { sender.sendMessage("Players only."); return true; }
        if (args.length < 1) { p.sendMessage(Text.mm("<red>Usage: /ignore <player></red>")); return true; }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null || target.equals(p)) { p.sendMessage(Text.mm("<red>Player not found.</red>")); return true; }
        plugin.database().toggleIgnore(p.getUniqueId(), target.getUniqueId()).thenAccept(ignored -> {
            Set<UUID> set = ignoreCache.computeIfAbsent(p.getUniqueId(), k -> ConcurrentHashMap.newKeySet());
            if (ignored) set.add(target.getUniqueId()); else set.remove(target.getUniqueId());
            plugin.sync(() -> p.sendMessage(Text.mm(ignored ? "<gray>You are now ignoring " + Text.escapeMini(target.getName()) + ".</gray>" : "<green>You are no longer ignoring " + Text.escapeMini(target.getName()) + ".</green>")));
        });
        return true;
    }
    protected boolean seen(CommandSender sender, String[] args) {
        if (args.length < 1) { sender.sendMessage(Text.mm("<red>Usage: /seen <player></red>")); return true; }
        Player online = Bukkit.getPlayerExact(args[0]);
        if (online != null) { sender.sendMessage(Text.mm("<green>" + Text.escapeMini(online.getName()) + " is online now.</green>")); return true; }
        plugin.database().findPlayerByName(args[0]).thenAccept(opt -> plugin.sync(() -> {
            if (opt.isEmpty()) { sender.sendMessage(Text.mm("<red>Unknown player.</red>")); return; }
            Database.PlayerLookup p = opt.get();
            sender.sendMessage(Text.mm("<gray>" + Text.escapeMini(p.name()) + " was last seen:</gray> <yellow>" + DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm z").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(p.lastJoin())) + "</yellow>"));
        }));
        return true;
    }
    protected boolean realname(CommandSender sender, String[] args) {
        if (args.length < 1) { sender.sendMessage(Text.mm("<red>Usage: /realname <name></red>")); return true; }
        String needle = args[0].toLowerCase(Locale.ROOT);
        List<String> matches = Bukkit.getOnlinePlayers().stream().map(Player::getName).filter(n -> n.toLowerCase(Locale.ROOT).contains(needle)).toList();
        sender.sendMessage(Text.mm(matches.isEmpty() ? "<red>No online match.</red>" : "<gray>Matches:</gray> <yellow>" + Text.escapeMini(String.join(", ", matches)) + "</yellow>"));
        return true;
    }
    protected boolean afk(CommandSender sender) {
        if (!(sender instanceof Player p)) { sender.sendMessage("Players only."); return true; }
        boolean nowAfk = !afk.remove(p.getUniqueId()); if (nowAfk) afk.add(p.getUniqueId());
        Bukkit.broadcast(Text.mm(nowAfk ? "<gray>" + Text.escapeMini(p.getName()) + " is now AFK.</gray>" : "<gray>" + Text.escapeMini(p.getName()) + " is no longer AFK.</gray>"));
        return true;
    }
}
