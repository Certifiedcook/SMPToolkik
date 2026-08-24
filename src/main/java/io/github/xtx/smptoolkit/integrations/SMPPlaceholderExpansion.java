package io.github.xtx.smptoolkit.integrations;

import io.github.xtx.smptoolkit.SMPToolkitPlugin;
import io.github.xtx.smptoolkit.data.Database;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class SMPPlaceholderExpansion extends PlaceholderExpansion {
    private final SMPToolkitPlugin plugin;

    public SMPPlaceholderExpansion(SMPToolkitPlugin plugin) {
        this.plugin = plugin;
    }

    @Override public @NotNull String getIdentifier() { return "smp"; }
    @Override public @NotNull String getAuthor() { return "XTX"; }
    @Override public @NotNull String getVersion() { return plugin.getPluginMeta().getVersion(); }
    @Override public boolean persist() { return true; }

    @Override
    public @Nullable String onRequest(OfflinePlayer player, @NotNull String params) {
        if (player == null || !player.hasPlayedBefore()) return "0";
        Database.Stats s = plugin.stats().cached(player.getUniqueId());
        return switch (params.toLowerCase()) {
            case "kills" -> Integer.toString(s.kills());
            case "deaths" -> Integer.toString(s.deaths());
            case "kd" -> String.format("%.2f", s.kd());
            case "playtime" -> io.github.xtx.smptoolkit.core.Text.formatDuration(s.playtimeMs());
            case "chatgames_wins" -> Integer.toString(s.chatWins());
            case "locator_active" -> plugin.locator().isActive() ? "true" : "false";
            case "combat_time" -> Integer.toString(plugin.gameplay().combatSeconds(player.getUniqueId()));
            default -> null;
        };
    }
}
