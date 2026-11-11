package dev.etran.bedwars1058compass

import org.bukkit.ChatColor
import org.bukkit.Material
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin
import java.io.InputStreamReader

/*
 * ================================ MAP OF THIS FILE (read this first) ================================
 *   1. QuickMessage ... one button of the quick communication menu, already parsed from the config.
 *   2. Settings ....... a typed view over config.yml.
 *        load()            reads the file (after merging in any option an older version did not have).
 *        mergeDefaults()   copies new default options into the user's config.yml, so upgrading never needs a manual edit.
 *        msg()             one message from the "messages:" section with {placeholders} replaced.
 *
 * Every other class reads its options through plugin.settings, never through plugin.config directly, so one load() is
 * all it takes for a change to be live (that is what /bwcompass reload and /bwcompass config set do).
 * =====================================================================================================
 */

/** One button of the quick communication menu. [message] is stored raw: placeholders and colours are applied when it is sent. */
data class QuickMessage(
    /** The key under quick-communication.messages (used for the click lookup and for in-game editing). */
    val key: String,
    /** Inventory slot (0-26) of the button. */
    val slot: Int,
    val material: Material,
    /** Button name, colours already applied. */
    val name: String,
    val message: String
)

/** Typed view over config.yml. Call [load] again to hot-reload. */
class Settings(private val plugin: JavaPlugin) {

    var compassSlot = 8; private set
    var lockSlot = true; private set
    var ensureCompass = true; private set
    var replaceForeignCompasses = false; private set
    var communicationName = ""; private set
    var communicationLore: List<String> = emptyList(); private set
    var trackerName = ""; private set
    var trackerLore: List<String> = emptyList(); private set

    var communicationEnabled = true; private set
    var communicationCooldownMs = 3000L; private set
    var communicationTitle = ""; private set
    var communicationFormat = ""; private set
    var quickMessages: List<QuickMessage> = emptyList(); private set

    var costMaterial: Material = Material.EMERALD; private set
    var costAmount = 2; private set
    var stopOnDeath = true; private set
    var updateTicks = 5L; private set
    var actionBarOnlyWhenHolding = true; private set
    var untrackedPointsToSpawn = true; private set

    var disableLocatorBar = true; private set

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

    /**
     * Reads config.yml from disk and fills every field below.
     * STEPS: 1) make sure the file exists  2) re-read it from disk  3) add options this version introduced  4) copy every value into a typed field.
     * The v1 options compass.name and compass.lore no longer exist: the two modes have their own names (compass.communication / compass.tracker).
     */
    fun load() {
        plugin.saveDefaultConfig()
        plugin.reloadConfig()
        mergeDefaults()
        val c = plugin.config

        compassSlot = c.getInt("compass.slot", 8).coerceIn(0, 8)
        lockSlot = c.getBoolean("compass.lock-slot", true)
        ensureCompass = c.getBoolean("compass.ensure-in-inventory", true)
        replaceForeignCompasses = c.getBoolean("compass.replace-foreign-compasses", false)
        communicationName = color(c.getString("compass.communication.name", "&aQuick Communication"))
        communicationLore = c.getStringList("compass.communication.lore").map(::color)
        trackerName = color(c.getString("compass.tracker.name", "&aTracker Shop"))
        trackerLore = c.getStringList("compass.tracker.lore").map(::color)

        communicationEnabled = c.getBoolean("quick-communication.enabled", true)
        communicationCooldownMs = (c.getDouble("quick-communication.cooldown-seconds", 3.0).coerceAtLeast(0.0) * 1000).toLong()
        communicationTitle = color(c.getString("quick-communication.title", "&8Quick Communication"))
        communicationFormat = c.getString("quick-communication.format", "&8[&bTEAM&8] {player}&8: &f{message}") ?: ""
        quickMessages = loadQuickMessages()

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

        disableLocatorBar = c.getBoolean("locator-bar.disable", true)

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

    /**
     * Copies every option the bundled config.yml has and the server's config.yml lacks, then saves.
     * An entry under quick-communication.messages is only copied when the whole section is missing,
     * so a button an admin deleted in game does not come back on the next reload.
     */
    private fun mergeDefaults() {
        val stream = plugin.getResource("config.yml") ?: return
        val defaults = InputStreamReader(stream, Charsets.UTF_8).use { YamlConfiguration.loadConfiguration(it) }
        val c = plugin.config

        var added = 0
        val hasMessages = c.isConfigurationSection("quick-communication.messages")
        for (key in defaults.getKeys(true)) {
            if (defaults.isConfigurationSection(key)) continue
            if (hasMessages && key.startsWith("quick-communication.messages.")) continue
            if (!c.contains(key)) {
                c.set(key, defaults.get(key))
                added++
            }
        }
        if (added > 0) {
            plugin.saveConfig()
            plugin.logger.info("Added $added new default option(s) to config.yml")
        }
    }

    /** Parses quick-communication.messages into buttons, skipping entries without a message. An unknown material falls back to paper. */
    private fun loadQuickMessages(): List<QuickMessage> {
        val section = plugin.config.getConfigurationSection("quick-communication.messages") ?: return emptyList()
        return section.getKeys(false).mapNotNull { key ->
            val entry = section.getConfigurationSection(key) ?: return@mapNotNull null
            val message = entry.getString("message") ?: return@mapNotNull null
            val material = Material.matchMaterial(entry.getString("material", "PAPER") ?: "PAPER") ?: Material.PAPER
            QuickMessage(key, entry.getInt("slot", 10).coerceIn(0, 26), material, color(entry.getString("name", key)), message)
        }.sortedBy { it.slot }
    }

    /** Name of the compass item for a mode. */
    fun compassName(mode: CompassMode): String = if (mode == CompassMode.TRACKER) trackerName else communicationName

    /** Lore of the compass item for a mode. */
    fun compassLore(mode: CompassMode): List<String> = if (mode == CompassMode.TRACKER) trackerLore else communicationLore

    /** Human readable price, e.g. "2 Emerald". */
    fun costText(): String =
        "$costAmount " + costMaterial.name.lowercase().split('_').joinToString(" ") { it.replaceFirstChar(Char::uppercase) }

    /** A message from the "messages:" section with {prefix} and every given {placeholder} replaced and colours applied. */
    fun msg(key: String, vararg replacements: Pair<String, String>): String {
        var text = plugin.config.getString("messages.$key") ?: return key
        text = text.replace("{prefix}", plugin.config.getString("messages.prefix") ?: "")
        for ((k, v) in replacements) text = text.replace("{$k}", v)
        return color(text)
    }

    companion object {
        /** Turns '&' colour codes into section-sign codes. */
        fun color(text: String?): String = ChatColor.translateAlternateColorCodes('&', text ?: "")
    }
}
