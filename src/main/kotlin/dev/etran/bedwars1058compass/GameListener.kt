package dev.etran.bedwars1058compass

import com.andrei1058.bedwars.api.arena.GameState
import com.andrei1058.bedwars.api.events.gameplay.GameStateChangeEvent
import com.andrei1058.bedwars.api.events.player.PlayerFirstSpawnEvent
import com.andrei1058.bedwars.api.events.player.PlayerKillEvent
import com.andrei1058.bedwars.api.events.player.PlayerLeaveArenaEvent
import com.andrei1058.bedwars.api.events.player.PlayerReSpawnEvent
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener

/** Reacts to BedWars1058 lifecycle events: give compasses, end trackers. */
class GameListener(private val plugin: BedWars1058Compass) : Listener {

    private fun giveLater(player: Player) {
        plugin.server.scheduler.runTaskLater(plugin, Runnable {
            if (player.isOnline && plugin.matches.isActivePlayer(player)) plugin.compasses.ensure(player)
        }, 2L)
    }

    @EventHandler
    fun onFirstSpawn(e: PlayerFirstSpawnEvent) = giveLater(e.player)

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

    @EventHandler
    fun onLeave(e: PlayerLeaveArenaEvent) = plugin.trackers.forget(e.player)

    @EventHandler
    fun onStateChange(e: GameStateChangeEvent) {
        if (e.newState == GameState.playing) {
            e.arena.players.forEach(::giveLater)
        } else {
            plugin.trackers.clear(e.arena)
        }
    }
}
