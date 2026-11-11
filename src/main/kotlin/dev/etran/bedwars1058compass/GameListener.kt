package dev.etran.bedwars1058compass

import com.andrei1058.bedwars.api.arena.GameState
import com.andrei1058.bedwars.api.events.gameplay.GameStateChangeEvent
import com.andrei1058.bedwars.api.events.player.PlayerBedBreakEvent
import com.andrei1058.bedwars.api.events.player.PlayerFirstSpawnEvent
import com.andrei1058.bedwars.api.events.player.PlayerKillEvent
import com.andrei1058.bedwars.api.events.player.PlayerLeaveArenaEvent
import com.andrei1058.bedwars.api.events.player.PlayerReSpawnEvent
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener

/** Reacts to BedWars1058 lifecycle events: give compasses, end trackers. */
class GameListener(private val plugin: BedWars1058Compass) : Listener {

    private fun giveLater(player: Player) {
        plugin.server.scheduler.runTaskLater(plugin, Runnable {
            if (player.isOnline && plugin.matches.isActivePlayer(player)) plugin.compasses.ensure(player)
        }, 2L)
    }

    @EventHandler
    fun onFirstSpawn(e: PlayerFirstSpawnEvent) {
        plugin.locatorBar.apply(e.arena.world)
        giveLater(e.player)
    }

    @EventHandler
    fun onRespawn(e: PlayerReSpawnEvent) {
        plugin.trackers.stop(e.player) // belt and braces: a respawned player never keeps an old tracker
        giveLater(e.player)
    }

    @EventHandler
    fun onKill(e: PlayerKillEvent) {
        if (plugin.settings.stopOnDeath && plugin.trackers.isTracking(e.victim)) {
            plugin.trackers.stop(e.victim, plugin.settings.msg("lost-on-death"))
        }
    }

    /**
     * A bed was destroyed: every member of that team gets the tracker shop compass.
     * Runs a couple of ticks later because BedWars1058 marks the bed as destroyed after it fires the event.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    fun onBedBreak(e: PlayerBedBreakEvent) {
        val members = ArrayList(e.victimTeam.members)
        plugin.server.scheduler.runTaskLater(plugin, Runnable {
            for (member in members) {
                if (!member.isOnline || !plugin.matches.isActivePlayer(member)) continue
                plugin.compasses.ensure(member)
                member.sendMessage(plugin.settings.msg("tracker-unlocked"))
            }
        }, 3L)
    }

    @EventHandler
    fun onLeave(e: PlayerLeaveArenaEvent) {
        plugin.trackers.forget(e.player)
        plugin.communication.forget(e.player)
    }

    @EventHandler
    fun onStateChange(e: GameStateChangeEvent) {
        plugin.locatorBar.apply(e.arena.world)
        if (e.newState == GameState.playing) {
            e.arena.players.forEach(::giveLater)
        } else {
            plugin.trackers.clear(e.arena)
        }
    }
}
