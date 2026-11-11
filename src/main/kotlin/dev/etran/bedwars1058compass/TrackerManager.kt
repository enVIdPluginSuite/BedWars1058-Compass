package dev.etran.bedwars1058compass

import com.andrei1058.bedwars.api.arena.GameState
import com.andrei1058.bedwars.api.arena.IArena
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.entity.Player
import java.util.UUID

/**
 * Owns all active trackers and the repeating task that keeps every in-match compass
 * pointing where WE say, no matter what other plugins did with it in between.
 */
class TrackerManager(private val plugin: BedWars1058Compass) {

    private class Session(val target: UUID, val arena: IArena)

    private val sessions = HashMap<UUID, Session>()
    private var tickCounter = 0L
    private var taskId = -1

    /** (Re)starts the repeating task with the current tracker.update-ticks. A task that is already running is cancelled first. */
    fun start() {
        if (taskId != -1) plugin.server.scheduler.cancelTask(taskId)
        taskId = plugin.server.scheduler.runTaskTimer(plugin, Runnable { tick(false) }, 20L, plugin.settings.updateTicks).taskId
    }

    /** Runs one full pass right now (including the once-a-second compass and locator bar checks). Used after a reload. */
    fun refresh() = tick(true)

    /** How many trackers are active. */
    fun activeCount(): Int = sessions.size

    fun isTracking(player: Player) = sessions.containsKey(player.uniqueId)

    fun targetOf(player: Player): Player? =
        sessions[player.uniqueId]?.let { Bukkit.getPlayer(it.target) }

    fun track(tracker: Player, target: Player) {
        val arena = plugin.matches.arenaOf(tracker) ?: return
        sessions[tracker.uniqueId] = Session(target.uniqueId, arena)
        tracker.compassTarget = target.location
    }

    fun stop(tracker: Player, message: String? = null) {
        if (sessions.remove(tracker.uniqueId) != null && message != null) tracker.sendMessage(message)
    }

    fun stop(trackerId: UUID, message: String? = null) {
        sessions.remove(trackerId) ?: return
        if (message != null) Bukkit.getPlayer(trackerId)?.sendMessage(message)
    }

    /** Drops the player's own tracker and any tracker aimed at them. */
    fun forget(player: Player) {
        sessions.remove(player.uniqueId)
        val watchers = sessions.filterValues { it.target == player.uniqueId }.keys
        watchers.forEach { stop(it, plugin.settings.msg("target-gone")) }
    }

    fun clear(arena: IArena) {
        sessions.entries.removeIf { it.value.arena == arena }
    }

    fun clearAll() = sessions.clear()

    /**
     * One pass over every running arena: keep compasses correct, keep the locator bar off, and update every tracker.
     * [forceEnsure] skips the once-a-second throttle for the compass and locator bar checks.
     */
    private fun tick(forceEnsure: Boolean) {
        tickCounter += plugin.settings.updateTicks
        val ensureNow = forceEnsure || tickCounter % 20L < plugin.settings.updateTicks

        for (arena in ArrayList(plugin.bedWars.arenaUtil.arenas)) {
            if (ensureNow) plugin.locatorBar.apply(arena.world) // re-asserted every second in case a world reset its game rules
            if (arena.status != GameState.playing) continue
            for (player in ArrayList(arena.players)) {
                if (!player.isOnline || !plugin.matches.isInMatch(player)) continue
                if (arena.isPlayer(player) && ensureNow && plugin.settings.ensureCompass && !arena.isReSpawning(player)) {
                    plugin.compasses.ensure(player)
                }
                update(arena, player)
            }
        }
    }

    private fun update(arena: IArena, player: Player) {
        val session = sessions[player.uniqueId]
        if (session == null) {
            if (plugin.settings.untrackedPointsToSpawn) pointAt(player, arena.getTeam(player)?.spawn)
            return
        }

        val target = Bukkit.getPlayer(session.target)
        val gone = target == null || !target.isOnline || plugin.matches.arenaOf(target) != arena ||
            (arena.isSpectator(target) && !arena.isReSpawning(target))
        if (gone) {
            stop(player.uniqueId, plugin.settings.msg("target-gone"))
            return
        }
        target!!

        val name = plugin.matches.coloredName(target, arena)
        val text: String = when {
            arena.isReSpawning(target) ->
                plugin.settings.msg("action-bar-respawning", "player" to name)
            target.world != player.world ->
                plugin.settings.msg("action-bar-other-world", "player" to name)
            else -> {
                pointAt(player, target.location)
                val distance = player.location.distance(target.location).toInt()
                plugin.settings.msg("action-bar", "player" to name, "distance" to distance.toString())
            }
        }

        val holding = Compasses.isCompass(plugin.bedWars.versionSupport.getItemInHand(player))
        if (!plugin.settings.actionBarOnlyWhenHolding || holding) {
            plugin.bedWars.versionSupport.playAction(player, text)
        }
    }

    private fun pointAt(player: Player, location: Location?) {
        if (location == null || location.world != player.world) return
        val current = player.compassTarget
        if (current == null || current.world != location.world || current.distanceSquared(location) > 0.25) {
            player.compassTarget = location
        }
    }
}
