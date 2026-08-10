package me.cyljacky02.loafylib.glow

import net.kyori.adventure.text.format.NamedTextColor
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Per-player state container for glowing entities and displays.
 *
 * All collections are thread-safe for concurrent access from multiple threads
 * (main thread, async tasks, Netty threads).
 */
internal class PlayerGlowData {
    /**
     * Entity ID -> GlowState for existing entities that are glowing for this player.
     */
    val glowingEntities: ConcurrentHashMap<Int, GlowState> = ConcurrentHashMap()

    /**
     * Entity ID -> last observed server-side entity flags byte.
     * Used to preserve non-glow flags (invisible, on-fire, etc.) when toggling glow.
     */
    val entityFlags: ConcurrentHashMap<Int, Byte> = ConcurrentHashMap()

    /**
     * Set of virtual entity IDs for display entities visible to this player.
     * Only tracks existence - no additional state needed since packets are sent immediately.
     */
    val activeDisplays: ConcurrentHashMap.KeySetView<Int, Boolean> =
        ConcurrentHashMap.newKeySet()

    /**
     * Team colors already sent to this player (avoid duplicate team creation packets).
     */
    val sentTeamColors: ConcurrentHashMap.KeySetView<NamedTextColor, Boolean> =
        ConcurrentHashMap.newKeySet()

    /**
     * Virtual entity ID -> MarkerState for shulker markers visible to this player.
     */
    val activeMarkers: ConcurrentHashMap<Int, MarkerState> = ConcurrentHashMap()
}

/**
 * State for an existing entity that is glowing for a specific player.
 *
 * @property entityId the Minecraft entity runtime ID
 * @property entityUuid the entity UUID (used for team membership)
 * @property color the glow color (team color)
 */
internal data class GlowState(
    val entityId: Int,
    val entityUuid: UUID,
    val color: NamedTextColor
)

/**
 * State for a virtual shulker marker (invisible glowing shulker) visible to a specific player.
 *
 * @property entityUuid the entity UUID (used for team membership)
 * @property color the glow color (team color, 16 options)
 */
internal data class MarkerState(
    val entityUuid: UUID,
    val color: NamedTextColor
)
