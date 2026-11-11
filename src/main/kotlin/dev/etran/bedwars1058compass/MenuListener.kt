package dev.etran.bedwars1058compass

import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryDragEvent

/** Click handling for the Tracker Shop and the player selection menu. */
class MenuListener(private val plugin: BedWars1058Compass) : Listener {

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onClick(e: InventoryClickEvent) {
        val holder = e.inventory.holder as? CompassMenu ?: return
        e.isCancelled = true // nothing may ever be taken out of, or put into, our menus
        val player = e.whoClicked as? Player ?: return
        if (e.clickedInventory != e.inventory) return

        if (!plugin.matches.isActivePlayer(player)) {
            player.closeInventory()
            return
        }

        when (holder) {
            is ShopMenu -> when (e.rawSlot) {
                TrackerMenus.SHOP_TRACKER_SLOT -> {
                    if (plugin.menus.canAfford(player)) {
                        plugin.menus.openTargets(player)
                    } else {
                        player.sendMessage(plugin.settings.msg("not-enough", "cost" to plugin.settings.costText()))
                        player.closeInventory()
                    }
                }
                TrackerMenus.SHOP_STOP_SLOT -> {
                    if (plugin.trackers.isTracking(player)) {
                        plugin.trackers.stop(player, plugin.settings.msg("stopped"))
                    }
                    player.closeInventory()
                }
            }

            is TargetMenu -> {
                val targetId = holder.slots[e.rawSlot] ?: return
                val target = plugin.server.getPlayer(targetId)
                val valid = target != null && plugin.matches.trackableTargets(player).contains(target)
                if (!valid) {
                    player.sendMessage(plugin.settings.msg("target-invalid"))
                    player.closeInventory()
                    return
                }
                if (!plugin.menus.charge(player)) {
                    player.sendMessage(plugin.settings.msg("not-enough", "cost" to plugin.settings.costText()))
                    player.closeInventory()
                    return
                }
                plugin.trackers.track(player, target!!)
                player.closeInventory()
                player.sendMessage(
                    plugin.settings.msg("now-tracking", "player" to plugin.matches.coloredName(target))
                )
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onDrag(e: InventoryDragEvent) {
        if (e.inventory.holder is CompassMenu) e.isCancelled = true
    }
}
