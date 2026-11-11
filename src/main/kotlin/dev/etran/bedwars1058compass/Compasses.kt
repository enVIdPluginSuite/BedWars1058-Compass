package dev.etran.bedwars1058compass

import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

/**
 * What the compass currently is for a player.
 *  - COMMUNICATION: the team's bed is alive, so the compass only sends quick messages to the team.
 *  - TRACKER:       the team's bed is destroyed, so the compass opens the tracker shop.
 */
enum class CompassMode { COMMUNICATION, TRACKER }

/** Everything about the physical compass item. */
class Compasses(private val plugin: BedWars1058Compass) {

    fun create(): ItemStack {
        val item = ItemStack(Material.COMPASS)
        val meta = item.itemMeta
        meta.displayName = plugin.settings.compassName
        meta.lore = plugin.settings.compassLore
        item.itemMeta = meta
        return item
    }

    /** True for ours. */
    fun isOurs(item: ItemStack?): Boolean {
        if (!isCompass(item)) return false
        val meta = item!!.itemMeta ?: return false
        return meta.hasDisplayName() && meta.displayName == plugin.settings.compassName
    }

    /**
     * Makes sure [player] holds exactly our compass. Optionally swaps compasses that came
     * from other plugins for ours, then adds one if none is present.
     */
    fun ensure(player: Player) {
        val inv = player.inventory
        var hasOurs = false

        for (slot in 0 until inv.size) {
            val stack = inv.getItem(slot)
            if (!isCompass(stack)) continue
            if (isOurs(stack)) {
                if (hasOurs) inv.setItem(slot, null) else hasOurs = true
            } else if (plugin.settings.replaceForeignCompasses) {
                if (hasOurs) {
                    inv.setItem(slot, null)
                } else {
                    inv.setItem(slot, create())
                    hasOurs = true
                }
            }
        }
        if (hasOurs) return

        // Anything compass-shaped counts when we are not replacing foreign ones; still give ours.
        val preferred = plugin.settings.compassSlot
        val target = when {
            inv.getItem(preferred) == null -> preferred
            else -> (0 until 9).firstOrNull { inv.getItem(it) == null }
                ?: (9 until inv.size).firstOrNull { inv.getItem(it) == null }
        } ?: return
        inv.setItem(target, create())
    }

    companion object {
        fun isCompass(item: ItemStack?): Boolean = item != null && item.type == Material.COMPASS
    }
}
