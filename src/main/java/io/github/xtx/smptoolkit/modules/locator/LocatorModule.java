package io.github.xtx.smptoolkit.modules.locator;

import io.github.xtx.smptoolkit.SMPToolkitPlugin;
import io.github.xtx.smptoolkit.core.Module;
import io.github.xtx.smptoolkit.core.Text;
import org.bukkit.Bukkit;
import org.bukkit.GameRules;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.scheduler.BukkitTask;

import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;

public final class LocatorModule implements Module {
    private final SMPToolkitPlugin plugin;
    private BukkitTask task;
    private Boolean manualOverride;
    private volatile boolean eventOverride;
    private volatile boolean lastState;
    private boolean initialized;

    public LocatorModule(SMPToolkitPlugin plugin) { this.plugin = plugin; }
    @Override public String name() { return "locator"; }

    @Override
    public void enable() {
        apply();
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::apply, 20L * 30, 20L * 30);
    }

    @Override
    public void disable() {
        if (task != null) task.cancel();
        eventOverride = false;
    }

    public boolean isActive() {
        if (eventOverride) return true;
        if (manualOverride != null) return manualOverride;
        return scheduledActive();
    }

    public void setEventOverride(boolean active) {
        eventOverride = active;
        apply();
    }

    public boolean handle(CommandSender sender, String label, String[] args) {
        if (!label.equalsIgnoreCase("locator")) return false;
        if (args.length == 0 || args[0].equalsIgnoreCase("status")) {
            sender.sendMessage(Text.mm("<gold>Locator Bar:</gold> " + (isActive()?"<green>ON</green>":"<red>OFF</red>") + " <dark_gray>(mode: " + mode() + ")</dark_gray>"));
            sender.sendMessage(Text.mm("<gray>Schedule:</gray> <white>" + Text.escapeMini(plugin.getConfig().getString("locator.start","17:30")) + "–" + Text.escapeMini(plugin.getConfig().getString("locator.end","18:30")) + " " + Text.escapeMini(plugin.getConfig().getString("locator.timezone","Europe/Dublin")) + "</white>"));
            return true;
        }
        if (!sender.hasPermission("smp.events.admin") && !sender.hasPermission("smp.admin")) {
            sender.sendMessage(Text.mm("<red>No permission.</red>"));
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "on" -> manualOverride = true;
            case "off" -> manualOverride = false;
            case "auto" -> manualOverride = null;
            default -> { sender.sendMessage(Text.mm("<red>Usage: /locator <status|on|off|auto></red>")); return true; }
        }
        apply();
        sender.sendMessage(Text.mm("<green>Locator mode set to " + mode() + ".</green>"));
        return true;
    }

    private String mode() {
        if (eventOverride) return "event";
        if (manualOverride == null) return "automatic";
        return manualOverride ? "forced-on" : "forced-off";
    }

    private boolean scheduledActive() {
        try {
            ZoneId zone = ZoneId.of(plugin.getConfig().getString("locator.timezone", "Europe/Dublin"));
            LocalTime now = ZonedDateTime.now(zone).toLocalTime();
            LocalTime start = LocalTime.parse(plugin.getConfig().getString("locator.start", "17:30"));
            LocalTime end = LocalTime.parse(plugin.getConfig().getString("locator.end", "18:30"));
            if (start.equals(end)) return true;
            if (start.isBefore(end)) return !now.isBefore(start) && now.isBefore(end);
            return !now.isBefore(start) || now.isBefore(end);
        } catch (DateTimeParseException | java.time.zone.ZoneRulesException ex) {
            plugin.getLogger().warning("Invalid locator schedule/timezone: " + ex.getMessage());
            return false;
        }
    }

    private void apply() {
        boolean state = isActive();
        List<String> configured = plugin.getConfig().getStringList("locator.worlds");
        for (String name : configured) {
            World world = Bukkit.getWorld(name);
            if (world != null) world.setGameRule(GameRules.LOCATOR_BAR, state);
        }
        if (initialized && state != lastState && plugin.getConfig().getBoolean("locator.announce-toggle", true)) {
            Bukkit.broadcast(Text.mm(state ? "<aqua><bold>Locator Hour is now active.</bold></aqua>" : "<gray>Locator Hour has ended.</gray>"));
        }
        initialized = true;
        lastState = state;
    }
}
