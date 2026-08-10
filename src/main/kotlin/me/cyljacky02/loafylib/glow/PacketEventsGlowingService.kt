package me.cyljacky02.loafylib.glow

import com.github.retrooper.packetevents.PacketEvents
import com.github.retrooper.packetevents.event.PacketListenerAbstract
import com.github.retrooper.packetevents.event.PacketListenerPriority
import com.github.retrooper.packetevents.event.PacketSendEvent
import com.github.retrooper.packetevents.protocol.entity.data.EntityData
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes
import com.github.retrooper.packetevents.protocol.packettype.PacketType
import com.github.retrooper.packetevents.util.Vector3d
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerTeams
import io.github.retrooper.packetevents.util.SpigotConversionUtil
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Bukkit
import org.bukkit.Color
import org.bukkit.Location
import org.bukkit.block.data.BlockData
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.Plugin
import org.bukkit.util.Transformation
import java.util.Optional
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * PacketEvents-based implementation of [GlowingService].
 *
 * This implementation uses PacketEvents to send entity metadata and team packets
 * for per-player glowing effects. It supports:
 * - Existing entity glowing with 16 team colors
 * - Display entity glowing with unlimited RGB colors
 *
 * ## Thread Safety
 * All methods are thread-safe using ConcurrentHashMap.
 * Entity IDs are generated using Paper's thread-safe nextEntityId() API.
 * Packets can be sent from any thread (main, async, or Netty threads).
 *
 * @property plugin the plugin instance for logging
 */
internal class PacketEventsGlowingService(
    private val plugin: Plugin
) : GlowingService, Listener {

    /**
     * Per-player state container. Uses ConcurrentHashMap for thread-safe access
     * from multiple threads (main thread, async tasks, Netty threads).
     */
    private val playerData = ConcurrentHashMap<UUID, PlayerGlowData>()

    /**
     * Team name prefix for glow teams. Each color gets its own team.
     */
    private val teamPrefix = "llg_"

    /**
     * PacketEvents listener for intercepting entity metadata packets.
     * Registered during [initialize] and unregistered during [shutdown].
     */
    private var packetListener: PacketListenerAbstract? = null

    private companion object {
        // Display entity metadata indices (1.19.4+)
        // See: https://minecraft.wiki/w/Chunk_format/display_entity
        
        /** Display entity: Interpolation duration (VarInt) - Index 8 */
        const val DISPLAY_INTERPOLATION_DURATION_INDEX = 8
        /** Display entity: Interpolation start delay (VarInt) - Index 9 */
        const val DISPLAY_INTERPOLATION_START_INDEX = 9
        /** Display entity: Translation (Vector3f) - Index 11 */
        const val DISPLAY_TRANSLATION_INDEX = 11
        /** Display entity: Scale (Vector3f) - Index 12 */
        const val DISPLAY_SCALE_INDEX = 12
        /** Display entity: Left rotation (Quaternionf) - Index 13 */
        const val DISPLAY_LEFT_ROTATION_INDEX = 13
        /** Display entity: Right rotation (Quaternionf) - Index 14 */
        const val DISPLAY_RIGHT_ROTATION_INDEX = 14
        /** Display entity: Glow color override (Int, ARGB) - Index 22 */
        const val DISPLAY_GLOW_COLOR_OVERRIDE_INDEX = 22
        
        /** Block display: Block state (VarInt block state ID) - Index 23 */
        const val BLOCK_DISPLAY_BLOCK_STATE_INDEX = 23
        /** Item display: Item stack - Index 23 */
        const val ITEM_DISPLAY_ITEM_INDEX = 23
        /** Text display: Text (Component) - Index 23 */
        const val TEXT_DISPLAY_TEXT_INDEX = 23
        /** Text display: Background color (Int, ARGB) - Index 25 */
        const val TEXT_DISPLAY_BACKGROUND_INDEX = 25
    }

    // ==================== Availability ====================

    /**
     * Returns true since this implementation is only created when PacketEvents is available.
     * 
     * The [GlowingServiceFactory] ensures this class is only instantiated when PacketEvents
     * is detected at runtime. Therefore, this method always returns true.
     */
    override fun isAvailable(): Boolean = true

    // ==================== Existing Entity Glowing ====================

    override fun setGlowing(entityId: Int, entityUuid: UUID, receiver: Player, color: GlowColor?) {
        if (!receiver.isOnline) return

        val effectiveColor = color?.namedTextColor ?: NamedTextColor.WHITE
        val receiverUuid = receiver.uniqueId

        // Get or create player data
        val data = playerData.computeIfAbsent(receiverUuid) { PlayerGlowData() }

        // Track the glow state
        val previous = data.glowingEntities.put(entityId, GlowState(entityId, entityUuid, effectiveColor))

        if (previous != null && previous.color != effectiveColor) {
            removeVirtualEntityFromTeam(receiver, previous.entityUuid, previous.color)
        }

        // Ensure team exists for this color
        ensureTeamExists(receiver, effectiveColor)

        // Add entity to team for color
        addVirtualEntityToTeam(receiver, entityUuid, effectiveColor)

        // Send glowing metadata packet
        sendGlowingMetadata(receiver, entityId, true)
    }

    override fun unsetGlowing(entityId: Int, receiver: Player) {
        if (!receiver.isOnline) return
        val receiverUuid = receiver.uniqueId

        // Get player data
        val data = playerData[receiverUuid] ?: return

        // Get and remove the glow state
        val glowState = data.glowingEntities.remove(entityId) ?: return

        // Remove entity from team
        removeVirtualEntityFromTeam(receiver, glowState.entityUuid, glowState.color)

        // Send non-glowing metadata packet
        sendGlowingMetadata(receiver, entityId, false)

        // Clear cached server flags for this entity to prevent unbounded growth
        data.entityFlags.remove(entityId)
    }

    override fun isGlowing(entityId: Int, receiver: Player): Boolean {
        val data = playerData[receiver.uniqueId] ?: return false
        return data.glowingEntities.containsKey(entityId)
    }

    // ==================== Display Entity Glowing (Stubs for later tasks) ====================

    override fun spawnGlowingBlock(
        location: Location,
        blockData: BlockData,
        receiver: Player,
        color: Color
    ): Int {
        if (!receiver.isOnline) return 0

        // Generate unique entity ID using Paper's nextEntityId() API.
        // Paper 26.x scopes the counter per world, so the receiver's world is
        // the correct one: these are virtual entities shown only to them.
        val entityId = Bukkit.getUnsafe().nextEntityId(receiver.world)
        val entityUuid = UUID.randomUUID()
        val receiverUuid = receiver.uniqueId

        // Get or create player data
        val data = playerData.computeIfAbsent(receiverUuid) { PlayerGlowData() }

        // Track the display entity ID
        data.activeDisplays.add(entityId)

        // Send spawn entity packet
        sendSpawnDisplayEntity(receiver, entityId, entityUuid, EntityTypes.BLOCK_DISPLAY, location)

        // Send metadata packet with block state, glowing flag, and glow color
        sendBlockDisplayMetadata(receiver, entityId, blockData, color)

        return entityId
    }

    override fun spawnGlowingItem(
        location: Location,
        itemStack: ItemStack,
        receiver: Player,
        color: Color
    ): Int {
        if (!receiver.isOnline) return 0

        // Generate unique entity ID using Paper's nextEntityId() API.
        // Paper 26.x scopes the counter per world, so the receiver's world is
        // the correct one: these are virtual entities shown only to them.
        val entityId = Bukkit.getUnsafe().nextEntityId(receiver.world)
        val entityUuid = UUID.randomUUID()
        val receiverUuid = receiver.uniqueId

        // Get or create player data
        val data = playerData.computeIfAbsent(receiverUuid) { PlayerGlowData() }

        // Track the display entity ID
        data.activeDisplays.add(entityId)

        // Send spawn entity packet
        sendSpawnDisplayEntity(receiver, entityId, entityUuid, EntityTypes.ITEM_DISPLAY, location)

        // Send metadata packet with item, glowing flag, and glow color
        sendItemDisplayMetadata(receiver, entityId, itemStack, color)

        return entityId
    }

    override fun spawnGlowingText(
        location: Location,
        text: Component,
        receiver: Player,
        color: Color
    ): Int {
        if (!receiver.isOnline) return 0

        // Generate unique entity ID using Paper's nextEntityId() API.
        // Paper 26.x scopes the counter per world, so the receiver's world is
        // the correct one: these are virtual entities shown only to them.
        val entityId = Bukkit.getUnsafe().nextEntityId(receiver.world)
        val entityUuid = UUID.randomUUID()
        val receiverUuid = receiver.uniqueId

        // Get or create player data
        val data = playerData.computeIfAbsent(receiverUuid) { PlayerGlowData() }

        // Track the display entity ID
        data.activeDisplays.add(entityId)

        // Send spawn entity packet
        sendSpawnDisplayEntity(receiver, entityId, entityUuid, EntityTypes.TEXT_DISPLAY, location)

        // Send metadata packet with text, glowing flag, and glow color
        sendTextDisplayMetadata(receiver, entityId, text, color)

        return entityId
    }

    override fun removeDisplay(entityId: Int, receiver: Player) {
        if (!receiver.isOnline) return

        val receiverUuid = receiver.uniqueId

        // Get player data
        val data = playerData[receiverUuid] ?: return

        // Remove from tracking (idempotent - returns false if not found)
        if (!data.activeDisplays.remove(entityId)) return

        // Send destroy entities packet
        val packet = WrapperPlayServerDestroyEntities(entityId)
        PacketEvents.getAPI().playerManager.sendPacket(receiver, packet)
    }

    override fun updateDisplayColor(entityId: Int, receiver: Player, color: Color) {
        if (!receiver.isOnline) return

        val receiverUuid = receiver.uniqueId

        // Get player data
        val data = playerData[receiverUuid] ?: return

        // Check if display exists
        if (entityId !in data.activeDisplays) return

        // Send metadata update with new glow color
        val metadata = listOf<EntityData<*>>(
            EntityData(DISPLAY_GLOW_COLOR_OVERRIDE_INDEX, EntityDataTypes.INT, colorToArgb(color))
        )

        val packet = WrapperPlayServerEntityMetadata(entityId, metadata)
        PacketEvents.getAPI().playerManager.sendPacket(receiver, packet)
    }

    override fun updateDisplayTransform(entityId: Int, receiver: Player, transform: Transformation) {
        if (!receiver.isOnline) return

        val receiverUuid = receiver.uniqueId

        // Get player data
        val data = playerData[receiverUuid] ?: return

        // Check if display exists
        if (entityId !in data.activeDisplays) return

        // Send metadata update with transformation components
        val metadata = mutableListOf<EntityData<*>>()

        // Translation (Vector3f)
        val translation = transform.translation
        metadata.add(EntityData(
            DISPLAY_TRANSLATION_INDEX,
            EntityDataTypes.VECTOR3F,
            com.github.retrooper.packetevents.util.Vector3f(translation.x, translation.y, translation.z)
        ))

        // Scale (Vector3f)
        val scale = transform.scale
        metadata.add(EntityData(
            DISPLAY_SCALE_INDEX,
            EntityDataTypes.VECTOR3F,
            com.github.retrooper.packetevents.util.Vector3f(scale.x, scale.y, scale.z)
        ))

        // Left rotation (Quaternionf)
        val leftRotation = transform.leftRotation
        metadata.add(EntityData(
            DISPLAY_LEFT_ROTATION_INDEX,
            EntityDataTypes.QUATERNION,
            com.github.retrooper.packetevents.util.Quaternion4f(
                leftRotation.x, leftRotation.y, leftRotation.z, leftRotation.w
            )
        ))

        // Right rotation (Quaternionf)
        val rightRotation = transform.rightRotation
        metadata.add(EntityData(
            DISPLAY_RIGHT_ROTATION_INDEX,
            EntityDataTypes.QUATERNION,
            com.github.retrooper.packetevents.util.Quaternion4f(
                rightRotation.x, rightRotation.y, rightRotation.z, rightRotation.w
            )
        ))

        val packet = WrapperPlayServerEntityMetadata(entityId, metadata)
        PacketEvents.getAPI().playerManager.sendPacket(receiver, packet)
    }

    override fun getActiveDisplays(receiver: Player): Set<Int> {
        val data = playerData[receiver.uniqueId] ?: return emptySet()
        return data.activeDisplays.toSet()
    }

    // ==================== Shulker Marker Glowing ====================

    override fun spawnGlowingMarker(
        location: Location,
        receiver: Player,
        color: GlowColor
    ): Int {
        if (!receiver.isOnline) return 0

        // Generate unique entity ID using Paper's nextEntityId() API.
        // Paper 26.x scopes the counter per world, so the receiver's world is
        // the correct one: these are virtual entities shown only to them.
        val entityId = Bukkit.getUnsafe().nextEntityId(receiver.world)
        val entityUuid = UUID.randomUUID()
        val receiverUuid = receiver.uniqueId

        // Block-align the location (corner of block)
        val alignedLocation = Location(
            location.world,
            location.blockX.toDouble(),
            location.blockY.toDouble(),
            location.blockZ.toDouble()
        )

        // Get or create player data
        val data = playerData.computeIfAbsent(receiverUuid) { PlayerGlowData() }

        // Track the marker state
        val effectiveColor = color.namedTextColor
        data.activeMarkers[entityId] = MarkerState(
            entityUuid = entityUuid,
            color = effectiveColor
        )

        // Ensure team exists for this color
        ensureTeamExists(receiver, effectiveColor)

        // Send spawn entity packet for shulker
        sendSpawnShulkerEntity(receiver, entityId, entityUuid, alignedLocation)

        // Send metadata packet with invisible + glowing flags
        sendShulkerMetadata(receiver, entityId)

        // Add entity to team for color (using UUID string like GlowingEntities does)
        addVirtualEntityToTeam(receiver, entityUuid, effectiveColor)

        return entityId
    }

    override fun removeMarker(entityId: Int, receiver: Player) {
        if (!receiver.isOnline) return

        val receiverUuid = receiver.uniqueId

        // Get player data
        val data = playerData[receiverUuid] ?: return

        // Remove from tracking (idempotent - returns null if not found)
        val markerState = data.activeMarkers.remove(entityId) ?: return

        // Remove entity from team using stored UUID
        removeVirtualEntityFromTeam(receiver, markerState.entityUuid, markerState.color)

        // Send destroy entities packet
        val packet = WrapperPlayServerDestroyEntities(entityId)
        PacketEvents.getAPI().playerManager.sendPacket(receiver, packet)
    }

    override fun updateMarkerColor(entityId: Int, receiver: Player, color: GlowColor) {
        if (!receiver.isOnline) return

        val receiverUuid = receiver.uniqueId

        // Get player data
        val data = playerData[receiverUuid] ?: return

        // Get the marker state
        val markerState = data.activeMarkers[entityId] ?: return

        val oldColor = markerState.color
        val newColor = color.namedTextColor

        if (oldColor == newColor) return // No change

        // Update the stored color
        data.activeMarkers[entityId] = markerState.copy(color = newColor)

        // Remove from old team
        removeVirtualEntityFromTeam(receiver, markerState.entityUuid, oldColor)

        // Ensure new team exists and add to it
        ensureTeamExists(receiver, newColor)
        addVirtualEntityToTeam(receiver, markerState.entityUuid, newColor)
    }

    override fun getActiveMarkers(receiver: Player): Set<Int> {
        val data = playerData[receiver.uniqueId] ?: return emptySet()
        return data.activeMarkers.keys.toSet()
    }

    // ==================== Lifecycle ====================

    override suspend fun initialize() {
        // Note: Bukkit listener registration is handled automatically by LoafyPlugin
        // for any component implementing Listener
        
        // Register PacketEvents listener for metadata interception
        packetListener = createPacketListener()
        PacketEvents.getAPI().eventManager.registerListener(packetListener)
        
        plugin.logger.info("GlowingService initialized with PacketEvents support")
    }

    /**
     * Shuts down the glowing service and cleans up all client-side state.
     *
     * For each online player:
     * - Sends destroy packets for all virtual displays
     * - Removes markers from teams and sends destroy packets
     * - Removes glowing entities from teams and restores original metadata flags
     *
     * This prevents "ghost" entities remaining visible on clients after plugin disable.
     */
    override suspend fun shutdown() {
        // Unregister PacketEvents listener
        packetListener?.let { listener ->
            PacketEvents.getAPI().eventManager.unregisterListener(listener)
        }
        packetListener = null

        val snapshot = playerData.entries.toList()
        for ((playerUuid, data) in snapshot) {
            val receiver = Bukkit.getPlayer(playerUuid) ?: continue
            if (!receiver.isOnline) continue

            for (entityId in data.activeDisplays) {
                val packet = WrapperPlayServerDestroyEntities(entityId)
                PacketEvents.getAPI().playerManager.sendPacket(receiver, packet)
            }

            for ((entityId, markerState) in data.activeMarkers) {
                removeVirtualEntityFromTeam(receiver, markerState.entityUuid, markerState.color)
                val packet = WrapperPlayServerDestroyEntities(entityId)
                PacketEvents.getAPI().playerManager.sendPacket(receiver, packet)
            }

            for ((entityId, glowState) in data.glowingEntities) {
                removeVirtualEntityFromTeam(receiver, glowState.entityUuid, glowState.color)
                val flags = data.entityFlags[entityId] ?: 0
                val metadata = listOf(EntityData(ENTITY_FLAGS_INDEX, EntityDataTypes.BYTE, flags))
                val packet = WrapperPlayServerEntityMetadata(entityId, metadata)
                PacketEvents.getAPI().playerManager.sendPacket(receiver, packet)
            }
        }

        playerData.clear()
    }

    // ==================== Event Handlers ====================

    /**
     * Handles player disconnect to clean up all glowing state for that player.
     * This ensures no memory leaks when players leave the server.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    fun onPlayerQuit(event: PlayerQuitEvent) {
        val playerUuid = event.player.uniqueId
        
        // Remove all state for this player (glowing entities, displays, markers, team colors)
        playerData.remove(playerUuid)
    }

    /**
     * Creates the PacketEvents listener for intercepting outgoing packets.
     *
     * Handles two packet types:
     * - **DESTROY_ENTITIES**: Evicts tracking state for destroyed entities to prevent memory growth.
     * - **ENTITY_METADATA**: Merges the glowing flag with existing entity flags to preserve
     *   the glowing state when other metadata updates occur, and caches server-side flags.
     */
    private fun createPacketListener(): PacketListenerAbstract {
        return object : PacketListenerAbstract(PacketListenerPriority.NORMAL) {
            override fun onPacketSend(event: PacketSendEvent) {
                val player = event.getPlayer<Player>() ?: return

                if (event.packetType == PacketType.Play.Server.DESTROY_ENTITIES) {
                    val data = playerData[player.uniqueId] ?: return

                    val packet = WrapperPlayServerDestroyEntities(event)
                    for (entityId in packet.entityIds) {
                        data.glowingEntities.remove(entityId)
                        data.entityFlags.remove(entityId)
                    }
                    return
                }

                if (event.packetType != PacketType.Play.Server.ENTITY_METADATA) return

                // Cache flags for tracked entities before any glow is requested so static
                // entities can be highlighted immediately later.
                val data = playerData.computeIfAbsent(player.uniqueId) { PlayerGlowData() }
                val packet = WrapperPlayServerEntityMetadata(event)
                val entityId = packet.entityId
                val mutation = captureAndMaybeApplyGlowing(
                    packet.entityMetadata,
                    glowing = data.glowingEntities.containsKey(entityId)
                )

                mutation.observedFlags?.let { observedFlags ->
                    data.entityFlags[entityId] = observedFlags
                }

                if (!mutation.modified) return

                packet.entityMetadata = mutation.entityMetadata
                event.markForReEncode(true)
            }
        }
    }

    // ==================== Internal Packet Methods ====================

    /**
     * Sends entity metadata packet to set or clear the glowing flag.
     *
     * If the server-side flags for this entity are unknown (not yet observed via packet
     * interception), this method returns early to avoid corrupting other entity flags.
     * The glow will be applied on the next ENTITY_METADATA packet from the server.
     *
     * @param receiver the player to send the packet to
     * @param entityId the entity ID to modify
     * @param glowing true to enable glowing, false to disable
     */
    private fun sendGlowingMetadata(receiver: Player, entityId: Int, glowing: Boolean) {
        val data = playerData[receiver.uniqueId] ?: return

        val serverFlags = data.entityFlags[entityId] ?: return
        val flags: Byte = if (glowing) withGlowingFlag(serverFlags) else serverFlags

        val metadata = listOf(
            EntityData(ENTITY_FLAGS_INDEX, EntityDataTypes.BYTE, flags)
        )

        val packet = WrapperPlayServerEntityMetadata(entityId, metadata)
        PacketEvents.getAPI().playerManager.sendPacket(receiver, packet)
    }

    /**
     * Ensures a team exists for the given color, creating it if necessary.
     * Teams are cached per-player to avoid sending duplicate creation packets.
     *
     * @param receiver the player to send the team to
     * @param color the team color
     */
    private fun ensureTeamExists(receiver: Player, color: NamedTextColor) {
        val data = playerData[receiver.uniqueId] ?: return

        // Check if team already sent to this player
        if (!data.sentTeamColors.add(color)) {
            return // Team already exists for this player
        }

        val teamName = teamPrefix + GlowColor.fromNamedTextColor(color).ordinal.toString(16)

        val teamInfo = WrapperPlayServerTeams.ScoreBoardTeamInfo(
            Component.text(teamName),  // displayName
            null,                       // prefix
            null,                       // suffix
            WrapperPlayServerTeams.NameTagVisibility.ALWAYS,
            WrapperPlayServerTeams.CollisionRule.NEVER, // Disable collision to prevent hitbox interaction
            color,                      // team color (glow color)
            WrapperPlayServerTeams.OptionData.NONE
        )

        val packet = WrapperPlayServerTeams(
            teamName,
            WrapperPlayServerTeams.TeamMode.CREATE,
            teamInfo
        )

        PacketEvents.getAPI().playerManager.sendPacket(receiver, packet)
    }

    // ==================== Display Entity Packet Methods ====================

    /**
     * Sends a spawn entity packet for a display entity.
     *
     * @param receiver the player to send the packet to
     * @param entityId the unique entity ID
     * @param entityUuid the entity UUID
     * @param entityType the entity type (BLOCK_DISPLAY, ITEM_DISPLAY, or TEXT_DISPLAY)
     * @param location the spawn location
     */
    private fun sendSpawnDisplayEntity(
        receiver: Player,
        entityId: Int,
        entityUuid: UUID,
        entityType: com.github.retrooper.packetevents.protocol.entity.type.EntityType,
        location: Location
    ) {
        val position = Vector3d(location.x, location.y, location.z)
        
        val packet = WrapperPlayServerSpawnEntity(
            entityId,
            Optional.of(entityUuid),
            entityType,
            position,
            location.pitch,
            location.yaw,
            location.yaw, // headYaw
            0, // data
            Optional.empty() // velocity
        )
        
        PacketEvents.getAPI().playerManager.sendPacket(receiver, packet)
    }

    /**
     * Sends metadata packet for a block display entity.
     *
     * @param receiver the player to send the packet to
     * @param entityId the entity ID
     * @param blockData the block data to display
     * @param color the glow color (ARGB)
     */
    private fun sendBlockDisplayMetadata(
        receiver: Player,
        entityId: Int,
        blockData: BlockData,
        color: Color
    ) {
        val metadata = mutableListOf<EntityData<*>>()
        
        // Entity flags with glowing bit set
        metadata.add(EntityData(ENTITY_FLAGS_INDEX, EntityDataTypes.BYTE, GLOWING_FLAG))
        
        // Glow color override (ARGB format with full alpha)
        metadata.add(EntityData(DISPLAY_GLOW_COLOR_OVERRIDE_INDEX, EntityDataTypes.INT, colorToArgb(color)))
        
        // Block state - convert Bukkit BlockData to block state ID
        val wrappedBlockState = SpigotConversionUtil.fromBukkitBlockData(blockData)
        val blockStateId = wrappedBlockState.globalId
        metadata.add(EntityData(BLOCK_DISPLAY_BLOCK_STATE_INDEX, EntityDataTypes.BLOCK_STATE, blockStateId))
        
        val packet = WrapperPlayServerEntityMetadata(entityId, metadata)
        PacketEvents.getAPI().playerManager.sendPacket(receiver, packet)
    }

    /**
     * Sends metadata packet for an item display entity.
     *
     * @param receiver the player to send the packet to
     * @param entityId the entity ID
     * @param itemStack the item to display
     * @param color the glow color (ARGB)
     */
    private fun sendItemDisplayMetadata(
        receiver: Player,
        entityId: Int,
        itemStack: ItemStack,
        color: Color
    ) {
        val metadata = mutableListOf<EntityData<*>>()
        
        // Entity flags with glowing bit set
        metadata.add(EntityData(ENTITY_FLAGS_INDEX, EntityDataTypes.BYTE, GLOWING_FLAG))
        
        // Glow color override (ARGB format with full alpha)
        metadata.add(EntityData(DISPLAY_GLOW_COLOR_OVERRIDE_INDEX, EntityDataTypes.INT, colorToArgb(color)))
        
        // Item stack - convert Bukkit ItemStack to PacketEvents ItemStack
        val packetEventsItem = SpigotConversionUtil.fromBukkitItemStack(itemStack)
        metadata.add(EntityData(ITEM_DISPLAY_ITEM_INDEX, EntityDataTypes.ITEMSTACK, packetEventsItem))
        
        val packet = WrapperPlayServerEntityMetadata(entityId, metadata)
        PacketEvents.getAPI().playerManager.sendPacket(receiver, packet)
    }

    /**
     * Sends metadata packet for a text display entity.
     *
     * @param receiver the player to send the packet to
     * @param entityId the entity ID
     * @param text the text component to display
     * @param color the glow color (ARGB)
     */
    private fun sendTextDisplayMetadata(
        receiver: Player,
        entityId: Int,
        text: Component,
        color: Color
    ) {
        val metadata = mutableListOf<EntityData<*>>()
        
        // Entity flags with glowing bit set
        metadata.add(EntityData(ENTITY_FLAGS_INDEX, EntityDataTypes.BYTE, GLOWING_FLAG))
        
        // Glow color override (ARGB format with full alpha)
        metadata.add(EntityData(DISPLAY_GLOW_COLOR_OVERRIDE_INDEX, EntityDataTypes.INT, colorToArgb(color)))
        
        // Text component - use ADV_COMPONENT for Adventure Component
        metadata.add(EntityData(TEXT_DISPLAY_TEXT_INDEX, EntityDataTypes.ADV_COMPONENT, text))
        
        val packet = WrapperPlayServerEntityMetadata(entityId, metadata)
        PacketEvents.getAPI().playerManager.sendPacket(receiver, packet)
    }

    /**
     * Converts a Bukkit Color to ARGB integer format.
     *
     * @param color the Bukkit color
     * @return ARGB integer with full alpha (255)
     */
    private fun colorToArgb(color: Color): Int {
        return (255 shl 24) or (color.red shl 16) or (color.green shl 8) or color.blue
    }

    // ==================== Shulker Marker Packet Methods ====================

    /**
     * Sends a spawn entity packet for an invisible shulker.
     *
     * @param receiver the player to send the packet to
     * @param entityId the unique entity ID
     * @param entityUuid the entity UUID
     * @param location the spawn location (block-aligned)
     */
    private fun sendSpawnShulkerEntity(
        receiver: Player,
        entityId: Int,
        entityUuid: UUID,
        location: Location
    ) {
        val position = Vector3d(location.x, location.y, location.z)

        val packet = WrapperPlayServerSpawnEntity(
            entityId,
            Optional.of(entityUuid),
            EntityTypes.SHULKER,
            position,
            0f, // pitch
            0f, // yaw
            0f, // headYaw
            0,  // data
            Optional.empty() // velocity
        )

        PacketEvents.getAPI().playerManager.sendPacket(receiver, packet)
    }

    /**
     * Sends metadata packet for an invisible glowing shulker.
     *
     * Sets both the invisibility flag (bit 5) and glowing flag (bit 6).
     *
     * @param receiver the player to send the packet to
     * @param entityId the entity ID
     */
    private fun sendShulkerMetadata(receiver: Player, entityId: Int) {
        val metadata = listOf(
            EntityData(ENTITY_FLAGS_INDEX, EntityDataTypes.BYTE, INVISIBLE_GLOWING_FLAGS)
        )

        val packet = WrapperPlayServerEntityMetadata(entityId, metadata)
        PacketEvents.getAPI().playerManager.sendPacket(receiver, packet)
    }

    /**
     * Adds a virtual entity (by UUID) to a team for glow color.
     *
     * @param receiver the player to send the packet to
     * @param entityUuid the entity UUID
     * @param color the team color
     */
    private fun addVirtualEntityToTeam(receiver: Player, entityUuid: UUID, color: NamedTextColor) {
        val teamName = teamPrefix + GlowColor.fromNamedTextColor(color).ordinal.toString(16)

        val packet = WrapperPlayServerTeams(
            teamName,
            WrapperPlayServerTeams.TeamMode.ADD_ENTITIES,
            null as WrapperPlayServerTeams.ScoreBoardTeamInfo?,
            entityUuid.toString()
        )

        PacketEvents.getAPI().playerManager.sendPacket(receiver, packet)
    }

    /**
     * Removes a virtual entity from a team.
     *
     * @param receiver the player to send the packet to
     * @param entityUuid the entity UUID
     * @param color the team color
     */
    private fun removeVirtualEntityFromTeam(receiver: Player, entityUuid: UUID, color: NamedTextColor) {
        val teamName = teamPrefix + GlowColor.fromNamedTextColor(color).ordinal.toString(16)

        val packet = WrapperPlayServerTeams(
            teamName,
            WrapperPlayServerTeams.TeamMode.REMOVE_ENTITIES,
            null as WrapperPlayServerTeams.ScoreBoardTeamInfo?,
            entityUuid.toString()
        )

        PacketEvents.getAPI().playerManager.sendPacket(receiver, packet)
    }

}
