package io.github.xtx.smptoolkit.integrations;

import io.github.xtx.smptoolkit.SMPToolkitPlugin;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.user.User;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public final class LuckPermsHook {
    private final SMPToolkitPlugin plugin;
    private LuckPerms luckPerms;

    public LuckPermsHook(SMPToolkitPlugin plugin) {
        this.plugin = plugin;
    }

    public void hook() {
        if (Bukkit.getPluginManager().getPlugin("LuckPerms") == null) {
            plugin.getLogger().info("LuckPerms not found; chat prefixes/suffixes will be blank.");
            return;
        }
        luckPerms = Bukkit.getServicesManager().load(LuckPerms.class);
        plugin.getLogger().info(luckPerms == null ? "LuckPerms plugin found but API service unavailable." : "LuckPerms integration enabled.");
    }

    public boolean available() { return luckPerms != null; }

    public String prefix(Player player) {
        User user = user(player);
        if (user == null) return "";
        String value = user.getCachedData().getMetaData().getPrefix();
        return value == null ? "" : value;
    }

    public String suffix(Player player) {
        User user = user(player);
        if (user == null) return "";
        String value = user.getCachedData().getMetaData().getSuffix();
        return value == null ? "" : value;
    }

    public String primaryGroup(Player player) {
        User user = user(player);
        return user == null ? "default" : user.getPrimaryGroup();
    }

    private User user(Player player) {
        return luckPerms == null ? null : luckPerms.getUserManager().getUser(player.getUniqueId());
    }
}
