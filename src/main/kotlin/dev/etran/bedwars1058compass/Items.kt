package dev.etran.bedwars1058compass

import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.SkullMeta

/** Small cross-version (1.8 -> latest) item helpers. */
object Items {

    private fun firstMaterial(vararg names: String): Material? =
        names.firstNotNullOfOrNull { Material.matchMaterial(it) }

    fun named(material: Material, name: String, lore: List<String> = emptyList()): ItemStack {
        val item = ItemStack(material)
        val meta = item.itemMeta
        meta.displayName = name
        meta.lore = lore
        item.itemMeta = meta
        return item
    }

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

    fun count(inventory: org.bukkit.inventory.Inventory, material: Material): Int =
        inventory.contents.filterNotNull().filter { it.type == material }.sumOf { it.amount }
}
