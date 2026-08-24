package io.github.xtx.smptoolkit.modules.staff;

import io.github.xtx.smptoolkit.SMPToolkitPlugin;
import io.github.xtx.smptoolkit.core.Module;
import org.bukkit.Bukkit;
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

public final class StaffModule extends StaffCommandsBase implements Module, Listener {
    public StaffModule(SMPToolkitPlugin plugin) { super(plugin); }
    @Override public void enable() { super.enable(); Bukkit.getPluginManager().registerEvents(this, plugin); }
    @Override @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true) public void onMove(PlayerMoveEvent e){super.onMove(e);}
    @Override @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true) public void onInteract(PlayerInteractEvent e){super.onInteract(e);}
    @Override @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true) public void onBreak(BlockBreakEvent e){super.onBreak(e);}
    @Override @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true) public void onPlace(BlockPlaceEvent e){super.onPlace(e);}
    @Override @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true) public void onDrop(PlayerDropItemEvent e){super.onDrop(e);}
    @Override @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true) public void onInventory(InventoryClickEvent e){super.onInventory(e);}
    @Override @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true) public void onPickup(EntityPickupItemEvent e){super.onPickup(e);}
    @Override @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true) public void onDrag(InventoryDragEvent e){super.onDrag(e);}
    @Override @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true) public void onInteractEntity(PlayerInteractEntityEvent e){super.onInteractEntity(e);}
    @Override @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true) public void onSwap(PlayerSwapHandItemsEvent e){super.onSwap(e);}
    @Override @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true) public void onProjectile(ProjectileLaunchEvent e){super.onProjectile(e);}
    @Override @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true) public void onTeleport(PlayerTeleportEvent e){super.onTeleport(e);}
    @Override @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true) public void onDamage(EntityDamageEvent e){super.onDamage(e);}
    @Override @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true) public void onDamageBy(EntityDamageByEntityEvent e){super.onDamageBy(e);}
    @Override @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true) public void onFrozenCommand(PlayerCommandPreprocessEvent e){super.onFrozenCommand(e);}
    @Override @EventHandler public void onQuit(PlayerQuitEvent e){super.onQuit(e);}
    @Override @EventHandler(priority = EventPriority.HIGHEST) public void onJoin(PlayerJoinEvent e){super.onJoin(e);}
    @SuppressWarnings("deprecation") @Override @EventHandler(priority = EventPriority.HIGHEST) public void onLogin(PlayerLoginEvent e){super.onLogin(e);}
    @Override @EventHandler(priority = EventPriority.HIGHEST) public void onGuiClick(InventoryClickEvent e){super.onGuiClick(e);}
}
