package dev.etran.bedwars1058compass

import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

/*
 * ================================ MAP OF THIS FILE (read this first) ================================
 *   1. CompassMode ... the two jobs of the compass (quick communication, then tracker shop).
 *   2. Compasses ..... the item itself:
 *        modeFor()   which job the compass has for a player right now (decided by their team's bed).
 *        create()    builds the item for a mode.
 *        isOurs()    is this stack one of our compasses (either mode).
 *        ensure()    gives / repairs / moves the compass so it is exactly right. Called on spawn and once a second.
 *
 * The compass never changes by itself: a repeating task in TrackerManager calls ensure(), and GameListener calls it right
 * after a bed is destroyed, so the item is renamed from "Quick Communication" to "Tracker Shop" within a few ticks.
 * =====================================================================================================
 */

/**
 * What the compass currently is for a player.
 *  - COMMUNICATION: the team's bed is alive, so the compass only sends quick messages to the team.
 *  - TRACKER:       the team's bed is destroyed, so the compass opens the tracker shop.
 */
enum class CompassMode { COMMUNICATION, TRACKER }

/** Everything about the physical compass item. */
class Compasses(private val plugin: BedWars1058Compass) {

    /** The job of the compass for [player]: tracker once their own team's bed is gone, quick communication until then. */
    fun modeFor(player: Player): CompassMode =
        if (plugin.matches.isBedDestroyed(player)) CompassMode.TRACKER else CompassMode.COMMUNICATION

    /** Builds our compass for [mode] with the name and lore from the config. */
    fun create(mode: CompassMode): ItemStack {
        val item = ItemStack(Material.COMPASS)
        val meta = item.itemMeta
        meta.displayName = plugin.settings.compassName(mode)
        meta.lore = plugin.settings.compassLore(mode)
        item.itemMeta = meta
        return item
    }

    /** True for a compass that carries the name of either of our modes. */
    fun isOurs(item: ItemStack?): Boolean {
        if (!isCompass(item)) return false
        val meta = item!!.itemMeta ?: return false
        if (!meta.hasDisplayName()) return false
        return meta.displayName == plugin.settings.communicationName || meta.displayName == plugin.settings.trackerName
    }

    /** True when [item] already looks exactly like our compass for [mode] (so it does not need to be rebuilt). */
    private fun matchesMode(item: ItemStack, mode: CompassMode): Boolean {
        val meta = item.itemMeta ?: return false
        return meta.hasDisplayName() && meta.displayName == plugin.settings.compassName(mode) &&
            (meta.lore ?: emptyList<String>()) == plugin.settings.compassLore(mode)
    }

    /**
     * Makes sure [player] holds exactly one compass, in the right mode, in the right slot.
     *
     * STEPS: 1) walk the inventory; the first compass of ours (or a foreign one when replacing is on) is kept and rebuilt if its
     * mode is stale, any further one is removed  2) none found: put a new one in the configured slot, else the first free hotbar
     * slot, else the first free slot  3) found but not in the configured slot: move it there when that slot is empty.
     */
    fun ensure(player: Player) {
        val inv = player.inventory
        val mode = modeFor(player)
        val preferred = plugin.settings.compassSlot
        var keptSlot = -1

        for (slot in 0 until inv.size) {
            val stack = inv.getItem(slot)
            if (!isCompass(stack)) continue
            if (!isOurs(stack) && !plugin.settings.replaceForeignCompasses) continue

            if (keptSlot != -1) {
                inv.setItem(slot, null) // a duplicate
            } else {
                keptSlot = slot
                if (!isOurs(stack) || !matchesMode(stack!!, mode) || stack.amount != 1) inv.setItem(slot, create(mode))
            }
        }

        if (keptSlot == -1) {
            val target = when {
                inv.getItem(preferred) == null -> preferred
                else -> (0 until 9).firstOrNull { inv.getItem(it) == null }
                    ?: (9 until inv.size).firstOrNull { inv.getItem(it) == null }
            } ?: return
            inv.setItem(target, create(mode))
        } else if (plugin.settings.lockSlot && keptSlot != preferred && inv.getItem(preferred) == null) {
            inv.setItem(preferred, inv.getItem(keptSlot))
            inv.setItem(keptSlot, null)
        }
    }

    companion object {
        /** True for any compass, ours or not. */
        fun isCompass(item: ItemStack?): Boolean = item != null && item.type == Material.COMPASS
    }
}
