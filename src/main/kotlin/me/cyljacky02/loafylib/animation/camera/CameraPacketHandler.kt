package me.cyljacky02.loafylib.animation.camera

import com.github.retrooper.packetevents.PacketEvents
import com.github.retrooper.packetevents.event.PacketListenerAbstract
import com.github.retrooper.packetevents.event.PacketListenerPriority
import com.github.retrooper.packetevents.event.PacketReceiveEvent
import com.github.retrooper.packetevents.event.PacketSendEvent
import com.github.retrooper.packetevents.protocol.packettype.PacketType
import com.github.retrooper.packetevents.protocol.item.ItemStack
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerPosition
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerPositionAndRotation
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerPositionAndLook
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetSlot
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerWindowItems
import me.cyljacky02.loafylib.animation.core.PlayerStateSnapshot
import net.kyori.adventure.util.TriState
import org.bukkit.Bukkit
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.EntityTargetEvent
import org.bukkit.event.entity.EntityTargetLivingEntityEvent
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCreativeEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.event.inventory.InventoryOpenEvent
import org.bukkit.event.player.PlayerAttemptPickupItemEvent
import org.bukkit.event.player.PlayerDropItemEvent
import org.bukkit.event.player.PlayerInteractAtEntityEvent
import org.bukkit.event.player.PlayerInteractEntityEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerItemConsumeEvent
import org.bukkit.event.player.PlayerKickEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.player.PlayerSwapHandItemsEvent
import org.bukkit.event.player.PlayerTeleportEvent
import org.bukkit.plugin.Plugin
import org.bukkit.persistence.PersistentDataType
import org.bukkit.potion.PotionEffectType
import io.papermc.paper.event.player.PlayerPickItemEvent
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
 * - Cancels damage and death events for camera-controlled players
 * - Cleans up on player disconnect with proper state restoration
 *
 * Thread-safe: Uses ConcurrentHashMap for controlled players.
 */
class CameraPacketHandler(private val plugin: Plugin) : PacketListenerAbstract(PacketListenerPriority.HIGH), Listener {

    companion object {
        /** Y-offset to prevent self-interaction (same as Typewriter) */
        const val Y_OFFSET = 500.0

        private val CAMERA_CONTROL_KEY = NamespacedKey("loafylib", "camera_control")
        private val CAMERA_CONTROL_WAS_INVULNERABLE_KEY = NamespacedKey("loafylib", "camera_control_was_invulnerable")
        private val CAMERA_CONTROL_ALLOW_FLIGHT_KEY = NamespacedKey("loafylib", "camera_control_allow_flight")
        private val CAMERA_CONTROL_IS_FLYING_KEY = NamespacedKey("loafylib", "camera_control_is_flying")
        private val CAMERA_CONTROL_FLYING_FALL_DAMAGE_KEY = NamespacedKey("loafylib", "camera_control_flying_fall_damage")
        private val CAMERA_CONTROL_HAD_INVISIBILITY_KEY = NamespacedKey("loafylib", "camera_control_had_invisibility")
        private val CAMERA_CONTROL_INVIS_DURATION_KEY = NamespacedKey("loafylib", "camera_control_invis_duration")
        private val CAMERA_CONTROL_INVIS_AMPLIFIER_KEY = NamespacedKey("loafylib", "camera_control_invis_amplifier")
        private val CAMERA_CONTROL_INVIS_AMBIENT_KEY = NamespacedKey("loafylib", "camera_control_invis_ambient")
        private val CAMERA_CONTROL_INVIS_PARTICLES_KEY = NamespacedKey("loafylib", "camera_control_invis_particles")
        private val CAMERA_CONTROL_INVIS_ICON_KEY = NamespacedKey("loafylib", "camera_control_invis_icon")
        private val CAMERA_CONTROL_FREEZE_TICKS_KEY = NamespacedKey("loafylib", "camera_control_freeze_ticks")
        private val CAMERA_CONTROL_FREEZE_LOCKED_KEY = NamespacedKey("loafylib", "camera_control_freeze_locked")

        private val emptySlotsCache = ConcurrentHashMap<Int, List<ItemStack>>()

        @Volatile
        private var fakeEmptyInventoryEnabled: Boolean = true

        fun setFakeEmptyInventoryEnabled(enabled: Boolean) {
            fakeEmptyInventoryEnabled = enabled
        }

        fun isFakeEmptyInventoryEnabled(): Boolean {
            return fakeEmptyInventoryEnabled
        }

        fun isPersistentlyCameraControlled(player: Player): Boolean {
            return player.persistentDataContainer.has(CAMERA_CONTROL_KEY, PersistentDataType.BYTE)
        }

        fun restorePersistentCameraControl(player: Player) {
            if (!player.isOnline) return
            val pdc = player.persistentDataContainer
            if (!pdc.has(CAMERA_CONTROL_KEY, PersistentDataType.BYTE)) return

            runCatching {
                if (pdc.has(CAMERA_CONTROL_WAS_INVULNERABLE_KEY, PersistentDataType.BYTE)) {
                    val wasInvulnerable = pdc.get(CAMERA_CONTROL_WAS_INVULNERABLE_KEY, PersistentDataType.BYTE)?.toInt() == 1
                    player.isInvulnerable = wasInvulnerable
                }
            }

            runCatching {
                if (
                    pdc.has(CAMERA_CONTROL_ALLOW_FLIGHT_KEY, PersistentDataType.BYTE) &&
                    pdc.has(CAMERA_CONTROL_IS_FLYING_KEY, PersistentDataType.BYTE) &&
                    pdc.has(CAMERA_CONTROL_FLYING_FALL_DAMAGE_KEY, PersistentDataType.BYTE)
                ) {
                    val allowFlight = pdc.get(CAMERA_CONTROL_ALLOW_FLIGHT_KEY, PersistentDataType.BYTE)?.toInt() == 1
                    val isFlying = pdc.get(CAMERA_CONTROL_IS_FLYING_KEY, PersistentDataType.BYTE)?.toInt() == 1
                    val fallDamage = when (pdc.get(CAMERA_CONTROL_FLYING_FALL_DAMAGE_KEY, PersistentDataType.BYTE)?.toInt()) {
                        1 -> TriState.TRUE
                        0 -> TriState.FALSE
                        2 -> TriState.NOT_SET
                        else -> TriState.NOT_SET
                    }
                    player.allowFlight = allowFlight
                    player.isFlying = isFlying
                    player.setFlyingFallDamage(fallDamage)
                }
            }

            runCatching {
                if (pdc.has(CAMERA_CONTROL_HAD_INVISIBILITY_KEY, PersistentDataType.BYTE)) {
                    val hadInvis = pdc.get(CAMERA_CONTROL_HAD_INVISIBILITY_KEY, PersistentDataType.BYTE)?.toInt() == 1
                    if (!hadInvis) {
                        player.removePotionEffect(PotionEffectType.INVISIBILITY)
                    } else {
                        val duration = pdc.get(CAMERA_CONTROL_INVIS_DURATION_KEY, PersistentDataType.INTEGER) ?: return@runCatching
                        val amplifier = pdc.get(CAMERA_CONTROL_INVIS_AMPLIFIER_KEY, PersistentDataType.INTEGER) ?: 0
                        val ambient = pdc.get(CAMERA_CONTROL_INVIS_AMBIENT_KEY, PersistentDataType.BYTE)?.toInt() == 1
                        val particles = pdc.get(CAMERA_CONTROL_INVIS_PARTICLES_KEY, PersistentDataType.BYTE)?.toInt() == 1
                        val icon = pdc.get(CAMERA_CONTROL_INVIS_ICON_KEY, PersistentDataType.BYTE)?.toInt() == 1
                        player.removePotionEffect(PotionEffectType.INVISIBILITY)
                        player.addPotionEffect(
                            org.bukkit.potion.PotionEffect(
                                PotionEffectType.INVISIBILITY,
                                duration,
                                amplifier,
                                ambient,
                                particles,
                                icon
                            )
                        )
                    }
                }
            }

            runCatching {
                if (
                    pdc.has(CAMERA_CONTROL_FREEZE_TICKS_KEY, PersistentDataType.INTEGER) &&
                    pdc.has(CAMERA_CONTROL_FREEZE_LOCKED_KEY, PersistentDataType.BYTE)
                ) {
                    val freezeTicks = pdc.get(CAMERA_CONTROL_FREEZE_TICKS_KEY, PersistentDataType.INTEGER) ?: 0
                    val freezeLocked = pdc.get(CAMERA_CONTROL_FREEZE_LOCKED_KEY, PersistentDataType.BYTE)?.toInt() == 1
                    player.lockFreezeTicks(freezeLocked)
                    player.freezeTicks = freezeTicks
                }
            }

            clearPersistentCameraControlMarker(player)
            runCatching { player.updateInventory() }
        }

        fun clearPersistentCameraControl(player: Player) {
            runCatching {
                clearPersistentCameraControlMarker(player)
                player.isInvulnerable = false
                player.removePotionEffect(PotionEffectType.INVISIBILITY)
                player.lockFreezeTicks(false)
                player.freezeTicks = 0
                player.updateInventory()
            }
        }

        private fun setPersistentCameraControlMarker(player: Player, wasInvulnerable: Boolean, snapshot: PlayerStateSnapshot) {
            runCatching {
                val pdc = player.persistentDataContainer
                pdc.set(CAMERA_CONTROL_KEY, PersistentDataType.BYTE, 1.toByte())
                pdc.set(CAMERA_CONTROL_WAS_INVULNERABLE_KEY, PersistentDataType.BYTE, if (wasInvulnerable) 1.toByte() else 0.toByte())
                pdc.set(CAMERA_CONTROL_ALLOW_FLIGHT_KEY, PersistentDataType.BYTE, if (snapshot.allowFlight) 1.toByte() else 0.toByte())
                pdc.set(CAMERA_CONTROL_IS_FLYING_KEY, PersistentDataType.BYTE, if (snapshot.isFlying) 1.toByte() else 0.toByte())
                pdc.set(
                    CAMERA_CONTROL_FLYING_FALL_DAMAGE_KEY,
                    PersistentDataType.BYTE,
                    when (snapshot.flyingFallDamage) {
                        TriState.TRUE -> 1.toByte()
                        TriState.FALSE -> 0.toByte()
                        TriState.NOT_SET -> 2.toByte()
                    }
                )

                pdc.set(CAMERA_CONTROL_HAD_INVISIBILITY_KEY, PersistentDataType.BYTE, if (snapshot.hadInvisibility) 1.toByte() else 0.toByte())
                pdc.set(CAMERA_CONTROL_FREEZE_TICKS_KEY, PersistentDataType.INTEGER, player.freezeTicks)
                pdc.set(CAMERA_CONTROL_FREEZE_LOCKED_KEY, PersistentDataType.BYTE, if (player.isFreezeTickingLocked) 1.toByte() else 0.toByte())
                snapshot.invisibilityEffect?.let { effect ->
                    pdc.set(CAMERA_CONTROL_INVIS_DURATION_KEY, PersistentDataType.INTEGER, effect.duration)
                    pdc.set(CAMERA_CONTROL_INVIS_AMPLIFIER_KEY, PersistentDataType.INTEGER, effect.amplifier)
                    pdc.set(CAMERA_CONTROL_INVIS_AMBIENT_KEY, PersistentDataType.BYTE, if (effect.isAmbient) 1.toByte() else 0.toByte())
                    pdc.set(CAMERA_CONTROL_INVIS_PARTICLES_KEY, PersistentDataType.BYTE, if (effect.hasParticles()) 1.toByte() else 0.toByte())
                    pdc.set(CAMERA_CONTROL_INVIS_ICON_KEY, PersistentDataType.BYTE, if (effect.hasIcon()) 1.toByte() else 0.toByte())
                }
            }
        }

        private fun clearPersistentCameraControlMarker(player: Player) {
            runCatching {
                val pdc = player.persistentDataContainer
                pdc.remove(CAMERA_CONTROL_KEY)
                pdc.remove(CAMERA_CONTROL_WAS_INVULNERABLE_KEY)
                pdc.remove(CAMERA_CONTROL_ALLOW_FLIGHT_KEY)
                pdc.remove(CAMERA_CONTROL_IS_FLYING_KEY)
                pdc.remove(CAMERA_CONTROL_FLYING_FALL_DAMAGE_KEY)
                pdc.remove(CAMERA_CONTROL_HAD_INVISIBILITY_KEY)
                pdc.remove(CAMERA_CONTROL_INVIS_DURATION_KEY)
                pdc.remove(CAMERA_CONTROL_INVIS_AMPLIFIER_KEY)
                pdc.remove(CAMERA_CONTROL_INVIS_AMBIENT_KEY)
                pdc.remove(CAMERA_CONTROL_INVIS_PARTICLES_KEY)
                pdc.remove(CAMERA_CONTROL_INVIS_ICON_KEY)
                pdc.remove(CAMERA_CONTROL_FREEZE_TICKS_KEY)
                pdc.remove(CAMERA_CONTROL_FREEZE_LOCKED_KEY)
            }
        }

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
                    it.restoreAllOnlinePlayers()
                    PacketEvents.getAPI().eventManager.unregisterListener(it)
                    HandlerList.unregisterAll(it)
                }
                instance = null
                registered = false
            }
        }
    }

    /**
     * State for a camera-controlled player.
     * Stores all information needed to restore player state on quit/death.
     */
    data class CameraControlState(
        val entityId: Int,
        val stateSnapshot: PlayerStateSnapshot,
        val plugin: Plugin,
        val wasInvulnerable: Boolean,
        val makeInvisible: Boolean,
        val hideFromOthers: Boolean,
        val freezeTicks: Int,
        val freezeLocked: Boolean
    )

    /** Players currently under camera control (UUID -> their control state) */
    private val controlledPlayers = ConcurrentHashMap<UUID, CameraControlState>()

    private fun restoreAllOnlinePlayers() {
        val uuids = controlledPlayers.keys.toList()
        for (uuid in uuids) {
            val player = Bukkit.getPlayer(uuid) ?: continue
            val state = controlledPlayers.remove(uuid) ?: continue
            if (!player.isOnline) continue
            restorePlayerState(player, state)
        }
    }

    /**
     * Start camera control for a player.
     * Enables Y-offset faking and self-interaction prevention.
     *
     * @param player The player to control
     * @param stateSnapshot The captured player state for restoration
     * @param plugin The plugin instance
     * @param wasInvulnerable Whether the player was invulnerable before
     * @param makeInvisible Whether invisibility was applied
     * @param hideFromOthers Whether the player was hidden from others
     */
    fun startCameraControl(
        player: Player,
        stateSnapshot: PlayerStateSnapshot,
        plugin: Plugin,
        wasInvulnerable: Boolean,
        makeInvisible: Boolean,
        hideFromOthers: Boolean
    ) {
        val freezeTicks = player.freezeTicks
        val freezeLocked = player.isFreezeTickingLocked
        controlledPlayers[player.uniqueId] = CameraControlState(
            entityId = player.entityId,
            stateSnapshot = stateSnapshot,
            plugin = plugin,
            wasInvulnerable = wasInvulnerable,
            makeInvisible = makeInvisible,
            hideFromOthers = hideFromOthers,
            freezeTicks = freezeTicks,
            freezeLocked = freezeLocked
        )

        setPersistentCameraControlMarker(player, wasInvulnerable, stateSnapshot)

        player.closeInventory()

        if (fakeEmptyInventoryEnabled) {
            runCatching { player.updateInventory() }
        }

        player.lockFreezeTicks(true)
        player.freezeTicks = player.maxFreezeTicks
    }

    /**
     * Stop camera control for a player.
     * Restores player state if the player is still online.
     *
     * @param player The player to stop controlling
     * @param skipRestore If true, skip state restoration (caller handles it)
     */
    fun stopCameraControl(player: Player, skipRestore: Boolean = false) {
        val state = controlledPlayers.remove(player.uniqueId) ?: return

        if (!skipRestore && player.isOnline) {
            restorePlayerState(player, state)
        }

        clearPersistentCameraControlMarker(player)
    }

    /**
     * Check if a player is under camera control.
     */
    fun isUnderCameraControl(player: Player): Boolean {
        return controlledPlayers.containsKey(player.uniqueId)
    }

    /**
     * Restore player state from camera control state.
     * Called on quit, death, or normal teardown.
     */
    private fun restorePlayerState(player: Player, state: CameraControlState) {
        runCatching {
            // Restore invulnerability
            if (!state.wasInvulnerable) {
                player.isInvulnerable = false
            }
        }

        runCatching {
            player.lockFreezeTicks(state.freezeLocked)
            player.freezeTicks = state.freezeTicks
        }

        runCatching {
            // Restore visibility and invisibility state from snapshot
            state.stateSnapshot.restore(
                player = player,
                plugin = state.plugin,
                restoreLocation = false,
                restoreVelocity = false,
                restoreVisibility = state.hideFromOthers
            )
        }

        runCatching {
            player.updateInventory()
        }

        clearPersistentCameraControlMarker(player)
    }

    /**
     * Handle player disconnect - cleanup camera control state and restore player state.
     * Uses LOW priority to run BEFORE other handlers, ensuring state is restored
     * while the player is still online.
     *
     * Based on Typewriter's approach for proper cleanup on disconnect.
     */
    @EventHandler(priority = EventPriority.LOW)
    fun onPlayerQuit(event: PlayerQuitEvent) {
        val player = event.player
        val state = controlledPlayers.remove(player.uniqueId) ?: return

        // Restore state while player is still online
        // This ensures invisibility effect is removed before disconnect
        restorePlayerState(player, state)
    }

    @EventHandler(priority = EventPriority.LOW)
    fun onPlayerKick(event: PlayerKickEvent) {
        val player = event.player
        val state = controlledPlayers.remove(player.uniqueId) ?: return
        restorePlayerState(player, state)
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onInventoryOpen(event: InventoryOpenEvent) {
        val player = event.player as? Player ?: return
        if (!controlledPlayers.containsKey(player.uniqueId)) return
        event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onInventoryClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        if (!controlledPlayers.containsKey(player.uniqueId)) return
        event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onInventoryCreative(event: InventoryCreativeEvent) {
        val player = event.whoClicked as? Player ?: return
        if (!controlledPlayers.containsKey(player.uniqueId)) return
        event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onInventoryDrag(event: InventoryDragEvent) {
        val player = event.whoClicked as? Player ?: return
        if (!controlledPlayers.containsKey(player.uniqueId)) return
        event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onPlayerInteract(event: PlayerInteractEvent) {
        val player = event.player
        if (!controlledPlayers.containsKey(player.uniqueId)) return
        event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onBlockBreak(event: BlockBreakEvent) {
        val player = event.player
        if (!controlledPlayers.containsKey(player.uniqueId)) return
        event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onBlockPlace(event: BlockPlaceEvent) {
        val player = event.player
        if (!controlledPlayers.containsKey(player.uniqueId)) return
        event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onPlayerInteractEntity(event: PlayerInteractEntityEvent) {
        val player = event.player
        if (!controlledPlayers.containsKey(player.uniqueId)) return
        event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onPlayerInteractAtEntity(event: PlayerInteractAtEntityEvent) {
        val player = event.player
        if (!controlledPlayers.containsKey(player.uniqueId)) return
        event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onPlayerDropItem(event: PlayerDropItemEvent) {
        val player = event.player
        if (!controlledPlayers.containsKey(player.uniqueId)) return
        event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onPlayerSwapHandItems(event: PlayerSwapHandItemsEvent) {
        val player = event.player
        if (!controlledPlayers.containsKey(player.uniqueId)) return
        event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onPlayerConsume(event: PlayerItemConsumeEvent) {
        val player = event.player
        if (!controlledPlayers.containsKey(player.uniqueId)) return
        event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onPlayerPickItem(event: PlayerPickItemEvent) {
        val player = event.player
        if (!controlledPlayers.containsKey(player.uniqueId)) return
        event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onPlayerAttemptPickupItem(event: PlayerAttemptPickupItemEvent) {
        val player = event.player
        if (!controlledPlayers.containsKey(player.uniqueId)) return
        event.isCancelled = true
    }

    /**
     * Cancel damage for camera-controlled players.
     * Prevents any damage during camera animations.
     * Based on Typewriter's EntityDamageEvent handling.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onEntityDamage(event: EntityDamageEvent) {
        val entity = event.entity
        if (entity is Player && controlledPlayers.containsKey(entity.uniqueId)) {
            event.isCancelled = true
        }
    }

    /**
     * Cancel death for camera-controlled players.
     * This is a safety net in case damage somehow gets through.
     * Based on Typewriter's PlayerDeathEvent handling at HIGHEST priority.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onPlayerDeath(event: PlayerDeathEvent) {
        val player = event.entity
        if (controlledPlayers.containsKey(player.uniqueId)) {
            // Cancel death - this works because we also cancel damage
            event.isCancelled = true
        }
    }

    /**
     * Cancel entity targeting for camera-controlled players.
     * Prevents mobs from targeting players during camera animations.
     * Based on Typewriter's EntityTargetEvent handling.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
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
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onEntityTargetLiving(event: EntityTargetLivingEntityEvent) {
        val target = event.target ?: return
        if (target is Player && controlledPlayers.containsKey(target.uniqueId)) {
            event.isCancelled = true
        }
    }

    /**
     * Handle external teleportation during camera control.
     *
     * Following Typewriter's philosophy: Don't fight external teleports, just clean up gracefully.
     * If a player is teleported by an external source (command, plugin, ender pearl, etc.),
     * we remove them from camera control tracking and restore their state immediately.
     * The CameraAction.tick() will detect the player is no longer under camera control
     * and throw AnimationCancelledException for proper cleanup.
     *
     * Note: We use MONITOR priority to observe the teleport without interfering with
     * cancellation logic. State restoration is safe here since it only modifies the
     * player's own properties (invulnerability, visibility), not the teleport itself.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onPlayerTeleport(event: PlayerTeleportEvent) {
        val player = event.player
        if (!controlledPlayers.containsKey(player.uniqueId)) return

        // Check if this is an external teleport that should end camera control
        // Internal causes (DISMOUNT, EXIT_BED) are normal game mechanics, allow them
        // UNKNOWN could be our own teleport or other internal mechanics
        val isExternalTeleport = when (event.cause) {
            PlayerTeleportEvent.TeleportCause.COMMAND,
            PlayerTeleportEvent.TeleportCause.PLUGIN,
            PlayerTeleportEvent.TeleportCause.ENDER_PEARL,
            PlayerTeleportEvent.TeleportCause.NETHER_PORTAL,
            PlayerTeleportEvent.TeleportCause.END_PORTAL,
            PlayerTeleportEvent.TeleportCause.END_GATEWAY,
            PlayerTeleportEvent.TeleportCause.SPECTATE -> true
            else -> false
        }

        if (isExternalTeleport) {
            // Remove from tracking and restore state immediately
            // CameraAction.tick() will detect this and throw AnimationCancelledException
            // but we restore state here to ensure cleanup even if teardown is delayed
            val state = controlledPlayers.remove(player.uniqueId)
            if (state != null) {
                restorePlayerState(player, state)
            }
        }
    }

    override fun onPacketSend(event: PacketSendEvent) {
        val uuid = event.user.uuid ?: return
        if (!controlledPlayers.containsKey(uuid)) return

        when (event.packetType) {
            PacketType.Play.Server.PLAYER_POSITION_AND_LOOK -> {
                val packet = WrapperPlayServerPlayerPositionAndLook(event)
                packet.y = packet.y + Y_OFFSET
            }
            PacketType.Play.Server.WINDOW_ITEMS -> {
                if (!fakeEmptyInventoryEnabled) return
                val packet = WrapperPlayServerWindowItems(event)
                val items = packet.items
                val emptyItems = emptySlotsCache.computeIfAbsent(items.size) { size: Int ->
                    List(size) { ItemStack.EMPTY }
                }
                packet.items = emptyItems
                runCatching { packet.setCarriedItem(ItemStack.EMPTY) }
            }
            PacketType.Play.Server.SET_SLOT -> {
                if (!fakeEmptyInventoryEnabled) return
                val packet = WrapperPlayServerSetSlot(event)
                packet.item = ItemStack.EMPTY
            }
        }
    }

    override fun onPacketReceive(event: PacketReceiveEvent) {
        val uuid = event.user.uuid ?: return
        if (!controlledPlayers.containsKey(uuid)) return

        when (event.packetType) {
            PacketType.Play.Client.PLAYER_INPUT -> {
                event.isCancelled = true
            }
            PacketType.Play.Client.CLICK_WINDOW,
            PacketType.Play.Client.CLICK_WINDOW_BUTTON -> {
                if (fakeEmptyInventoryEnabled) {
                    event.isCancelled = true
                }
            }
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
                event.isCancelled = true
            }
        }
    }
}

