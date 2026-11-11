package dev.etran.bedwars1058compass

import org.bukkit.ChatColor
import org.bukkit.Material
import org.bukkit.plugin.java.JavaPlugin

/** Typed view over config.yml. Call [load] again to hot-reload. */
class Settings(private val plugin: JavaPlugin) {

    var compassName = ""; private set
    var compassLore: List<String> = emptyList(); private set
    var compassSlot = 8; private set
    var ensureCompass = true; private set
    var replaceForeignCompasses = false; private set

    var costMaterial: Material = Material.EMERALD; private set
    var costAmount = 2; private set
    var stopOnDeath = true; private set
    var updateTicks = 5L; private set
    var actionBarOnlyWhenHolding = true; private set
    var untrackedPointsToSpawn = true; private set

    var includeSpectators = false; private set
    var cancelDrop = true; private set
    var blockContainerMoves = true; private set
    var suppressForeignReactions = true; private set
    var suppressWindowMs = 250L; private set

    var shopTitle = ""; private set
    var selectTitle = ""; private set
    var shopItemName = ""; private set
    var shopItemLore: List<String> = emptyList(); private set
    var stopItemName = ""; private set
    var statusItemName = ""; private set

    fun load() {
        plugin.saveDefaultConfig()
        plugin.reloadConfig()
        val c = plugin.config

        compassName = color(c.getString("compass.name", "&aCompass"))
        compassLore = c.getStringList("compass.lore").map(::color)
        compassSlot = c.getInt("compass.slot", 8).coerceIn(0, 8)
        ensureCompass = c.getBoolean("compass.ensure-in-inventory", true)
        replaceForeignCompasses = c.getBoolean("compass.replace-foreign-compasses", false)

        val matName = c.getString("tracker.cost.material", "EMERALD") ?: "EMERALD"
        costMaterial = Material.matchMaterial(matName) ?: run {
            plugin.logger.warning("Unknown tracker.cost.material '$matName', using EMERALD")
            Material.EMERALD
        }
        costAmount = c.getInt("tracker.cost.amount", 2).coerceAtLeast(0)
        stopOnDeath = c.getBoolean("tracker.stop-on-death", true)
        updateTicks = c.getInt("tracker.update-ticks", 5).coerceAtLeast(1).toLong()
        actionBarOnlyWhenHolding = c.getBoolean("tracker.action-bar-only-when-holding", true)
        untrackedPointsToSpawn = c.getBoolean("tracker.untracked-points-to-spawn", true)

        includeSpectators = c.getBoolean("override.include-spectators", false)
        cancelDrop = c.getBoolean("override.cancel-drop", true)
        blockContainerMoves = c.getBoolean("override.block-container-moves", true)
        suppressForeignReactions = c.getBoolean("override.suppress-foreign-reactions", true)
        suppressWindowMs = c.getLong("override.suppress-window-ms", 250L)

        shopTitle = color(c.getString("gui.shop-title", "&8Tracker Shop"))
        selectTitle = color(c.getString("gui.select-title", "&8Select a player to track"))
        shopItemName = color(c.getString("gui.shop-item-name", "&aPlayer Tracker"))
        shopItemLore = c.getStringList("gui.shop-item-lore")
        stopItemName = color(c.getString("gui.stop-item-name", "&cStop Tracking"))
        statusItemName = color(c.getString("gui.status-item-name", "&eCurrently Tracking"))
    }

    /** Human readable price, e.g. "2 Emerald". */
    fun costText(): String =
        "$costAmount " + costMaterial.name.lowercase().split('_').joinToString(" ") { it.replaceFirstChar(Char::uppercase) }

    fun msg(key: String, vararg replacements: Pair<String, String>): String {
        var text = plugin.config.getString("messages.$key") ?: return key
        text = text.replace("{prefix}", plugin.config.getString("messages.prefix") ?: "")
        for ((k, v) in replacements) text = text.replace("{$k}", v)
        return color(text)
    }

    companion object {
        fun color(text: String?): String = ChatColor.translateAlternateColorCodes('&', text ?: "")
    }
}
