package me.cyljacky02.loafylib.glow

import net.kyori.adventure.text.Component
import org.bukkit.Color
import org.bukkit.Location
import org.bukkit.block.data.BlockData
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.util.Transformation
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.logging.Logger

/**
 * No-operation implementation of [GlowingService] used when PacketEvents is not available.
 *
 * All methods are no-ops that return default values. A single warning is logged
 * per session when any method is called, to inform users that PacketEvents is required.
 *
 * This implementation ensures LoafyLib loads successfully without PacketEvents,
 * providing graceful degradation of the glowing feature.
 *
 * @property logger the logger to use for warning messages
 */
internal class NoOpGlowingService(
    private val logger: Logger
) : GlowingService {

    /**
     * Tracks whether the warning has been logged this session.
     * Uses AtomicBoolean for thread-safe compare-and-set operation.
     */
    private val warningLogged = AtomicBoolean(false)

    /**
     * Logs a warning message once per session when glowing methods are called
     * without PacketEvents installed.
     */
    private fun logWarningOnce() {
        if (warningLogged.compareAndSet(false, true)) {
            logger.warning(
                "GlowingService requires PacketEvents. " +
                "Install packetevents to enable glowing functionality."
            )
        }
    }

    override fun isAvailable(): Boolean = false

    // ==================== Existing Entity Glowing ====================

    override fun setGlowing(entityId: Int, entityUuid: UUID, receiver: Player, color: GlowColor?) {
        logWarningOnce()
    }

    override fun unsetGlowing(entityId: Int, receiver: Player) {
        logWarningOnce()
    }

    override fun isGlowing(entityId: Int, receiver: Player): Boolean {
        logWarningOnce()
        return false
    }

    // ==================== Display Entity Glowing ====================

    override fun spawnGlowingBlock(
        location: Location,
        blockData: BlockData,
        receiver: Player,
        color: Color
    ): Int {
        logWarningOnce()
        return 0
    }

    override fun spawnGlowingItem(
        location: Location,
        itemStack: ItemStack,
        receiver: Player,
        color: Color
    ): Int {
        logWarningOnce()
        return 0
    }

    override fun spawnGlowingText(
        location: Location,
        text: Component,
        receiver: Player,
        color: Color
    ): Int {
        logWarningOnce()
        return 0
    }

    override fun removeDisplay(entityId: Int, receiver: Player) {
        logWarningOnce()
    }

    override fun updateDisplayColor(entityId: Int, receiver: Player, color: Color) {
        logWarningOnce()
    }

    override fun updateDisplayTransform(entityId: Int, receiver: Player, transform: Transformation) {
        logWarningOnce()
    }

    override fun getActiveDisplays(receiver: Player): Set<Int> {
        logWarningOnce()
        return emptySet()
    }

    // ==================== Shulker Marker Glowing ====================

    override fun spawnGlowingMarker(
        location: Location,
        receiver: Player,
        color: GlowColor
    ): Int {
        logWarningOnce()
        return 0
    }

    override fun removeMarker(entityId: Int, receiver: Player) {
        logWarningOnce()
    }

    override fun updateMarkerColor(entityId: Int, receiver: Player, color: GlowColor) {
        logWarningOnce()
    }

    override fun getActiveMarkers(receiver: Player): Set<Int> {
        logWarningOnce()
        return emptySet()
    }

    // ==================== Lifecycle ====================

    override suspend fun initialize() {
        // No-op: nothing to initialize without PacketEvents
    }

    override suspend fun shutdown() {
        // No-op: nothing to clean up
    }
}
