package dev.etran.bedwars1058compass

import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemStack
import java.util.UUID

/** Marker so listeners can recognise our inventories. */
sealed class CompassMenu : InventoryHolder {
    lateinit var inv: Inventory
    override fun getInventory(): Inventory = inv
}

class ShopMenu : CompassMenu()

class TargetMenu : CompassMenu() {
    val slots = HashMap<Int, UUID>()
}

class TrackerMenus(private val plugin: BedWars1058Compass) {

    companion object {
        const val SHOP_TRACKER_SLOT = 11
        const val SHOP_STATUS_SLOT = 13
        const val SHOP_STOP_SLOT = 15
    }

    private val s get() = plugin.settings

    fun openShop(player: Player) {
        val holder = ShopMenu()
        val inv = Bukkit.createInventory(holder, 27, s.shopTitle)
        holder.inv = inv
        for (i in 0 until inv.size) inv.setItem(i, Items.filler())

        val affordable = canAfford(player)
        val action = if (affordable) "&eClick to select a player!" else "&cYou cannot afford this!"
        val lore = s.shopItemLore.map {
            Settings.color(it.replace("{cost}", s.costText()).replace("{action}", action))
        }
        inv.setItem(SHOP_TRACKER_SLOT, Items.named(Material.COMPASS, s.shopItemName, lore))

        val current = plugin.trackers.targetOf(player)
        if (current != null) {
            inv.setItem(
                SHOP_STATUS_SLOT,
                Items.head(current.name, s.statusItemName, listOf(plugin.matches.coloredName(current)))
            )
            inv.setItem(SHOP_STOP_SLOT, Items.named(Material.BARRIER, s.stopItemName))
        }
        player.openInventory(inv)
    }

    fun openTargets(player: Player) {
        val targets = plugin.matches.trackableTargets(player)
        if (targets.isEmpty()) {
            player.closeInventory()
            player.sendMessage(s.msg("no-targets"))
            return
        }
        val holder = TargetMenu()
        val rows = ((targets.size + 8) / 9).coerceIn(1, 6)
        val inv = Bukkit.createInventory(holder, rows * 9, s.selectTitle)
        holder.inv = inv

        val arena = plugin.matches.arenaOf(player)
        targets.take(rows * 9).forEachIndexed { index, target ->
            val name = plugin.matches.coloredName(target, arena)
            val lore = listOf(
                Settings.color("&7Cost: &f${s.costText()}"),
                "",
                Settings.color("&eClick to track!")
            )
            inv.setItem(index, Items.head(target.name, name, lore))
            holder.slots[index] = target.uniqueId
        }
        player.openInventory(inv)
    }

    fun canAfford(player: Player) = Items.count(player.inventory, s.costMaterial) >= s.costAmount

    /** Removes the price from the inventory. Returns false (and takes nothing) if unaffordable. */
    fun charge(player: Player): Boolean {
        if (!canAfford(player)) return false
        if (s.costAmount > 0) {
            player.inventory.removeItem(ItemStack(s.costMaterial, s.costAmount))
            @Suppress("DEPRECATION")
            player.updateInventory()
        }
        return true
    }
}
