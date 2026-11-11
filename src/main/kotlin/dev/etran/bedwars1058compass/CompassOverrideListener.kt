package dev.etran.bedwars1058compass

import org.bukkit.entity.Player
import org.bukkit.event.Event
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.player.PlayerAnimationEvent
import org.bukkit.event.player.PlayerTeleportEvent
import org.bukkit.event.inventory.ClickType
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.event.inventory.InventoryOpenEvent
import org.bukkit.event.inventory.InventoryType
import org.bukkit.event.player.PlayerCommandPreprocessEvent
import org.bukkit.event.player.PlayerDropItemEvent
import org.bukkit.event.player.PlayerInteractAtEntityEvent
import org.bukkit.event.player.PlayerInteractEntityEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerQuitEvent
import java.util.UUID

/**
 * Makes this plugin the sole owner of compass behaviour during a match.
 *
 * Strategy, in layers:
 *  1. LOWEST  - we run first: cancel the interaction (so every plugin that respects
 *               cancellation skips it) and open our own menu.
 *  2. HIGHEST - and MONITOR: if a plugin un-cancelled the event, cancel it again.
 *  3. Plugins that ignore cancellation and react anyway (open a menu, run a command)
 *     are caught by suppressing InventoryOpen / command events in a short window after
 *     any compass click.
 *  4. Compasses can't be dropped or moved into foreign containers, and the compass
 *     target + item are re-asserted by [TrackerManager] on a timer.
 */
class CompassOverrideListener(private val plugin: BedWars1058Compass) : Listener {

    private val lastCompassUse = HashMap<UUID, Long>()
    private val lastMenuOpen = HashMap<UUID, Long>()

    /** True while [player] is inside a running match (the scope of the whole takeover). */
    private fun inMatch(player: Player) = plugin.matches.isInMatch(player)

    /** Remembers that [player] just used a compass. This opens the short window in which foreign reactions are cancelled. */
    private fun touch(player: Player) {
        lastCompassUse[player.uniqueId] = System.currentTimeMillis()
    }

    /** True when [player] used a compass less than override.suppress-window-ms ago. */
    private fun recentlyUsed(player: Player): Boolean {
        val at = lastCompassUse[player.uniqueId] ?: return false
        return System.currentTimeMillis() - at <= plugin.settings.suppressWindowMs
    }

    /** Cancels an interaction completely: the event, the item use and the block use. */
    private fun lock(e: PlayerInteractEvent) {
        e.isCancelled = true
        e.setUseItemInHand(Event.Result.DENY)
        e.setUseInteractedBlock(Event.Result.DENY)
    }

    /** True for any click or use of a compass by a player in a match (stepping on pressure plates does not count). */
    private fun isCompassClick(e: PlayerInteractEvent): Boolean =
        e.action != Action.PHYSICAL && Compasses.isCompass(e.item) && inMatch(e.player)

    // ---- 1. first word -------------------------------------------------------------------

    /** Runs before every other plugin: locks the click and, for a right-click, opens what the compass currently offers (quick messages or the tracker shop). */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    fun onInteractFirst(e: PlayerInteractEvent) {
        if (!isCompassClick(e)) return
        val player = e.player
        touch(player)
        lock(e)

        val rightClick = e.action == Action.RIGHT_CLICK_AIR || e.action == Action.RIGHT_CLICK_BLOCK
        if (!rightClick || !plugin.matches.isActivePlayer(player)) return

        // Modern servers fire the event once per hand; don't reopen the menu twice.
        val now = System.currentTimeMillis()
        if (now - (lastMenuOpen[player.uniqueId] ?: 0L) < 200L) return
        lastMenuOpen[player.uniqueId] = now
        plugin.menus.openCompass(player)
    }

    // ---- 2. last words -------------------------------------------------------------------

    /** Second layer: if a plugin un-cancelled the click after us, cancel it again. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    fun onInteractLate(e: PlayerInteractEvent) {
        if (isCompassClick(e) && (!e.isCancelled || e.useItemInHand() != Event.Result.DENY)) lock(e)
    }

    /** Third layer, last word: same as [onInteractLate], at MONITOR so nothing can run after it. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    fun onInteractFinal(e: PlayerInteractEvent) {
        if (isCompassClick(e) && (!e.isCancelled || e.useItemInHand() != Event.Result.DENY)) lock(e)
    }

    /** Right-clicking an entity with a compass is cancelled before other plugins see it. */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    fun onEntityInteractFirst(e: PlayerInteractEntityEvent) = entityInteract(e)

    /** Re-cancels an entity interaction that another plugin un-cancelled. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    fun onEntityInteractLate(e: PlayerInteractEntityEvent) = entityInteract(e)

    /** Same as [onEntityInteractFirst], for the "at entity" variant (armour stands). */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    fun onEntityAtInteractFirst(e: PlayerInteractAtEntityEvent) = entityInteract(e)

    /** Same as [onEntityInteractLate], for the "at entity" variant. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    fun onEntityAtInteractLate(e: PlayerInteractAtEntityEvent) = entityInteract(e)

    /** Shared body of the four entity handlers: marks a compass use and cancels the interaction. */
    private fun entityInteract(e: PlayerInteractEntityEvent) {
        val player = e.player
        if (!inMatch(player)) return
        if (!Compasses.isCompass(plugin.bedWars.versionSupport.getItemInHand(player))) return
        touch(player)
        e.isCancelled = true
    }

    // ---- 3. plugins that ignore cancellation ---------------------------------------------

    /** Cancels any menu another plugin opens right after a compass click (our own menus are exempt). */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    fun onForeignInventoryOpen(e: InventoryOpenEvent) {
        if (!plugin.settings.suppressForeignReactions) return
        val player = e.player as? Player ?: return
        if (e.inventory.holder is CompassMenu) return
        if (inMatch(player) && recentlyUsed(player)) e.isCancelled = true
    }

    /** Cancels any command another plugin runs for the player right after a compass click. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    fun onForeignCommand(e: PlayerCommandPreprocessEvent) {
        if (!plugin.settings.suppressForeignReactions) return
        if (inMatch(e.player) && recentlyUsed(e.player)) e.isCancelled = true
    }

    // ---- 3b. punches / swings (e.g. "hit with a compass -> teleport") --------------------
    // We must NOT cancel the damage itself (a compass punch still has to hurt like a fist),
    // so instead we mark the swing as a compass use. That opens the suppression window, in
    // which any plugin-driven teleport, menu or command for that player is cancelled.

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    fun onPunch(e: EntityDamageByEntityEvent) {
        val damager = e.damager as? Player ?: return
        if (!inMatch(damager)) return
        if (Compasses.isCompass(plugin.bedWars.versionSupport.getItemInHand(damager))) touch(damager)
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    fun onSwing(e: PlayerAnimationEvent) {
        val player = e.player
        if (!inMatch(player)) return
        if (Compasses.isCompass(plugin.bedWars.versionSupport.getItemInHand(player))) touch(player)
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    fun onForeignTeleport(e: PlayerTeleportEvent) {
        if (!plugin.settings.suppressForeignReactions) return
        val player = e.player
        if (!inMatch(player) || !recentlyUsed(player)) return
        // Only plugin/command driven teleports; never touch BedWars' own respawn handling.
        if (e.cause == PlayerTeleportEvent.TeleportCause.PLUGIN ||
            e.cause == PlayerTeleportEvent.TeleportCause.COMMAND ||
            e.cause == PlayerTeleportEvent.TeleportCause.UNKNOWN
        ) e.isCancelled = true
    }

    // ---- 4. keep the item where it belongs -----------------------------------------------

    /** A compass can not be dropped during a match (override.cancel-drop). */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    fun onDrop(e: PlayerDropItemEvent) {
        if (!plugin.settings.cancelDrop) return
        if (Compasses.isCompass(e.itemDrop.itemStack) && inMatch(e.player)) e.isCancelled = true
    }

    /** Stops the compass being moved into a chest, shop or other menu: placing it, shift-clicking it and number-key swaps. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    fun onInventoryClick(e: InventoryClickEvent) {
        if (!plugin.settings.blockContainerMoves) return
        val player = e.whoClicked as? Player ?: return
        val top = e.inventory
        if (top.holder is CompassMenu || top.type == InventoryType.CRAFTING) return // our menus / own inventory only
        if (!inMatch(player)) return

        val clickedTop = e.rawSlot in 0 until top.size
        val hotbarItem = if (e.click == ClickType.NUMBER_KEY) player.inventory.getItem(e.hotbarButton) else null

        val movesIn = (clickedTop && Compasses.isCompass(e.cursor)) ||
            (clickedTop && Compasses.isCompass(hotbarItem)) ||
            (e.isShiftClick && !clickedTop && Compasses.isCompass(e.currentItem))
        if (movesIn) e.isCancelled = true
    }

    /** Same protection as [onInventoryClick] for click-and-drag. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    fun onInventoryDrag(e: InventoryDragEvent) {
        if (!plugin.settings.blockContainerMoves) return
        val player = e.whoClicked as? Player ?: return
        val top = e.inventory
        if (top.holder is CompassMenu || top.type == InventoryType.CRAFTING) return
        if (!inMatch(player) || !Compasses.isCompass(e.oldCursor)) return
        if (e.rawSlots.any { it < top.size }) e.isCancelled = true
    }

    /** Forgets the player's timers. */
    @EventHandler
    fun onQuit(e: PlayerQuitEvent) {
        lastCompassUse.remove(e.player.uniqueId)
        lastMenuOpen.remove(e.player.uniqueId)
    }
}
