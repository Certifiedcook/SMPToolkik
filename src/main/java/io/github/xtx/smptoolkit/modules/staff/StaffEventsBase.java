package io.github.xtx.smptoolkit.modules.staff;

import io.github.xtx.smptoolkit.SMPToolkitPlugin;
import io.github.xtx.smptoolkit.core.Module;
import io.github.xtx.smptoolkit.core.Text;
import io.github.xtx.smptoolkit.data.Database;
import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;

class StaffEventsBase extends StaffState {
    StaffEventsBase(SMPToolkitPlugin plugin) { super(plugin); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!frozen.contains(event.getPlayer().getUniqueId()) || event.getPlayer().hasPermission("smp.freeze.bypass")) return;
        if (event.hasChangedPosition()) event.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) { if (isActuallyFrozen(event.getPlayer())) event.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) { if (isActuallyFrozen(event.getPlayer())) event.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) { if (isActuallyFrozen(event.getPlayer())) event.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) { if (isActuallyFrozen(event.getPlayer())) event.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventory(InventoryClickEvent event) { if (event.getWhoClicked() instanceof Player p && isActuallyFrozen(p)) event.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) { if (event.getEntity() instanceof Player p && isActuallyFrozen(p)) event.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) { if (event.getWhoClicked() instanceof Player p && isActuallyFrozen(p)) event.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) { if (isActuallyFrozen(event.getPlayer())) event.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent event) { if (isActuallyFrozen(event.getPlayer())) event.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onProjectile(ProjectileLaunchEvent event) {
        if (event.getEntity().getShooter() instanceof Player p && isActuallyFrozen(p)) event.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (isActuallyFrozen(event.getPlayer()) && plugin.getConfig().getBoolean("staff.freeze.block-teleports", true) && event.getCause() != PlayerTeleportEvent.TeleportCause.PLUGIN) event.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player p && (god.contains(p.getUniqueId()) || isActuallyFrozen(p))) event.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamageBy(EntityDamageByEntityEvent event) {
        Entity damager = event.getDamager();
        if (damager instanceof Player p && isActuallyFrozen(p)) event.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFrozenCommand(PlayerCommandPreprocessEvent event) {
        Player p = event.getPlayer();
        if (!isActuallyFrozen(p) || !plugin.getConfig().getBoolean("staff.freeze.block-all-commands", true)) return;
        String cmd = event.getMessage().substring(1).split(" ",2)[0].toLowerCase(Locale.ROOT);
        if (plugin.getConfig().getStringList("staff.freeze.allowed-commands").stream().anyMatch(s -> s.equalsIgnoreCase(cmd))) return;
        event.setCancelled(true);
        p.sendMessage(Text.mm("<red>You cannot use commands while frozen.</red>"));
    }
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player p = event.getPlayer();
        if (frozen.contains(p.getUniqueId()) && plugin.getConfig().getBoolean("staff.freeze.disconnect-alert", true)) {
            staffBroadcast(Text.mm("<red><bold>FREEZE</bold></red> <yellow>" + Text.escapeMini(p.getName()) + " disconnected while frozen.</yellow>"));
            plugin.discord().sendStaffAudit(p.getName() + " disconnected while frozen");
        }
        if (vanished.contains(p.getUniqueId()) && plugin.getConfig().getBoolean("staff.vanish.hide-join-leave", true)) event.quitMessage(null);
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        if (vanished.contains(p.getUniqueId()) && plugin.getConfig().getBoolean("staff.vanish.hide-join-leave", true)) event.joinMessage(null);
        for (UUID id : vanished) {
            Player hidden = Bukkit.getPlayer(id);
            if (hidden != null && !p.hasPermission("smp.staff.vanish")) p.hidePlayer(plugin, hidden);
        }
        if (frozen.contains(p.getUniqueId())) p.sendMessage(Text.mm("<red><bold>You are still frozen.</bold></red> <gray>Wait for staff instructions.</gray>"));
    }
    @SuppressWarnings("deprecation")
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onLogin(PlayerLoginEvent event) {
        if (!plugin.getConfig().getBoolean("staff.maintenance.enabled", false)) return;
        if (event.getPlayer().hasPermission("smp.maintenance.bypass")) return;
        event.disallow(PlayerLoginEvent.Result.KICK_OTHER, Text.mm(plugin.getConfig().getString("staff.maintenance.kick-message", "<red>Server maintenance is enabled.</red>")));
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onGuiClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player staff)) return;
        UUID targetId = guiTargets.get(staff.getUniqueId());
        if (targetId == null || !Text.plain(event.getView().title()).startsWith("Staff: ")) return;
        event.setCancelled(true);
        Player target = Bukkit.getPlayer(targetId);
        if (target == null) { staff.closeInventory(); staff.sendMessage(Text.mm("<red>Target is offline.</red>")); return; }
        switch (event.getRawSlot()) {
            case 10 -> staff.teleport(target.getLocation());
            case 11 -> toggleFreeze(staff, target);
            case 12 -> staff.openInventory(target.getInventory());
            case 13 -> staff.openInventory(target.getEnderChest());
            case 14 -> toggleVanish(target);
            case 15 -> showPlayerInfo(staff, target);
            case 16 -> target.kick(Text.mm("<red>Kicked by staff.</red>"));
            default -> { return; }
        }
    }
    private boolean isActuallyFrozen(Player p) { return frozen.contains(p.getUniqueId()) && !p.hasPermission("smp.freeze.bypass"); }
}
