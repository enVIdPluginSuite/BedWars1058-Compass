package dev.etran.bedwars1058compass

import com.andrei1058.bedwars.api.BedWars
import org.bukkit.event.HandlerList
import org.bukkit.plugin.java.JavaPlugin

/*
 * ================================ MAP OF THIS FILE (read this first) ================================
 *   onEnable / onDisable ... find the BedWars1058 API, register /bwcompass, build everything / undo our world changes.
 *   build() ................ creates every component from config.yml, registers the listeners and starts the repeating task.
 *   reloadSettings() ....... "config" reload: re-read config.yml and apply it to the running matches.
 *   reloadAll() ............ "full" reload: unregister everything, cancel every task, then build() again.
 *
 * The components and what they own:
 *   Settings .................. typed config.yml            MatchService ......... "is this player in a running match?"
 *   Compasses ................. the compass item            TrackerManager ....... active trackers + the repeating task
 *   TrackerMenus .............. tracker shop + target list  QuickCommunication ... the pre-bed quick team chat menu
 *   LocatorBar ................ player locator bar off      ConfigEditor ......... in-game config editing
 *   CompassOverrideListener ... owns every compass event     GameListener ......... BedWars1058 lifecycle events
 *   MenuListener .............. clicks inside our menus      PluginCommand ........ /bwcompass
 *
 * Components never keep a copy of an option: they read plugin.settings every time, so a reload is visible at once.
 * =====================================================================================================
 */

class BedWars1058Compass : JavaPlugin() {

    lateinit var bedWars: BedWars; private set
    lateinit var settings: Settings; private set
    lateinit var matches: MatchService; private set
    lateinit var compasses: Compasses; private set
    lateinit var trackers: TrackerManager; private set
    lateinit var menus: TrackerMenus; private set
    lateinit var communication: QuickCommunication; private set
    lateinit var locatorBar: LocatorBar; private set
    lateinit var configEditor: ConfigEditor; private set

    /** Finds the BedWars1058 API, hooks up /bwcompass and builds the plugin. Disables itself when BedWars1058 is missing. */
    override fun onEnable() {
        val registration = server.servicesManager.getRegistration(BedWars::class.java)
        if (registration == null) {
            logger.severe("BedWars1058 API not found - disabling.")
            server.pluginManager.disablePlugin(this)
            return
        }
        bedWars = registration.provider

        val command = PluginCommand(this)
        getCommand("bwcompass")?.let {
            it.setExecutor(command)
            it.tabCompleter = command
        }

        build()
        logger.info("BedWars1058-Compass ${description.version} enabled.")
    }

    /** Forgets every tracker and gives the worlds their original locator bar setting back. */
    override fun onDisable() {
        if (::menus.isInitialized) menus.closeAll()
        if (::trackers.isInitialized) trackers.clearAll()
        if (::locatorBar.isInitialized) locatorBar.restoreAll()
    }

    /**
     * Creates every component from the current config.yml, registers the listeners and starts the repeating task.
     * The TrackerManager is the one component that survives a full reload, so trackers bought before it keep working.
     */
    private fun build() {
        settings = Settings(this).also { it.load() }
        matches = MatchService(this)
        compasses = Compasses(this)
        menus = TrackerMenus(this)
        communication = QuickCommunication(this)
        locatorBar = LocatorBar(this)
        configEditor = ConfigEditor(this)
        if (!::trackers.isInitialized) trackers = TrackerManager(this)

        server.pluginManager.apply {
            registerEvents(CompassOverrideListener(this@BedWars1058Compass), this@BedWars1058Compass)
            registerEvents(GameListener(this@BedWars1058Compass), this@BedWars1058Compass)
            registerEvents(MenuListener(this@BedWars1058Compass), this@BedWars1058Compass)
        }
        trackers.start()
        applyToRunningMatches()
    }

    /**
     * Config reload: re-reads config.yml and applies it. Open menus are closed (their titles and buttons may be stale),
     * the repeating task restarts with the new tick rate, and the compass items and locator bar are updated right now.
     */
    fun reloadSettings() {
        menus.closeAll()
        settings.load()
        trackers.start()
        applyToRunningMatches()
    }

    /**
     * Full reload: closes our menus, removes every listener and scheduled task of this plugin, undoes our world changes,
     * and builds everything again from disk. Equivalent to restarting the plugin without losing the active trackers.
     */
    fun reloadAll() {
        menus.closeAll()
        HandlerList.unregisterAll(this)
        server.scheduler.cancelTasks(this)
        locatorBar.restoreAll()
        build()
    }

    /** Brings arenas that are already running in line with the (new) settings without waiting for the next timer tick. */
    private fun applyToRunningMatches() {
        locatorBar.syncAll()
        trackers.refresh()
    }
}
