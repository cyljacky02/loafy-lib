package me.cyljacky02.loafylib.glow

import me.cyljacky02.loafylib.plugin.PluginComponent
import net.kyori.adventure.text.Component
import org.bukkit.Color
import org.bukkit.Location
import org.bukkit.block.data.BlockData
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.util.Transformation

/**
 * Service interface for managing per-player glowing entity effects.
 *
 * Provides two glowing approaches:
 * 1. **Existing Entity Glowing** - Uses entity metadata flag + team colors (16 NamedTextColors)
 * 2. **Display Entity Glowing** - Uses virtual BlockDisplay/ItemDisplay/TextDisplay with custom RGB glow colors
 *
 * This service requires PacketEvents as a soft dependency. When PacketEvents is not available,
 * [isAvailable] returns false and all methods become no-ops with a single warning logged per session.
 *
 * ## Thread Safety
 * All methods are thread-safe and can be called from any thread (main, async, or Netty threads).
 *
 * ## Usage Example
 * ```kotlin
 * val glowingService = registry.get<GlowingService>()
 *
 * // Make an existing entity glow for a specific player
 * if (glowingService.isAvailable()) {
 *     glowingService.setGlowing(entity, player, ChatColor.RED)
 * }
 *
 * // Spawn a glowing block with custom RGB color
 * val entityId = glowingService.spawnGlowingBlock(
 *     location = block.location,
 *     blockData = Material.DIAMOND_BLOCK.createBlockData(),
 *     receiver = player,
 *     color = Color.fromRGB(255, 128, 0) // Orange
 * )
 *
 * // Later, remove the display
 * glowingService.removeDisplay(entityId, player)
 * ```
 *
 * @see PluginComponent
 */
interface GlowingService : PluginComponent {

    /**
     * Checks if the glowing service is available.
     *
     * Returns false when PacketEvents is not installed, in which case
     * all other methods become no-ops.
     *
     * @return true if PacketEvents is available and glowing functionality works
     */
    fun isAvailable(): Boolean

    // ==================== Existing Entity Glowing (16 team colors) ====================

    /**
     * Makes an existing entity glow for a specific player.
     *
     * Uses team-based coloring which supports 16 [NamedTextColor] values.
     * The glow effect is only visible to the specified receiver.
     *
     * @param entity the entity to make glow
     * @param receiver the player who will see the glow effect
     * @param color the glow color (null defaults to white)
     */
    fun setGlowing(entity: Entity, receiver: Player, color: GlowColor? = null)

    /**
     * Removes the glowing effect from an entity for a specific player.
     *
     * @param entity the entity to stop glowing
     * @param receiver the player who should no longer see the glow
     */
    fun unsetGlowing(entity: Entity, receiver: Player)

    /**
     * Checks if an entity is currently glowing for a specific player.
     *
     * @param entity the entity to check
     * @param receiver the player to check visibility for
     * @return true if the entity is glowing for this player
     */
    fun isGlowing(entity: Entity, receiver: Player): Boolean

    // ==================== Display Entity Glowing (unlimited RGB) ====================

    /**
     * Spawns a glowing block display entity visible only to the specified player.
     *
     * Uses BlockDisplay entity with custom RGB glow color via glow_color_override.
     * The returned entity ID can be used to update or remove the display.
     *
     * @param location the location to spawn the display
     * @param blockData the block data to display
     * @param receiver the player who will see the display
     * @param color the RGB glow color
     * @return unique negative entity ID for this display
     */
    fun spawnGlowingBlock(
        location: Location,
        blockData: BlockData,
        receiver: Player,
        color: Color
    ): Int

    /**
     * Spawns a glowing item display entity visible only to the specified player.
     *
     * Uses ItemDisplay entity with custom RGB glow color via glow_color_override.
     *
     * @param location the location to spawn the display
     * @param itemStack the item to display
     * @param receiver the player who will see the display
     * @param color the RGB glow color
     * @return unique negative entity ID for this display
     */
    fun spawnGlowingItem(
        location: Location,
        itemStack: ItemStack,
        receiver: Player,
        color: Color
    ): Int

    /**
     * Spawns a glowing text display entity visible only to the specified player.
     *
     * Uses TextDisplay entity with custom RGB glow color via glow_color_override.
     *
     * @param location the location to spawn the display
     * @param text the text component to display
     * @param receiver the player who will see the display
     * @param color the RGB glow color
     * @return unique negative entity ID for this display
     */
    fun spawnGlowingText(
        location: Location,
        text: Component,
        receiver: Player,
        color: Color
    ): Int

    /**
     * Removes a virtual display entity for a specific player.
     *
     * If the entity ID is not found, this method does nothing (idempotent).
     *
     * @param entityId the entity ID returned from spawn methods
     * @param receiver the player to remove the display for
     */
    fun removeDisplay(entityId: Int, receiver: Player)

    /**
     * Updates the glow color of an existing display entity.
     *
     * @param entityId the entity ID returned from spawn methods
     * @param receiver the player who sees the display
     * @param color the new RGB glow color
     */
    fun updateDisplayColor(entityId: Int, receiver: Player, color: Color)

    /**
     * Updates the transformation (position, rotation, scale) of an existing display entity.
     *
     * @param entityId the entity ID returned from spawn methods
     * @param receiver the player who sees the display
     * @param transform the new transformation
     */
    fun updateDisplayTransform(entityId: Int, receiver: Player, transform: Transformation)

    /**
     * Gets all active display entity IDs for a specific player.
     *
     * @param receiver the player to get displays for
     * @return set of entity IDs currently active for this player
     */
    fun getActiveDisplays(receiver: Player): Set<Int>

    // ==================== Shulker Marker Glowing (invisible glow outline) ====================

    /**
     * Spawns an invisible glowing shulker marker at the specified location.
     *
     * This creates a pure glow outline without any visible block, using an invisible
     * shulker entity. The shulker's 1x1x1 bounding box provides the glow outline shape.
     *
     * Use this when you need "just the glow" without displaying an actual block.
     * For displaying a glowing block, use [spawnGlowingBlock] instead.
     *
     * Note: Limited to 16 team colors (GlowColor). For RGB colors, use Display entities.
     *
     * @param location the location to spawn the marker (will be block-aligned)
     * @param receiver the player who will see the marker
     * @param color the glow color (one of 16 team colors)
     * @return unique negative entity ID for this marker
     */
    fun spawnGlowingMarker(
        location: Location,
        receiver: Player,
        color: GlowColor = GlowColor.WHITE
    ): Int

    /**
     * Removes a glowing marker for a specific player.
     *
     * If the entity ID is not found, this method does nothing (idempotent).
     *
     * @param entityId the entity ID returned from [spawnGlowingMarker]
     * @param receiver the player to remove the marker for
     */
    fun removeMarker(entityId: Int, receiver: Player)

    /**
     * Updates the glow color of an existing marker.
     *
     * @param entityId the entity ID returned from [spawnGlowingMarker]
     * @param receiver the player who sees the marker
     * @param color the new glow color
     */
    fun updateMarkerColor(entityId: Int, receiver: Player, color: GlowColor)

    /**
     * Gets all active marker entity IDs for a specific player.
     *
     * @param receiver the player to get markers for
     * @return set of entity IDs currently active for this player
     */
    fun getActiveMarkers(receiver: Player): Set<Int>
}
