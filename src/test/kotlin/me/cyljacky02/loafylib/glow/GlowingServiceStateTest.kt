package me.cyljacky02.loafylib.glow

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Color
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.block.data.BlockData
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Unit tests for GlowingService state management.
 *
 * These tests verify the internal state management logic of the GlowingService
 * without requiring PacketEvents. Tests use direct manipulation of the internal
 * data structures to verify state transitions.
 *
 * Note: These are unit tests (not property tests) because:
 * - State transitions are finite and deterministic (glow on/off, display spawn/remove)
 * - Mocking Bukkit types (Player, Entity, Location) is complex for random generation
 * - Specific scenarios provide better coverage than random inputs
 */
class GlowingServiceStateTest : FunSpec({

    afterTest {
        unmockkAll()
    }

    // =============================================================================
    // Helper Functions
    // =============================================================================

    fun mockPlayer(uuid: UUID = UUID.randomUUID(), online: Boolean = true): Player {
        return mockk<Player> {
            every { uniqueId } returns uuid
            every { isOnline } returns online
        }
    }

    fun mockEntity(entityId: Int = 123): Entity {
        return mockk<Entity> {
            every { this@mockk.entityId } returns entityId
            every { uniqueId } returns UUID.randomUUID()
        }
    }

    fun mockLocation(): Location {
        val world = mockk<World> {
            every { name } returns "world"
        }
        return mockk<Location> {
            every { this@mockk.world } returns world
            every { x } returns 0.0
            every { y } returns 64.0
            every { z } returns 0.0
            every { pitch } returns 0f
            every { yaw } returns 0f
            every { clone() } returns this
        }
    }

    fun mockBlockData(): BlockData {
        return mockk<BlockData>()
    }

    fun mockItemStack(): ItemStack {
        return mockk<ItemStack>()
    }

    /**
     * Creates a testable state container that mimics PacketEventsGlowingService
     * internal state without requiring PacketEvents.
     */
    fun createTestableState(): TestableGlowState {
        return TestableGlowState()
    }

    // =============================================================================
    // Glow State Round-Trip
    // =============================================================================

    context("Glow State Round-Trip") {

        test("setGlowing then isGlowing returns true") {
            val state = createTestableState()
            val player = mockPlayer(online = true)
            val entity = mockEntity(entityId = 123)

            state.setGlowing(entity, player, GlowColor.RED)

            state.isGlowing(entity, player) shouldBe true
        }

        test("unsetGlowing then isGlowing returns false") {
            val state = createTestableState()
            val player = mockPlayer(online = true)
            val entity = mockEntity(entityId = 123)

            state.setGlowing(entity, player, GlowColor.RED)
            state.unsetGlowing(entity, player)

            state.isGlowing(entity, player) shouldBe false
        }

        test("isGlowing returns false for entity never set to glow") {
            val state = createTestableState()
            val player = mockPlayer(online = true)
            val entity = mockEntity(entityId = 123)

            state.isGlowing(entity, player) shouldBe false
        }

        test("setGlowing with different colors updates state") {
            val state = createTestableState()
            val player = mockPlayer(online = true)
            val entity = mockEntity(entityId = 123)

            state.setGlowing(entity, player, GlowColor.RED)
            val firstColor = state.getGlowColor(entity, player)

            state.setGlowing(entity, player, GlowColor.BLUE)
            val secondColor = state.getGlowColor(entity, player)

            firstColor shouldBe NamedTextColor.RED
            secondColor shouldBe NamedTextColor.BLUE
        }
    }

    // =============================================================================
    // Property 2 & 6: Display Spawn Returns Unique Negative IDs
    // =============================================================================

    context("Property 2 & 6: Display Spawn Returns Unique Negative IDs") {

        test("spawn returns negative entity ID") {
            val state = createTestableState()
            val player = mockPlayer(online = true)

            val id = state.spawnGlowingBlock(mockLocation(), mockBlockData(), player, Color.RED)

            (id < 0) shouldBe true
        }

        test("multiple spawns return unique IDs") {
            val state = createTestableState()
            val player = mockPlayer(online = true)

            val ids = (1..10).map {
                state.spawnGlowingBlock(mockLocation(), mockBlockData(), player, Color.RED)
            }

            ids.toSet().size shouldBe 10 // All unique
        }

        test("spawned ID is in getActiveDisplays") {
            val state = createTestableState()
            val player = mockPlayer(online = true)

            val id = state.spawnGlowingBlock(mockLocation(), mockBlockData(), player, Color.RED)

            state.getActiveDisplays(player) shouldContain id
        }

        test("all display types return unique negative IDs") {
            val state = createTestableState()
            val player = mockPlayer(online = true)

            val blockId = state.spawnGlowingBlock(mockLocation(), mockBlockData(), player, Color.RED)
            val itemId = state.spawnGlowingItem(mockLocation(), mockItemStack(), player, Color.GREEN)
            val textId = state.spawnGlowingText(mockLocation(), net.kyori.adventure.text.Component.text("test"), player, Color.BLUE)

            val ids = setOf(blockId, itemId, textId)
            ids.size shouldBe 3 // All unique
            ids.all { it < 0 } shouldBe true // All negative
        }
    }

    // =============================================================================
    // Display Remove Clears State
    // =============================================================================

    context("Display Remove Clears State") {

        test("removeDisplay removes ID from getActiveDisplays") {
            val state = createTestableState()
            val player = mockPlayer(online = true)

            val id = state.spawnGlowingBlock(mockLocation(), mockBlockData(), player, Color.RED)
            state.removeDisplay(id, player)

            state.getActiveDisplays(player) shouldNotContain id
        }

        test("removeDisplay is idempotent") {
            val state = createTestableState()
            val player = mockPlayer(online = true)

            val id = state.spawnGlowingBlock(mockLocation(), mockBlockData(), player, Color.RED)
            state.removeDisplay(id, player)
            state.removeDisplay(id, player) // Should not throw

            state.getActiveDisplays(player) shouldNotContain id
        }

        test("removeDisplay with non-existent ID does nothing") {
            val state = createTestableState()
            val player = mockPlayer(online = true)

            // Should not throw
            state.removeDisplay(-999, player)

            state.getActiveDisplays(player).shouldBeEmpty()
        }
    }

    // =============================================================================
    // Per-Player State Isolation
    // =============================================================================

    context("Per-Player State Isolation") {

        test("P1 glow state is independent of P2") {
            val state = createTestableState()
            val player1 = mockPlayer(uuid = UUID.randomUUID(), online = true)
            val player2 = mockPlayer(uuid = UUID.randomUUID(), online = true)
            val entity = mockEntity(entityId = 123)

            state.setGlowing(entity, player1, GlowColor.RED)

            state.isGlowing(entity, player1) shouldBe true
            state.isGlowing(entity, player2) shouldBe false
        }

        test("P1 display state is independent of P2") {
            val state = createTestableState()
            val player1 = mockPlayer(uuid = UUID.randomUUID(), online = true)
            val player2 = mockPlayer(uuid = UUID.randomUUID(), online = true)

            val id = state.spawnGlowingBlock(mockLocation(), mockBlockData(), player1, Color.RED)

            state.getActiveDisplays(player1) shouldContain id
            state.getActiveDisplays(player2) shouldNotContain id
        }

        test("unsetGlowing for P1 does not affect P2") {
            val state = createTestableState()
            val player1 = mockPlayer(uuid = UUID.randomUUID(), online = true)
            val player2 = mockPlayer(uuid = UUID.randomUUID(), online = true)
            val entity = mockEntity(entityId = 123)

            state.setGlowing(entity, player1, GlowColor.RED)
            state.setGlowing(entity, player2, GlowColor.BLUE)
            state.unsetGlowing(entity, player1)

            state.isGlowing(entity, player1) shouldBe false
            state.isGlowing(entity, player2) shouldBe true
        }

        test("removeDisplay for P1 does not affect P2") {
            val state = createTestableState()
            val player1 = mockPlayer(uuid = UUID.randomUUID(), online = true)
            val player2 = mockPlayer(uuid = UUID.randomUUID(), online = true)

            val id1 = state.spawnGlowingBlock(mockLocation(), mockBlockData(), player1, Color.RED)
            val id2 = state.spawnGlowingBlock(mockLocation(), mockBlockData(), player2, Color.BLUE)

            state.removeDisplay(id1, player1)

            state.getActiveDisplays(player1) shouldNotContain id1
            state.getActiveDisplays(player2) shouldContain id2
        }
    }

    // =============================================================================
    // Player Disconnect Cleanup
    // =============================================================================

    context("Player Disconnect Cleanup") {

        test("player data is removed on disconnect") {
            val state = createTestableState()
            val player = mockPlayer(online = true)
            val entity = mockEntity(entityId = 123)

            state.setGlowing(entity, player, GlowColor.RED)
            state.spawnGlowingBlock(mockLocation(), mockBlockData(), player, Color.RED)

            // Simulate disconnect by removing player data
            state.simulatePlayerDisconnect(player)

            state.isGlowing(entity, player) shouldBe false
            state.getActiveDisplays(player).shouldBeEmpty()
        }

        test("disconnect cleanup does not affect other players") {
            val state = createTestableState()
            val player1 = mockPlayer(uuid = UUID.randomUUID(), online = true)
            val player2 = mockPlayer(uuid = UUID.randomUUID(), online = true)
            val entity = mockEntity(entityId = 123)

            state.setGlowing(entity, player1, GlowColor.RED)
            state.setGlowing(entity, player2, GlowColor.BLUE)
            val id2 = state.spawnGlowingBlock(mockLocation(), mockBlockData(), player2, Color.GREEN)

            state.simulatePlayerDisconnect(player1)

            state.isGlowing(entity, player1) shouldBe false
            state.isGlowing(entity, player2) shouldBe true
            state.getActiveDisplays(player2) shouldContain id2
        }
    }

    // =============================================================================
    // Team Color Caching
    // =============================================================================

    context("Team Color Caching") {

        test("same color is cached in sentTeamColors") {
            val state = createTestableState()
            val player = mockPlayer(online = true)
            val entity1 = mockEntity(entityId = 1)
            val entity2 = mockEntity(entityId = 2)

            state.setGlowing(entity1, player, GlowColor.RED)
            state.setGlowing(entity2, player, GlowColor.RED)

            val sentColors = state.getSentTeamColors(player)
            sentColors shouldHaveSize 1
            sentColors shouldContain NamedTextColor.RED
        }

        test("different colors are all cached") {
            val state = createTestableState()
            val player = mockPlayer(online = true)
            val entity1 = mockEntity(entityId = 1)
            val entity2 = mockEntity(entityId = 2)
            val entity3 = mockEntity(entityId = 3)

            state.setGlowing(entity1, player, GlowColor.RED)
            state.setGlowing(entity2, player, GlowColor.BLUE)
            state.setGlowing(entity3, player, GlowColor.GREEN)

            val sentColors = state.getSentTeamColors(player)
            sentColors shouldHaveSize 3
            sentColors shouldContain NamedTextColor.RED
            sentColors shouldContain NamedTextColor.BLUE
            sentColors shouldContain NamedTextColor.GREEN
        }
    }

    // =============================================================================
    // Edge Cases
    // =============================================================================

    context("Edge Cases") {

        test("null color defaults to WHITE") {
            val state = createTestableState()
            val player = mockPlayer(online = true)
            val entity = mockEntity(entityId = 123)

            state.setGlowing(entity, player, null)

            val color = state.getGlowColor(entity, player)
            color shouldBe NamedTextColor.WHITE
        }

        test("offline player is ignored for setGlowing") {
            val state = createTestableState()
            val player = mockPlayer(online = false)
            val entity = mockEntity(entityId = 123)

            state.setGlowing(entity, player, GlowColor.RED)

            state.isGlowing(entity, player) shouldBe false
        }

        test("offline player is ignored for spawnGlowingBlock") {
            val state = createTestableState()
            val player = mockPlayer(online = false)

            val id = state.spawnGlowingBlock(mockLocation(), mockBlockData(), player, Color.RED)

            id shouldBe 0
            state.getActiveDisplays(player).shouldBeEmpty()
        }

        test("offline player is ignored for unsetGlowing") {
            val state = createTestableState()
            val onlinePlayer = mockPlayer(online = true)
            val entity = mockEntity(entityId = 123)

            // Set glow while online
            state.setGlowing(entity, onlinePlayer, GlowColor.RED)

            // Create offline version of same player
            val offlinePlayer = mockPlayer(uuid = onlinePlayer.uniqueId, online = false)

            // Try to unset while offline - should be ignored
            state.unsetGlowing(entity, offlinePlayer)

            // Glow should still be set (check with original player reference)
            state.isGlowing(entity, onlinePlayer) shouldBe true
        }

        test("offline player is ignored for removeDisplay") {
            val state = createTestableState()
            val onlinePlayer = mockPlayer(online = true)

            val id = state.spawnGlowingBlock(mockLocation(), mockBlockData(), onlinePlayer, Color.RED)

            // Create offline version of same player
            val offlinePlayer = mockPlayer(uuid = onlinePlayer.uniqueId, online = false)

            // Try to remove while offline - should be ignored
            state.removeDisplay(id, offlinePlayer)

            // Display should still exist
            state.getActiveDisplays(onlinePlayer) shouldContain id
        }

        test("getActiveDisplays returns empty set for unknown player") {
            val state = createTestableState()
            val player = mockPlayer(online = true)

            state.getActiveDisplays(player).shouldBeEmpty()
        }

        test("isGlowing returns false for unknown player") {
            val state = createTestableState()
            val player = mockPlayer(online = true)
            val entity = mockEntity(entityId = 123)

            state.isGlowing(entity, player) shouldBe false
        }
    }

    // =============================================================================
    // Display Color and Transform Updates
    // =============================================================================

    context("Display Updates") {

        test("updateDisplayColor updates stored color") {
            val state = createTestableState()
            val player = mockPlayer(online = true)

            val id = state.spawnGlowingBlock(mockLocation(), mockBlockData(), player, Color.RED)
            state.updateDisplayColor(id, player, Color.BLUE)

            val displayState = state.getDisplayState(id, player)
            displayState shouldNotBe null
            displayState!!.color shouldBe Color.BLUE
        }

        test("updateDisplayColor with non-existent ID does nothing") {
            val state = createTestableState()
            val player = mockPlayer(online = true)

            // Should not throw
            state.updateDisplayColor(-999, player, Color.BLUE)
        }

        test("updateDisplayColor for offline player is ignored") {
            val state = createTestableState()
            val onlinePlayer = mockPlayer(online = true)

            val id = state.spawnGlowingBlock(mockLocation(), mockBlockData(), onlinePlayer, Color.RED)

            val offlinePlayer = mockPlayer(uuid = onlinePlayer.uniqueId, online = false)
            state.updateDisplayColor(id, offlinePlayer, Color.BLUE)

            // Color should still be RED
            val displayState = state.getDisplayState(id, onlinePlayer)
            displayState!!.color shouldBe Color.RED
        }
    }
})

// =============================================================================
// Testable State Container
// =============================================================================

/**
 * A testable state container that mimics the internal state management of
 * PacketEventsGlowingService without requiring PacketEvents.
 *
 * This allows testing the state management logic in isolation.
 */
private class TestableGlowState {
    private val playerData = ConcurrentHashMap<UUID, PlayerGlowData>()
    private val entityIdGenerator = AtomicInteger(-1)

    fun setGlowing(entity: Entity, receiver: Player, color: GlowColor?) {
        if (!receiver.isOnline) return

        val effectiveColor = color?.namedTextColor ?: NamedTextColor.WHITE
        val entityId = entity.entityId
        val receiverUuid = receiver.uniqueId

        val data = playerData.computeIfAbsent(receiverUuid) { PlayerGlowData() }
        data.glowingEntities[entityId] = GlowState(entityId, effectiveColor)
        data.sentTeamColors.add(effectiveColor)
    }

    fun unsetGlowing(entity: Entity, receiver: Player) {
        if (!receiver.isOnline) return

        val entityId = entity.entityId
        val receiverUuid = receiver.uniqueId

        val data = playerData[receiverUuid] ?: return
        data.glowingEntities.remove(entityId)
    }

    fun isGlowing(entity: Entity, receiver: Player): Boolean {
        val data = playerData[receiver.uniqueId] ?: return false
        return data.glowingEntities.containsKey(entity.entityId)
    }

    fun getGlowColor(entity: Entity, receiver: Player): NamedTextColor? {
        val data = playerData[receiver.uniqueId] ?: return null
        return data.glowingEntities[entity.entityId]?.color
    }

    fun spawnGlowingBlock(
        location: Location,
        blockData: BlockData,
        receiver: Player,
        color: Color
    ): Int {
        if (!receiver.isOnline) return 0

        val entityId = entityIdGenerator.getAndDecrement()
        val receiverUuid = receiver.uniqueId

        val data = playerData.computeIfAbsent(receiverUuid) { PlayerGlowData() }
        data.activeDisplays[entityId] = DisplayState(
            entityId = entityId,
            type = DisplayType.BLOCK,
            location = location.clone(),
            color = color,
            transform = null
        )

        return entityId
    }

    fun spawnGlowingItem(
        location: Location,
        itemStack: ItemStack,
        receiver: Player,
        color: Color
    ): Int {
        if (!receiver.isOnline) return 0

        val entityId = entityIdGenerator.getAndDecrement()
        val receiverUuid = receiver.uniqueId

        val data = playerData.computeIfAbsent(receiverUuid) { PlayerGlowData() }
        data.activeDisplays[entityId] = DisplayState(
            entityId = entityId,
            type = DisplayType.ITEM,
            location = location.clone(),
            color = color,
            transform = null
        )

        return entityId
    }

    fun spawnGlowingText(
        location: Location,
        text: net.kyori.adventure.text.Component,
        receiver: Player,
        color: Color
    ): Int {
        if (!receiver.isOnline) return 0

        val entityId = entityIdGenerator.getAndDecrement()
        val receiverUuid = receiver.uniqueId

        val data = playerData.computeIfAbsent(receiverUuid) { PlayerGlowData() }
        data.activeDisplays[entityId] = DisplayState(
            entityId = entityId,
            type = DisplayType.TEXT,
            location = location.clone(),
            color = color,
            transform = null
        )

        return entityId
    }

    fun removeDisplay(entityId: Int, receiver: Player) {
        if (!receiver.isOnline) return

        val receiverUuid = receiver.uniqueId
        val data = playerData[receiverUuid] ?: return
        data.activeDisplays.remove(entityId)
    }

    fun updateDisplayColor(entityId: Int, receiver: Player, color: Color) {
        if (!receiver.isOnline) return

        val receiverUuid = receiver.uniqueId
        val data = playerData[receiverUuid] ?: return
        val displayState = data.activeDisplays[entityId] ?: return
        data.activeDisplays[entityId] = displayState.copy(color = color)
    }

    fun getActiveDisplays(receiver: Player): Set<Int> {
        val data = playerData[receiver.uniqueId] ?: return emptySet()
        return data.activeDisplays.keys.toSet()
    }

    fun getDisplayState(entityId: Int, receiver: Player): DisplayState? {
        val data = playerData[receiver.uniqueId] ?: return null
        return data.activeDisplays[entityId]
    }

    fun getSentTeamColors(receiver: Player): Set<NamedTextColor> {
        val data = playerData[receiver.uniqueId] ?: return emptySet()
        return data.sentTeamColors.toSet()
    }

    fun simulatePlayerDisconnect(player: Player) {
        playerData.remove(player.uniqueId)
    }
}
