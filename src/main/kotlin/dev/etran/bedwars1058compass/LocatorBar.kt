package dev.etran.bedwars1058compass

import org.bukkit.Bukkit
import org.bukkit.World

/*
 * ================================ MAP OF THIS FILE (read this first) ================================
 *   LocatorBar switches the player locator bar (Minecraft 1.21.6+, it replaces the XP bar and shows where other players
 *   are) off in every arena world, because it would make the tracker pointless.
 *
 *   apply()       turns it off in one world (remembering what the world had before).
 *   restore()     puts a world back the way it was.   restoreAll() does it for every world we touched.
 *   syncAll()     applies or restores every arena world to match the current config (used after a reload).
 *
 * How it works: the locator bar is a game rule. Its name changed between versions (locatorBar, later locator_bar), so both
 * spellings are tried, and a rule the server does not have is simply skipped: on a server older than 1.21.6 this class does nothing.
 * The rule is read and written through the string based World methods, which exist on every version this plugin supports.
 * =====================================================================================================
 */

/** Disables the player locator bar in arena worlds (toggle: locator-bar.disable). */
class LocatorBar(private val plugin: BedWars1058Compass) {

    /** world name -> (rule name -> the value it had before we changed it). Only worlds we actually changed are in here. */
    private val originals = HashMap<String, HashMap<String, String>>()

    /** Turns the locator bar off in [world] when the toggle is on. Safe to call every few seconds: it only writes when the value is wrong. */
    fun apply(world: World?) {
        if (world == null || !plugin.settings.disableLocatorBar) return
        for (rule in RULES) {
            if (!hasRule(world, rule)) continue
            val current = readRule(world, rule)
            if (current == "false") continue
            originals.getOrPut(world.name) { HashMap() }.putIfAbsent(rule, current ?: "true")
            writeRule(world, rule, "false")
        }
    }

    /** Puts [world] back to the value it had before [apply] changed it. */
    fun restore(world: World?) {
        if (world == null) return
        originals.remove(world.name)?.forEach { (rule, value) -> writeRule(world, rule, value) }
    }

    /** Restores every world we changed. Called when the plugin is disabled or reloaded. */
    fun restoreAll() {
        for (name in ArrayList(originals.keys)) restore(Bukkit.getWorld(name))
        originals.clear()
    }

    /** Applies (toggle on) or restores (toggle off) every arena world, so a config change takes effect without a restart. */
    fun syncAll() {
        for (arena in ArrayList(plugin.bedWars.arenaUtil.arenas)) {
            if (plugin.settings.disableLocatorBar) apply(arena.world) else restore(arena.world)
        }
    }

    /** True when the server knows the game rule. Checked first because setting an unknown rule creates a junk one on 1.8. */
    @Suppress("DEPRECATION")
    private fun hasRule(world: World, rule: String): Boolean = try {
        world.isGameRule(rule)
    } catch (t: Throwable) {
        false
    }

    /** The current value of a rule as text, or null when it cannot be read. */
    @Suppress("DEPRECATION")
    private fun readRule(world: World, rule: String): String? = try {
        world.getGameRuleValue(rule)
    } catch (t: Throwable) {
        null
    }

    /** Writes a rule; a failure is logged once per call site and otherwise ignored, because a missing locator bar is not an error. */
    @Suppress("DEPRECATION")
    private fun writeRule(world: World, rule: String, value: String) {
        try {
            world.setGameRuleValue(rule, value)
        } catch (t: Throwable) {
            plugin.logger.warning("Could not set game rule $rule=$value in ${world.name}: ${t.message}")
        }
    }

    private companion object {
        /** Every name the locator bar rule has had, newest last. */
        val RULES = listOf("locatorBar", "locator_bar")
    }
}
