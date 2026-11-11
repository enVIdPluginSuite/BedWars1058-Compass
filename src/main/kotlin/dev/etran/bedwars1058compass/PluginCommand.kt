package dev.etran.bedwars1058compass

import com.andrei1058.bedwars.api.arena.GameState
import org.bukkit.ChatColor
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter

/*
 * ================================ MAP OF THIS FILE (read this first) ================================
 *   /bwcompass                         help
 *   /bwcompass status                  what the plugin is doing right now
 *   /bwcompass reload [config]         full reload (default) or config only
 *   /bwcompass config list [group]     show the groups / options
 *   /bwcompass config get <option>     show one option
 *   /bwcompass config set <option> <v> change one option, save it and apply it at once
 *   /bwcompass config reset <option>   back to the default
 *   /bwcompass config remove <id>      delete a quick message button
 *
 * Sections, top to bottom: onCommand (dispatch) -> status / reload / config handlers -> onTabComplete -> small output helpers.
 * Permissions: bwcompass.admin (everything), or bwcompass.reload / bwcompass.config for just those parts.
 *
 * "Full" reload vs "config" reload:
 *   config  re-reads config.yml and re-applies it (names, costs, menus, tick rate, locator bar...).
 *   full    does that AND rebuilds every component: all listeners are unregistered and registered again, every scheduled
 *           task is restarted. Active trackers survive, so a match in progress is not disturbed.
 * =====================================================================================================
 */

/** The in-game admin command: reload the plugin and edit config.yml without touching the server console. */
class PluginCommand(private val plugin: BedWars1058Compass) : CommandExecutor, TabCompleter {

    /** Entry point: picks the subcommand and checks its permission. */
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        when (args.firstOrNull()?.lowercase()) {
            "reload" -> if (allowed(sender, "bwcompass.reload")) reload(sender, args)
            "config" -> if (allowed(sender, "bwcompass.config")) config(sender, label, args)
            "status" -> if (allowed(sender, "bwcompass.admin")) status(sender)
            else -> if (allowed(sender, "bwcompass.admin")) help(sender, label)
        }
        return true
    }

    /** Checks [permission] and tells the sender when it is missing. */
    private fun allowed(sender: CommandSender, permission: String): Boolean {
        if (sender.hasPermission(permission)) return true
        sender.sendMessage(plugin.settings.msg("no-permission"))
        return false
    }

    // ---- handlers -------------------------------------------------------------------------

    /** `/bwcompass reload [config]`. A failed reload is reported in chat and logged with its stack trace. */
    private fun reload(sender: CommandSender, args: Array<out String>) {
        val configOnly = args.getOrNull(1).equals("config", ignoreCase = true)
        try {
            if (configOnly) plugin.reloadSettings() else plugin.reloadAll()
            say(sender, if (configOnly) "&aConfiguration reloaded." else "&aPlugin fully reloaded (listeners, tasks and configuration).")
        } catch (t: Throwable) {
            plugin.logger.severe("Reload failed: $t")
            t.printStackTrace()
            say(sender, "&cReload failed: ${t.message}. See the console for details.")
        }
    }

    /** `/bwcompass status`: a short overview that helps when something looks wrong. */
    private fun status(sender: CommandSender) {
        val arenas = plugin.bedWars.arenaUtil.arenas
        val playing = arenas.filter { it.status == GameState.playing }
        say(sender, "&7Version &f${plugin.description.version} &8| &7arenas &f${arenas.size} &8| &7playing &f${playing.size}")
        say(sender, "&7Players in matches &f${playing.sumOf { it.players.size }} &8| &7active trackers &f${plugin.trackers.activeCount()}")
        say(sender, "&7Locator bar: " + if (plugin.settings.disableLocatorBar) "&aswitched off in arena worlds" else "&eleft alone")
        say(sender, "&7Quick communication: " + if (plugin.settings.communicationEnabled) "&aon (${plugin.settings.quickMessages.size} buttons)" else "&eoff")
    }

    /** `/bwcompass config <list|get|set|reset|remove> ...`. Every successful change is saved and applied at once. */
    private fun config(sender: CommandSender, label: String, args: Array<out String>) {
        val editor = plugin.configEditor
        val path = args.getOrNull(2) ?: ""
        when (args.getOrNull(1)?.lowercase()) {
            "list" -> {
                val entries = editor.sectionsUnder(path)
                if (entries.isEmpty()) return say(sender, "&cNothing found under '$path'.")
                say(sender, "&7Options under &f${path.ifBlank { "(top level)" }}&7:")
                for ((name, isGroup) in entries) {
                    val full = if (path.isBlank()) name else "$path.$name"
                    if (isGroup) sender.sendMessage(color("&8 - &b$name &7(group, open with: config list $full)"))
                    else sender.sendMessage(color("&8 - &f$name&7 = &e${shorten(editor.format(editor.get(full)))}"))
                }
            }
            "get" -> {
                if (editor.isSection(path)) return say(sender, "&7'$path' is a group; use &fconfig list $path&7.")
                say(sender, "&f$path &7= &e${editor.format(editor.get(path))}")
            }
            "set" -> {
                val value = args.drop(3).joinToString(" ")
                if (path.isBlank() || value.isBlank()) return say(sender, "&cUsage: /$label config set <option> <value>  (lists: separate entries with ||)")
                changed(sender, editor.set(path, value), "&aSet &f$path &ato &e$value&a.")
            }
            "reset" -> changed(sender, editor.reset(path), "&aReset &f$path&a.")
            "remove" -> changed(sender, editor.remove(path), "&aRemoved &f$path&a.")
            else -> say(sender, "&cUsage: /$label config <list|get|set|reset|remove> ...")
        }
    }

    /** After a config change: show the error, or reload the settings and confirm. */
    private fun changed(sender: CommandSender, error: String?, success: String) {
        if (error != null) return say(sender, "&c$error")
        try {
            plugin.reloadSettings()
            say(sender, success)
        } catch (t: Throwable) {
            say(sender, "&cSaved, but applying it failed: ${t.message}")
        }
    }

    /** `/bwcompass`: the list of subcommands. */
    private fun help(sender: CommandSender, label: String) {
        say(sender, "&aBedWars1058-Compass &7v${plugin.description.version}")
        sender.sendMessage(color("&8 - &f/$label status &7what the plugin is doing"))
        sender.sendMessage(color("&8 - &f/$label reload [config] &7full reload, or just the configuration"))
        sender.sendMessage(color("&8 - &f/$label config list [group] &7browse the options"))
        sender.sendMessage(color("&8 - &f/$label config get <option>"))
        sender.sendMessage(color("&8 - &f/$label config set <option> <value> &7(lists: entries separated by ||)"))
        sender.sendMessage(color("&8 - &f/$label config reset <option> &7back to the default"))
        sender.sendMessage(color("&8 - &f/$label config remove <id> &7delete a quick message button"))
    }

    // ---- tab completion -------------------------------------------------------------------

    /** Completes subcommands, option paths and (for `set`) the current value of the option. */
    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<out String>): List<String> {
        if (!sender.hasPermission("bwcompass.admin") && !sender.hasPermission("bwcompass.config") && !sender.hasPermission("bwcompass.reload")) return emptyList()
        val editor = plugin.configEditor
        val options = when (args.size) {
            1 -> listOf("status", "reload", "config")
            2 -> when (args[0].lowercase()) {
                "reload" -> listOf("full", "config")
                "config" -> listOf("list", "get", "set", "reset", "remove")
                else -> emptyList()
            }
            3 -> if (!args[0].equals("config", ignoreCase = true)) emptyList() else when (args[1].lowercase()) {
                "list" -> editor.sectionsUnder("").map { it.first } + editor.leafKeys().map { it.substringBeforeLast('.', "") }.filter { it.isNotEmpty() }.distinct()
                "get", "set", "reset" -> editor.leafKeys()
                "remove" -> editor.leafKeys().filter { it.startsWith("quick-communication.messages.") }
                    .map { it.substringBeforeLast('.') }.distinct()
                else -> emptyList()
            }
            4 -> if (args[0].equals("config", ignoreCase = true) && args[1].equals("set", ignoreCase = true)) {
                listOf(editor.format(editor.get(args[2])))
            } else emptyList()
            else -> emptyList()
        }
        return options.filter { it.startsWith(args.last(), ignoreCase = true) }
    }

    // ---- output helpers -------------------------------------------------------------------

    /** Sends a prefixed line. */
    private fun say(sender: CommandSender, text: String) {
        sender.sendMessage(color(plugin.config.getString("messages.prefix") ?: "") + color(text))
    }

    private fun color(text: String) = ChatColor.translateAlternateColorCodes('&', text)

    /** Keeps long values (lore lists) to one chat line. */
    private fun shorten(text: String) = if (text.length > 70) text.take(67) + "..." else text
}
