package me.cyljacky02.loafylib.animation.camera

import com.github.retrooper.packetevents.PacketEvents
import com.github.retrooper.packetevents.event.PacketListenerAbstract
import com.github.retrooper.packetevents.event.PacketListenerPriority
import com.github.retrooper.packetevents.event.PacketReceiveEvent
import com.github.retrooper.packetevents.event.PacketSendEvent
import com.github.retrooper.packetevents.protocol.packettype.PacketType
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerPosition
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerPositionAndRotation
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerPositionAndLook
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityTargetEvent
import org.bukkit.event.entity.EntityTargetLivingEntityEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.plugin.Plugin
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Handles packet interception for camera-controlled players.
 *
 * Based on Typewriter's approach:
 * - Fakes player Y-coordinate (+500) to prevent self-interaction
 * - Corrects incoming position packets (-500)
 * - Cancels self-interaction packets
 * - Cancels entity targeting events (mobs won't target camera-controlled players)
 * - Cleans up on player disconnect
 *
 * Thread-safe: Uses ConcurrentHashMap for controlled players.
 */
class CameraPacketHandler(private val plugin: Plugin) : PacketListenerAbstract(PacketListenerPriority.HIGH), Listener {

    companion object {
        /** Y-offset to prevent self-interaction (same as Typewriter) */
        const val Y_OFFSET = 500.0

        @Volatile
        private var instance: CameraPacketHandler? = null
        
        @Volatile
        private var registered = false
        
        private val lock = Any()

        /**
         * Get or create the singleton instance.
         */
        fun getInstance(plugin: Plugin): CameraPacketHandler {
            return instance ?: synchronized(lock) {
                instance ?: CameraPacketHandler(plugin).also { instance = it }
            }
        }

        /**
         * Register the packet listener if not already registered.
         */
        fun register(plugin: Plugin) {
            if (registered) return
            synchronized(lock) {
                if (registered) return
                val handler = getInstance(plugin)
                PacketEvents.getAPI().eventManager.registerListener(handler)
                Bukkit.getPluginManager().registerEvents(handler, plugin)
                registered = true
            }
        }

        /**
         * Unregister the packet listener.
         */
        fun unregister() {
            synchronized(lock) {
                instance?.let {
                    PacketEvents.getAPI().eventManager.unregisterListener(it)
                    HandlerList.unregisterAll(it)
                    it.controlledPlayers.clear()
                }
                instance = null
                registered = false
            }
        }
    }

    /** Players currently under camera control (UUID -> their entity ID) */
    private val controlledPlayers = ConcurrentHashMap<UUID, Int>()

    /**
     * Start camera control for a player.
     * Enables Y-offset faking and self-interaction prevention.
     */
    fun startCameraControl(player: Player) {
        controlledPlayers[player.uniqueId] = player.entityId
    }

    /**
     * Stop camera control for a player.
     */
    fun stopCameraControl(player: Player) {
        controlledPlayers.remove(player.uniqueId)
    }

    /**
     * Check if a player is under camera control.
     */
    fun isUnderCameraControl(player: Player): Boolean {
        return controlledPlayers.containsKey(player.uniqueId)
    }

    /**
     * Handle player disconnect - cleanup camera control state.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    fun onPlayerQuit(event: PlayerQuitEvent) {
        controlledPlayers.remove(event.player.uniqueId)
    }

    /**
     * Cancel entity targeting for camera-controlled players.
     * Prevents mobs from targeting players during camera animations.
     * Based on Typewriter's EntityTargetEvent handling.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onEntityTarget(event: EntityTargetEvent) {
        val target = event.target ?: return
        if (target is Player && controlledPlayers.containsKey(target.uniqueId)) {
            event.isCancelled = true
        }
    }

    /**
     * Cancel living entity targeting for camera-controlled players.
     * More specific version for living entity targets.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onEntityTargetLiving(event: EntityTargetLivingEntityEvent) {
        val target = event.target ?: return
        if (target is Player && controlledPlayers.containsKey(target.uniqueId)) {
            event.isCancelled = true
        }
    }

    override fun onPacketSend(event: PacketSendEvent) {
        val uuid = event.user.uuid ?: return
        if (!controlledPlayers.containsKey(uuid)) return

        // Fake player's Y position (+500) to prevent self-interaction
        when (event.packetType) {
            PacketType.Play.Server.PLAYER_POSITION_AND_LOOK -> {
                val packet = WrapperPlayServerPlayerPositionAndLook(event)
                packet.y = packet.y + Y_OFFSET
            }
        }
    }

    override fun onPacketReceive(event: PacketReceiveEvent) {
        val uuid = event.user.uuid ?: return
        val playerEntityId = controlledPlayers[uuid] ?: return

        when (event.packetType) {
            // Correct incoming position packets (-500)
            PacketType.Play.Client.PLAYER_POSITION -> {
                val packet = WrapperPlayClientPlayerPosition(event)
                packet.position = packet.position.subtract(0.0, Y_OFFSET, 0.0)
            }
            PacketType.Play.Client.PLAYER_POSITION_AND_ROTATION -> {
                val packet = WrapperPlayClientPlayerPositionAndRotation(event)
                packet.position = packet.position.subtract(0.0, Y_OFFSET, 0.0)
            }
            // Cancel self-interaction
            PacketType.Play.Client.INTERACT_ENTITY -> {
                val packet = WrapperPlayClientInteractEntity(event)
                if (packet.entityId == playerEntityId) {
                    event.isCancelled = true
                }
            }
        }
    }
}

