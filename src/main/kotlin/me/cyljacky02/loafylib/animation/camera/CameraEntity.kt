package me.cyljacky02.loafylib.animation.camera

import com.github.retrooper.packetevents.PacketEvents
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerCamera
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityTeleport
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata
import com.github.retrooper.packetevents.protocol.entity.data.EntityData
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes
import com.github.retrooper.packetevents.util.Vector3d
import io.github.retrooper.packetevents.util.SpigotConversionUtil
import org.bukkit.Location
import org.bukkit.entity.Player
import java.util.UUID

/**
 * Manages a virtual display entity for camera control.
 *
 * This entity is packet-based (never actually spawned server-side),
 * making it lightweight and safe. The player spectates this entity
 * to achieve smooth camera movement without manipulating player state.
 *
 * Based on Typewriter's DisplayCameraAction approach:
 * - Uses TEXT_DISPLAY entity (invisible, zero hitbox)
 * - Client-side interpolation via teleportDuration
 * - No server-side entity overhead
 *
 * @param player The player whose camera will be controlled
 * @param interpolationTicks How many ticks the client interpolates movement (0-59)
 */
class CameraEntity(
    private val player: Player,
    private val interpolationTicks: Int = DEFAULT_INTERPOLATION
) {
    companion object {
        const val DEFAULT_INTERPOLATION = 10

        // Entity metadata indices for Display entities (1.19.4+)
        private const val INTERPOLATION_DURATION_INDEX = 9
        private const val POS_ROT_INTERPOLATION_DURATION_INDEX = 10
    }

    // Use Paper's safe entity ID allocation to avoid conflicts with server entities
    // This increments the server's internal ENTITY_COUNTER, guaranteeing uniqueness
    private val entityId: Int = org.bukkit.Bukkit.getUnsafe().nextEntityId()
    private val entityUuid: UUID = UUID.randomUUID()
    private var spawned: Boolean = false
    private var currentLocation: Location? = null

    /**
     * Spawn the camera entity at the given location.
     * Call [startSpectating] afterward to attach the player's camera.
     *
     * @param location Initial spawn location
     */
    fun spawn(location: Location) {
        if (spawned) return

        currentLocation = location.clone()

        // Spawn TEXT_DISPLAY entity via packet
        val spawnPacket = WrapperPlayServerSpawnEntity(
            entityId,
            entityUuid,
            EntityTypes.TEXT_DISPLAY,
            SpigotConversionUtil.fromBukkitLocation(location),
            location.yaw,
            0,
            null
        )

        // Set interpolation duration metadata
        val metadataPacket = WrapperPlayServerEntityMetadata(
            entityId,
            listOf(
                // Transformation interpolation duration
                EntityData(INTERPOLATION_DURATION_INDEX, EntityDataTypes.INT, interpolationTicks),
                // Position/rotation interpolation duration
                EntityData(POS_ROT_INTERPOLATION_DURATION_INDEX, EntityDataTypes.INT, interpolationTicks)
            )
        )

        val packetManager = PacketEvents.getAPI().playerManager
        packetManager.sendPacket(player, spawnPacket)
        packetManager.sendPacket(player, metadataPacket)

        spawned = true
    }
    
    /**
     * Make the player spectate this camera entity.
     * This changes only the camera view, not the player's actual position.
     */
    fun startSpectating() {
        if (!spawned) return
        
        val cameraPacket = WrapperPlayServerCamera(entityId)
        PacketEvents.getAPI().playerManager.sendPacket(player, cameraPacket)
    }
    
    /**
     * Stop spectating and return camera to player's own view.
     */
    fun stopSpectating() {
        val cameraPacket = WrapperPlayServerCamera(player.entityId)
        PacketEvents.getAPI().playerManager.sendPacket(player, cameraPacket)
    }
    
    /**
     * Teleport the camera entity to a new location.
     * The client will interpolate the movement over interpolationTicks.
     *
     * @param location Target location
     */
    fun teleport(location: Location) {
        if (!spawned) return

        currentLocation = location.clone()

        val position = Vector3d(location.x, location.y, location.z)
        val teleportPacket = WrapperPlayServerEntityTeleport(
            entityId,
            position,
            location.yaw,
            location.pitch,
            false
        )

        PacketEvents.getAPI().playerManager.sendPacket(player, teleportPacket)
    }
    
    /**
     * Despawn the camera entity.
     */
    fun despawn() {
        if (!spawned) return
        
        val destroyPacket = WrapperPlayServerDestroyEntities(entityId)
        PacketEvents.getAPI().playerManager.sendPacket(player, destroyPacket)
        
        spawned = false
        currentLocation = null
    }
    
    /**
     * Full cleanup: stop spectating and despawn.
     */
    fun cleanup() {
        stopSpectating()
        despawn()
    }
    
    fun isSpawned(): Boolean = spawned
    fun getCurrentLocation(): Location? = currentLocation?.clone()
    fun getEntityId(): Int = entityId
}

