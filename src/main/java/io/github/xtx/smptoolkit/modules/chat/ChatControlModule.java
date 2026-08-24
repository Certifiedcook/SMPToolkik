package io.github.xtx.smptoolkit.modules.chat;

import io.github.xtx.smptoolkit.SMPToolkitPlugin;
import io.github.xtx.smptoolkit.core.Module;
import io.github.xtx.smptoolkit.core.Text;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.net.URI;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ChatControlModule implements Module, Listener {
    private static final Pattern URL = Pattern.compile("(?i)\\b(?:https?://|www\\.)?([a-z0-9-]+(?:\\.[a-z0-9-]+)+)(?:/\\S*)?");
    private final SMPToolkitPlugin plugin;
    private final Map<UUID, ChatChannel> channels = new ConcurrentHashMap<>();
    private final Set<UUID> staffChat = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Long> lastChatAt = new ConcurrentHashMap<>();
    private final Map<UUID, LastMessage> lastMessages = new ConcurrentHashMap<>();
    private volatile int slowmodeSeconds;
    private volatile boolean globalMute;

    public ChatControlModule(SMPToolkitPlugin plugin) { this.plugin = plugin; }
    @Override public String name() { return "chat-control"; }

    @Override
    public void enable() {
        slowmodeSeconds = plugin.getConfig().getInt("chat.slowmode-seconds", 0);
        globalMute = plugin.getConfig().getBoolean("chat.globally-muted", false);
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @Override public void disable() {}

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        String plain = Text.plain(event.message());

        if (plugin.database().activePunishmentNow(player.getUniqueId(), "MUTE").isPresent()) {
            event.setCancelled(true);
            player.sendMessage(Text.mm("<red>You are muted.</red>"));
            return;
        }

        if (staffChat.contains(player.getUniqueId()) || channelOf(player) == ChatChannel.STAFF) {
            event.setCancelled(true);
            Component msg = Text.mm("<dark_gray>[</dark_gray><aqua>Staff</aqua><dark_gray>]</dark_gray> <white>" + Text.escapeMini(player.getName()) + "</white><gray>: </gray><white>" + Text.escapeMini(plain) + "</white>");
            for (Player target : Bukkit.getOnlinePlayers()) if (target.hasPermission("smp.staff.chat")) target.sendMessage(msg);
            Bukkit.getConsoleSender().sendMessage(msg);
            if (plugin.getConfig().getBoolean("discord.minecraft-chat-log.include-staff-chat", true))
                plugin.discord().sendMinecraftChat(player.getName(), "STAFF", plain);
            return;
        }

        if (globalMute && !player.hasPermission("smp.chat.admin")) {
            event.setCancelled(true);
            player.sendMessage(Text.mm("<red>Global chat is currently muted.</red>"));
            return;
        }

        long now = System.currentTimeMillis();
        if (slowmodeSeconds > 0 && !player.hasPermission("smp.chat.slow.bypass")) {
            long previous = lastChatAt.getOrDefault(player.getUniqueId(), 0L);
            long remaining = slowmodeSeconds * 1000L - (now - previous);
            if (remaining > 0) {
                event.setCancelled(true);
                player.sendMessage(Text.mm("<red>Slow mode: wait " + Math.max(1, (remaining + 999) / 1000) + "s.</red>"));
                return;
            }
        }

        if (plugin.getConfig().getBoolean("chat.filters.enabled", true) && !player.hasPermission("smp.chat.admin")) {
            if (isRepeated(player, plain, now)) {
                event.setCancelled(true);
                player.sendMessage(Text.mm("<red>Please do not repeat the same message.</red>"));
                return;
            }
            if (tooManyCaps(plain)) {
                event.setCancelled(true);
                player.sendMessage(Text.mm("<red>Please reduce excessive capital letters.</red>"));
                return;
            }
            if (containsBlockedLink(plain) && !player.hasPermission("smp.chat.links")) {
                event.setCancelled(true);
                player.sendMessage(Text.mm("<red>Links are not allowed in chat.</red>"));
                return;
            }
        }

        lastChatAt.put(player.getUniqueId(), now);
        lastMessages.put(player.getUniqueId(), new LastMessage(plain, now));

        ChatChannel channel = channelOf(player);
        if (channel == ChatChannel.LOCAL) {
            double radius = plugin.getConfig().getDouble("chat.local-radius", 100.0);
            double r2 = radius * radius;
            event.viewers().removeIf(audience -> {
                if (!(audience instanceof Player target)) return false;
                if (!target.getWorld().equals(player.getWorld())) return true;
                if (target.getLocation().distanceSquared(player.getLocation()) > r2) return true;
                return plugin.utilities() != null && plugin.utilities().isIgnoring(target.getUniqueId(), player.getUniqueId());
            });
        } else {
            event.viewers().removeIf(audience -> audience instanceof Player target && plugin.utilities() != null && plugin.utilities().isIgnoring(target.getUniqueId(), player.getUniqueId()));
        }

        event.renderer((source, displayName, message, viewer) -> render(source, channel, message));
    }

    private Component render(Player source, ChatChannel channel, Component message) {
        String format = plugin.getConfig().getString("chat.format", "<dark_gray>[<channel>]</dark_gray> <prefix><white><name></white><suffix><gray>: </gray><message>");
        String prefix = plugin.integrations().luckPerms().prefix(source);
        String suffix = plugin.integrations().luckPerms().suffix(source);
        String msg = Text.escapeMini(Text.plain(message));
        String assembled = format
                .replace("<channel>", channel.name())
                .replace("<prefix>", prefix == null ? "" : prefix)
                .replace("<suffix>", suffix == null ? "" : suffix)
                .replace("<name>", Text.escapeMini(source.getName()))
                .replace("<message>", msg);
        return MiniMessage.miniMessage().deserialize(assembled);
    }

    private boolean isRepeated(Player player, String message, long now) {
        int window = plugin.getConfig().getInt("chat.filters.repeat-window-seconds", 8);
        LastMessage old = lastMessages.get(player.getUniqueId());
        return old != null && now - old.time <= window * 1000L && old.text.equalsIgnoreCase(message.trim());
    }

    private boolean tooManyCaps(String message) {
        if (!plugin.getConfig().getBoolean("chat.filters.caps-enabled", true)) return false;
        int min = plugin.getConfig().getInt("chat.filters.caps-min-length", 10);
        if (message.length() < min) return false;
        int letters = 0, upper = 0;
        for (char c : message.toCharArray()) if (Character.isLetter(c)) { letters++; if (Character.isUpperCase(c)) upper++; }
        if (letters < min) return false;
        int pct = (int) Math.round(100.0 * upper / Math.max(1, letters));
        return pct >= plugin.getConfig().getInt("chat.filters.caps-max-percent", 75);
    }

    private boolean containsBlockedLink(String message) {
        if (!plugin.getConfig().getBoolean("chat.filters.links-enabled", true)) return false;
        Matcher matcher = URL.matcher(message);
        List<String> whitelist = plugin.getConfig().getStringList("chat.filters.link-whitelist");
        while (matcher.find()) {
            String domain = matcher.group(1).toLowerCase(Locale.ROOT);
            boolean allowed = whitelist.stream().map(s -> s.toLowerCase(Locale.ROOT)).anyMatch(w -> domain.equals(w) || domain.endsWith("." + w));
            if (!allowed) return true;
        }
        return false;
    }

    public ChatChannel channelOf(Player player) {
        if (staffChat.contains(player.getUniqueId())) return ChatChannel.STAFF;
        return channels.getOrDefault(player.getUniqueId(), ChatChannel.GLOBAL);
    }

    public boolean handle(CommandSender sender, String label, String[] args) {
        switch (label.toLowerCase(Locale.ROOT)) {
            case "staffchat", "sc" -> {
                if (!(sender instanceof Player p)) { sender.sendMessage("Players only."); return true; }
                if (args.length > 0) {
                    Component msg = Text.mm("<dark_gray>[</dark_gray><aqua>Staff</aqua><dark_gray>]</dark_gray> <white>" + Text.escapeMini(p.getName()) + "</white><gray>: </gray><white>" + Text.escapeMini(Text.join(args,0)) + "</white>");
                    for (Player target : Bukkit.getOnlinePlayers()) if (target.hasPermission("smp.staff.chat")) target.sendMessage(msg);
                    Bukkit.getConsoleSender().sendMessage(msg);
                    if (plugin.getConfig().getBoolean("discord.minecraft-chat-log.include-staff-chat", true))
                        plugin.discord().sendMinecraftChat(p.getName(), "STAFF", Text.join(args,0));
                } else {
                    boolean enabled = !staffChat.remove(p.getUniqueId());
                    if (enabled) staffChat.add(p.getUniqueId());
                    p.sendMessage(Text.mm(enabled ? "<aqua>Staff chat enabled.</aqua>" : "<gray>Staff chat disabled.</gray>"));
                }
                return true;
            }
            case "channel" -> {
                if (!(sender instanceof Player p)) { sender.sendMessage("Players only."); return true; }
                if (args.length == 0) { p.sendMessage(Text.mm("<gray>Channel:</gray> <yellow>" + channelOf(p).name() + "</yellow>")); return true; }
                try {
                    ChatChannel c = ChatChannel.valueOf(args[0].toUpperCase(Locale.ROOT));
                    if (c == ChatChannel.STAFF && !p.hasPermission("smp.staff.chat")) { p.sendMessage(Text.mm("<red>No permission.</red>")); return true; }
                    channels.put(p.getUniqueId(), c); staffChat.remove(p.getUniqueId());
                    p.sendMessage(Text.mm("<green>Chat channel set to " + c.name() + ".</green>"));
                } catch (IllegalArgumentException ex) {
                    p.sendMessage(Text.mm("<red>Channels: global, local, trade" + (p.hasPermission("smp.staff.chat") ? ", staff" : "") + ".</red>"));
                }
                return true;
            }
            case "chat" -> {
                if (args.length == 0) { sender.sendMessage(Text.mm("<yellow>/chat clear | mute | unmute | slow <seconds></yellow>")); return true; }
                switch (args[0].toLowerCase(Locale.ROOT)) {
                    case "clear" -> {
                        for (Player p : Bukkit.getOnlinePlayers()) { for (int i=0;i<80;i++) p.sendMessage(Component.empty()); }
                        Bukkit.broadcast(Text.mm("<gray>Chat was cleared by <white>" + Text.escapeMini(sender.getName()) + "</white>.</gray>"));
                    }
                    case "mute" -> { globalMute = true; plugin.getConfig().set("chat.globally-muted", true); plugin.saveConfig(); Bukkit.broadcast(Text.mm("<red>Global chat has been muted.</red>")); }
                    case "unmute" -> { globalMute = false; plugin.getConfig().set("chat.globally-muted", false); plugin.saveConfig(); Bukkit.broadcast(Text.mm("<green>Global chat has been unmuted.</green>")); }
                    case "slow" -> {
                        if (args.length < 2) { sender.sendMessage(Text.mm("<red>Usage: /chat slow <seconds></red>")); break; }
                        try { slowmodeSeconds = Math.max(0, Integer.parseInt(args[1])); plugin.getConfig().set("chat.slowmode-seconds", slowmodeSeconds); plugin.saveConfig(); Bukkit.broadcast(Text.mm("<gray>Chat slow mode: <yellow>" + slowmodeSeconds + "s</yellow>.</gray>")); }
                        catch (NumberFormatException ex) { sender.sendMessage(Text.mm("<red>Seconds must be a number.</red>")); }
                    }
                    default -> sender.sendMessage(Text.mm("<red>Unknown chat subcommand.</red>"));
                }
                return true;
            }
        }
        return false;
    }

    private record LastMessage(String text, long time) {}
}
