package io.github.xtx.smptoolkit.integrations;

import io.github.xtx.smptoolkit.SMPToolkitPlugin;
import org.bukkit.Bukkit;

public final class Integrations {
    private final SMPToolkitPlugin plugin;
    private final LuckPermsHook luckPerms;

    public Integrations(SMPToolkitPlugin plugin) {
        this.plugin = plugin;
        this.luckPerms = new LuckPermsHook(plugin);
    }

    public void enable() {
        luckPerms.hook();
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new SMPPlaceholderExpansion(plugin).register();
            plugin.getLogger().info("PlaceholderAPI expansion registered: %smp_*%.");
        }
    }

    public LuckPermsHook luckPerms() { return luckPerms; }
}
