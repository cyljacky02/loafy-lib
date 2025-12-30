package me.cyljacky02.loafylib.event

import me.cyljacky02.loafylib.util.hasItemKey
import org.bukkit.NamespacedKey
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.player.PlayerInteractEvent

// ============================================================================
// Item Key Matching Extensions
// ============================================================================

/**
 * Checks if the item involved in this interaction matches the given key.
 * Returns false if no item is involved (null-safe).
 *
 * ```kotlin
 * @EventHandler
 * suspend fun onInteract(event: PlayerInteractEvent) {
 *     if (!event.matchesItem(wandKey)) return
 *     // Handle wand interaction
 * }
 * ```
 */
fun PlayerInteractEvent.matchesItem(key: NamespacedKey): Boolean {
    return item?.hasItemKey(key) == true
}

/**
 * Checks if the item used to place the block matches the given key.
 *
 * ```kotlin
 * @EventHandler
 * fun onPlace(event: BlockPlaceEvent) {
 *     if (!event.matchesItem(customBlockKey)) return
 *     // Handle custom block placement
 * }
 * ```
 */
fun BlockPlaceEvent.matchesItem(key: NamespacedKey): Boolean {
    return itemInHand.hasItemKey(key)
}

/**
 * Checks if the item used to break the block matches the given key.
 * Uses the player's main hand item.
 *
 * ```kotlin
 * @EventHandler
 * fun onBreak(event: BlockBreakEvent) {
 *     if (!event.matchesItem(specialPickaxeKey)) return
 *     // Handle special pickaxe breaking
 * }
 * ```
 */
fun BlockBreakEvent.matchesItem(key: NamespacedKey): Boolean {
    return player.inventory.itemInMainHand.hasItemKey(key)
}
