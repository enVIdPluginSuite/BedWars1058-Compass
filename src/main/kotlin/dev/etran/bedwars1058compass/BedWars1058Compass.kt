package dev.etran.bedwars1058compass

import com.andrei1058.bedwars.api.BedWars
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.plugin.java.JavaPlugin

class BedWars1058Compass : JavaPlugin() {

    lateinit var bedWars: BedWars; private set
    lateinit var settings: Settings; private set
    lateinit var matches: MatchService; private set
    lateinit var compasses: Compasses; private set
    lateinit var trackers: TrackerManager; private set
    lateinit var menus: TrackerMenus; private set

    override fun onEnable() {
        val registration = server.servicesManager.getRegistration(BedWars::class.java)
        if (registration == null) {
            logger.severe("BedWars1058 API not found - disabling.")
            server.pluginManager.disablePlugin(this)
            return
        }
        bedWars = registration.provider

        settings = Settings(this).also { it.load() }
        matches = MatchService(this)
        compasses = Compasses(this)
        menus = TrackerMenus(this)
        trackers = TrackerManager(this)

        server.pluginManager.apply {
            registerEvents(CompassOverrideListener(this@BedWars1058Compass), this@BedWars1058Compass)
            registerEvents(GameListener(this@BedWars1058Compass), this@BedWars1058Compass)
            registerEvents(MenuListener(this@BedWars1058Compass), this@BedWars1058Compass)
        }
        trackers.start()
        logger.info("BedWars1058-Compass enabled.")
    }

    override fun onDisable() {
        if (::trackers.isInitialized) trackers.clearAll()
    }

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (!sender.hasPermission("bwcompass.admin")) {
            sender.sendMessage(settings.msg("no-permission"))
            return true
        }
        if (args.firstOrNull().equals("reload", ignoreCase = true)) {
            settings.load()
            sender.sendMessage(settings.msg("reloaded"))
        } else {
            sender.sendMessage("/$label reload")
        }
        return true
    }
}
