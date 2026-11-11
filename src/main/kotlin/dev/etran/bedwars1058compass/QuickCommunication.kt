package dev.etran.bedwars1058compass

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import java.util.UUID

/*
 * ================================ MAP OF THIS FILE (read this first) ================================
 *   1. CommunicationMenu ... the holder of the menu, remembers which slot sends which message.
 *   2. QuickCommunication .. the feature:
 *        open()    builds and shows the menu from quick-communication.messages.
 *        send()    sends the chosen message to the sender's team (cooldown, placeholders, format).
 *        forget()  drops the cooldown of a player who left.
 *
 * Life of ONE message: right-click the compass (CompassOverrideListener) -> TrackerMenus.openCompass -> open() -> click
 * (MenuListener) -> send() -> every member of the sender's team gets one chat line. Nobody outside the team sees it.
 * =====================================================================================================
 */

/** Holder of the quick communication menu. [slots] maps an inventory slot to the key of the message it sends. */
class CommunicationMenu : CompassMenu() {
    val slots = HashMap<Int, String>()
}

/** The Hypixel-style quick team chat that the compass offers until the team's bed is destroyed. */
class QuickCommunication(private val plugin: BedWars1058Compass) {

    /** When each player last sent a message (ms), for the cooldown. */
    private val lastSent = HashMap<UUID, Long>()

    /** Shows the menu to [player]. Does nothing when the feature is off or there are no messages. */
    fun open(player: Player) {
        val s = plugin.settings
        if (!s.communicationEnabled || s.quickMessages.isEmpty()) return

        val holder = CommunicationMenu()
        val inv = Bukkit.createInventory(holder, 27, s.communicationTitle)
        holder.inv = inv
        for (i in 0 until inv.size) inv.setItem(i, Items.filler())

        for (entry in s.quickMessages) {
            val preview = Settings.color(fill(entry.message, player))
            val lore = listOf(Settings.color("&7Sends to your team:"), Settings.color("&f$preview"), "", Settings.color("&eClick to send!"))
            inv.setItem(entry.slot, Items.named(entry.material, entry.name, lore))
            holder.slots[entry.slot] = entry.key
        }
        plugin.menus.opened(player)
        player.openInventory(inv)
    }

    /**
     * Sends the message with key [key] from [player] to their team.
     * STEPS: 1) still allowed (living player, bed alive, feature on)  2) cooldown  3) fill in the placeholders  4) tell every teammate.
     */
    fun send(player: Player, key: String) {
        val s = plugin.settings
        if (!s.communicationEnabled || !plugin.matches.isActivePlayer(player)) return
        if (plugin.compasses.modeFor(player) != CompassMode.COMMUNICATION) return
        val entry = s.quickMessages.firstOrNull { it.key == key } ?: return
        val team = plugin.matches.teamOf(player) ?: return

        val now = System.currentTimeMillis()
        val waitMs = (lastSent[player.uniqueId] ?: 0L) + s.communicationCooldownMs - now
        if (waitMs > 0) {
            val seconds = (waitMs + 999) / 1000
            player.sendMessage(s.msg("communication-cooldown", "seconds" to seconds.toString()))
            return
        }
        lastSent[player.uniqueId] = now

        val line = Settings.color(
            fill(s.communicationFormat, player).replace("{message}", Settings.color(fill(entry.message, player)))
        )
        for (mate in ArrayList(team.members)) {
            if (mate.isOnline) mate.sendMessage(line)
        }
    }

    /** Forgets the cooldown of a player (they left the match or the server). */
    fun forget(player: Player) {
        lastSent.remove(player.uniqueId)
    }

    /** Replaces {player}, {team}, {x}, {y} and {z} for [player]. {message} is handled by the caller. */
    private fun fill(text: String, player: Player): String {
        val arena = plugin.matches.arenaOf(player)
        val location = player.location
        return text
            .replace("{player}", plugin.matches.coloredName(player, arena))
            .replace("{team}", arena?.getTeam(player)?.name ?: "")
            .replace("{x}", location.blockX.toString())
            .replace("{y}", location.blockY.toString())
            .replace("{z}", location.blockZ.toString())
    }
}
