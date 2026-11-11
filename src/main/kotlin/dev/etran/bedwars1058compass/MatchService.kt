package dev.etran.bedwars1058compass

import com.andrei1058.bedwars.api.arena.GameState
import com.andrei1058.bedwars.api.arena.IArena
import com.andrei1058.bedwars.api.arena.team.ITeam
import org.bukkit.ChatColor
import org.bukkit.entity.Player

/** Thin wrapper around the BedWars1058 API answering "is this player in a running match?". */
class MatchService(private val plugin: BedWars1058Compass) {

    fun arenaOf(player: Player): IArena? = plugin.bedWars.arenaUtil.getArenaByPlayer(player)

    /**
     * True while the player is inside a match that has started. This is the scope of the
     * compass takeover. Spectators are only included when configured.
     */
    fun isInMatch(player: Player): Boolean {
        val arena = arenaOf(player) ?: return false
        if (arena.status != GameState.playing) return false
        return arena.isPlayer(player) || (plugin.settings.includeSpectators && arena.isSpectator(player))
    }

    /** A living, non-respawning participant: the only people who can buy / use a tracker. */
    fun isActivePlayer(player: Player): Boolean {
        val arena = arenaOf(player) ?: return false
        return arena.status == GameState.playing && arena.isPlayer(player) && !arena.isReSpawning(player)
    }

    /** The team [player] plays for, or null when they are not in an arena. */
    fun teamOf(player: Player): ITeam? = arenaOf(player)?.getTeam(player)

    /** True once the bed of [player]'s own team is destroyed. This is what turns the compass into the tracker shop. */
    fun isBedDestroyed(player: Player): Boolean = teamOf(player)?.isBedDestroyed == true

    /** Enemy players that can currently be tracked by [player]. */
    fun trackableTargets(player: Player): List<Player> {
        val arena = arenaOf(player) ?: return emptyList()
        val own = arena.getTeam(player)
        return arena.players.filter { other ->
            other != player && other.isOnline && arena.isPlayer(other) &&
                !arena.isReSpawning(other) && arena.getTeam(other) != own
        }
    }

    fun coloredName(player: Player, arena: IArena? = arenaOf(player)): String {
        val chat: ChatColor = arena?.getTeam(player)?.color?.chat() ?: ChatColor.WHITE
        return "$chat${player.name}"
    }
}
