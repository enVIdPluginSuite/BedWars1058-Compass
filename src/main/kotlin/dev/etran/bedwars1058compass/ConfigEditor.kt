package dev.etran.bedwars1058compass

import org.bukkit.Material
import org.bukkit.configuration.file.YamlConfiguration
import java.io.InputStreamReader

/*
 * ================================ MAP OF THIS FILE (read this first) ================================
 *   ConfigEditor changes config.yml from inside the game. It never reloads anything itself: it only edits and saves, and the
 *   caller (PluginCommand) reloads the plugin afterwards.
 *
 *   Reading:   leafKeys / sectionsUnder / get / format
 *   Writing:   set (type checked + validated), reset (back to the bundled default), remove (quick message buttons only)
 *   Internals: coerce (text -> the type the option already has), validate (ranges, materials), isCreatable (which new keys are allowed)
 *
 * Why it type checks: a value typed in chat is text. "true" must become a boolean, "5" a number, "a || b" a list, and the new value
 * must have the same type as the old one, otherwise a typo would silently turn a number option into a string and break the plugin.
 *
 * Note: Bukkit rewrites the whole file when saving. Servers on 1.18.2+ keep the comments; older servers drop them.
 * =====================================================================================================
 */

/** Edits config.yml in game. Every function returns an error message (or null on success) instead of throwing. */
class ConfigEditor(private val plugin: BedWars1058Compass) {

    /** The config.yml bundled inside the jar: the source of "reset" and of the list of valid options. */
    private val defaults: YamlConfiguration by lazy {
        val stream = plugin.getResource("config.yml")
        if (stream == null) YamlConfiguration() else InputStreamReader(stream, Charsets.UTF_8).use { YamlConfiguration.loadConfiguration(it) }
    }

    /** Every option (not section) in the current config, sorted. */
    fun leafKeys(): List<String> =
        plugin.config.getKeys(true).filter { !plugin.config.isConfigurationSection(it) }.sorted()

    /** The names one level below [prefix] ("" = the top level), each marked by whether it is a section. Used by "config list". */
    fun sectionsUnder(prefix: String): List<Pair<String, Boolean>> {
        val section = if (prefix.isBlank()) plugin.config else plugin.config.getConfigurationSection(prefix) ?: return emptyList()
        return section.getKeys(false).map { it to section.isConfigurationSection(it) }
    }

    /** True when [path] is a section (a group of options) rather than a single option. */
    fun isSection(path: String): Boolean = plugin.config.isConfigurationSection(path)

    /** The raw value stored at [path], or null. */
    fun get(path: String): Any? = plugin.config.get(path)

    /** A value as it is typed back into "config set": lists use " || " between entries. */
    fun format(value: Any?): String = when (value) {
        null -> "(not set)"
        is List<*> -> value.joinToString(" || ")
        else -> value.toString()
    }

    /**
     * Sets [path] to [raw] (what the admin typed) and saves.
     * STEPS: 1) is the path allowed  2) turn the text into the type the option already has  3) range / material checks  4) store and save.
     */
    fun set(path: String, raw: String): String? {
        val c = plugin.config
        if (path.isBlank()) return "Give the option to change, for example tracker.cost.amount."
        if (c.isConfigurationSection(path)) return "'$path' is a group of options; change one inside it (see: config list $path)."
        if (!c.contains(path) && !defaults.contains(path) && !isCreatable(path)) return "Unknown option '$path'. Use config list to see them."

        val value = try {
            coerce(path, raw)
        } catch (e: IllegalArgumentException) {
            return e.message
        }
        validate(path, value)?.let { return it }

        c.set(path, value)
        plugin.saveConfig()
        return null
    }

    /** Puts [path] back to the value in the bundled config.yml. */
    fun reset(path: String): String? {
        val default = defaults.get(path)
        if (default == null || defaults.isConfigurationSection(path)) return "'$path' has no single default to go back to."
        plugin.config.set(path, default)
        plugin.saveConfig()
        return null
    }

    /** Deletes a quick message button (quick-communication.messages.<id>) or one of its fields. Nothing else can be deleted. */
    fun remove(path: String): String? {
        if (!path.startsWith(MESSAGES_PREFIX) || path == MESSAGES_PREFIX.trimEnd('.')) {
            return "Only quick message buttons can be removed (quick-communication.messages.<id>). Use reset for everything else."
        }
        if (!plugin.config.contains(path)) return "'$path' does not exist."
        plugin.config.set(path, null)
        plugin.saveConfig()
        return null
    }

    /** New keys may only be created for quick message buttons: quick-communication.messages.<id>.<field>. Anything else must already exist. */
    private fun isCreatable(path: String): Boolean = path.startsWith(MESSAGES_PREFIX) && path.removePrefix(MESSAGES_PREFIX).count { it == '.' } >= 1

    /**
     * Turns typed text into the value the option should hold, using the option's current (or default) value to learn its type.
     * Throws IllegalArgumentException with a message fit for the admin when the text does not fit.
     */
    private fun coerce(path: String, raw: String): Any {
        val text = raw.trim()
        return when (val existing = plugin.config.get(path) ?: defaults.get(path)) {
            is Boolean -> when (text.lowercase()) {
                "true", "on", "yes" -> true
                "false", "off", "no" -> false
                else -> throw IllegalArgumentException("'$path' is true or false.")
            }
            is Number -> number(path, text)
            is List<*> -> text.split("||").map { it.trim() }.filter { it.isNotEmpty() }
            is String -> text
            null -> if (text.toIntOrNull() != null) text.toInt() else text // a brand new quick message field
            else -> text
        }
    }

    /** Parses a number: whole numbers stay whole (Int), anything with a decimal point becomes a Double. */
    private fun number(path: String, text: String): Number {
        text.toIntOrNull()?.let { return it }
        return text.toDoubleOrNull() ?: throw IllegalArgumentException("'$path' is a number.")
    }

    /** Range and material checks for the options where a wrong value would break the plugin. Returns an error message or null. */
    private fun validate(path: String, value: Any): String? {
        val leaf = path.substringAfterLast('.')
        val number = (value as? Number)?.toDouble()
        return when {
            leaf == "material" && Material.matchMaterial(value.toString()) == null -> "'$value' is not a material."
            path == "compass.slot" && (number == null || number < 0 || number > 8) -> "compass.slot is a hotbar slot from 0 to 8 (8 is the 9th slot)."
            leaf == "slot" && path.startsWith(MESSAGES_PREFIX) && (number == null || number < 0 || number > 26) -> "A quick message slot is from 0 to 26."
            path == "tracker.cost.amount" && (number == null || number < 0) -> "tracker.cost.amount can't be negative."
            path == "tracker.update-ticks" && (number == null || number < 1) -> "tracker.update-ticks must be at least 1."
            path == "quick-communication.cooldown-seconds" && (number == null || number < 0) -> "The cooldown can't be negative."
            path == "override.suppress-window-ms" && (number == null || number < 0) -> "The window can't be negative."
            else -> null
        }
    }

    private companion object {
        /** Where the quick message buttons live. */
        const val MESSAGES_PREFIX = "quick-communication.messages."
    }
}
