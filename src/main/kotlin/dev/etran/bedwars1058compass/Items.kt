package dev.etran.bedwars1058compass

import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.SkullMeta

/*
 * ================================ MAP OF THIS FILE (read this first) ================================
 *   Item helpers that work from 1.8 to the latest version: the grey glass pane and the player head have different materials
 *   (and a data value on 1.8), so the modern name is tried first and the legacy one is the fallback.
 * =====================================================================================================
 */

/** Small cross-version (1.8 -> latest) item helpers. */
object Items {

    private fun firstMaterial(vararg names: String): Material? =
        names.firstNotNullOfOrNull { Material.matchMaterial(it) }

    /** An item with a display name and lore. */
    fun named(material: Material, name: String, lore: List<String> = emptyList()): ItemStack {
        val item = ItemStack(material)
        val meta = item.itemMeta
        meta.displayName = name
        meta.lore = lore
        item.itemMeta = meta
        return item
    }

    /** The grey glass pane that fills empty menu slots (legacy and modern material). */
    fun filler(): ItemStack {
        val modern = Material.matchMaterial("GRAY_STAINED_GLASS_PANE")
        val item = if (modern != null) ItemStack(modern) else {
            @Suppress("DEPRECATION")
            ItemStack(firstMaterial("STAINED_GLASS_PANE") ?: Material.GLASS, 1, 7.toShort())
        }
        val meta = item.itemMeta
        meta.displayName = " "
        item.itemMeta = meta
        return item
    }

    /** A player head with a display name and lore. */
    fun head(owner: String, name: String, lore: List<String>): ItemStack {
        val modern = Material.matchMaterial("PLAYER_HEAD")
        val item = if (modern != null) ItemStack(modern) else {
            @Suppress("DEPRECATION")
            ItemStack(firstMaterial("SKULL_ITEM") ?: Material.PAPER, 1, 3.toShort())
        }
        val meta = item.itemMeta
        if (meta is SkullMeta) {
            @Suppress("DEPRECATION")
            meta.owner = owner
        }
        meta.displayName = name
        meta.lore = lore
        item.itemMeta = meta
        return item
    }

    /** How many items of [material] are in [inventory] in total. */
    fun count(inventory: org.bukkit.inventory.Inventory, material: Material): Int =
        inventory.contents.filterNotNull().filter { it.type == material }.sumOf { it.amount }
}
