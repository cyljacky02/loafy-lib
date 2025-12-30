package me.cyljacky02.loafylib.glow

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import net.kyori.adventure.text.Component
import org.bukkit.Color
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.block.data.BlockData
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.util.Transformation
import java.util.UUID
import java.util.logging.Logger

/**
 * Unit tests for NoOpGlowingService.
 *
 * These tests verify that NoOpGlowingService:
 * - Returns false for isAvailable()
 * - Logs a warning only once per session
 * - All methods don't throw exceptions
 * - Spawn methods return 0
 * - getActiveDisplays returns empty set
 */
class NoOpGlowingServiceTest : FunSpec({

    afterTest {
        unmockkAll()
    }

    // =============================================================================
    // Helper Functions
    // =============================================================================

    fun mockPlayer(): Player {
        return mockk<Player> {
            every { uniqueId } returns UUID.randomUUID()
            every { isOnline } returns true
        }
    }

    fun mockEntity(): Entity {
        return mockk<Entity> {
            every { entityId } returns 123
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
        }
    }

    fun mockBlockData(): BlockData = mockk()
    fun mockItemStack(): ItemStack = mockk()
    fun mockTransformation(): Transformation = mockk()

    // =============================================================================
    // isAvailable() returns false
    // =============================================================================

    context("isAvailable() returns false") {

        test("isAvailable returns false") {
            val logger = mockk<Logger>(relaxed = true)
            val service = NoOpGlowingService(logger)

            service.isAvailable() shouldBe false
        }
    }

    // =============================================================================
    // Warning logged only once per session
    // =============================================================================

    context("Warning logged only once per session") {

        test("warning is logged on first method call") {
            val logger = mockk<Logger>(relaxed = true)
            val service = NoOpGlowingService(logger)

            service.setGlowing(mockEntity(), mockPlayer(), GlowColor.RED)

            verify(exactly = 1) {
                logger.warning(match<String> { it.contains("PacketEvents") })
            }
        }

        test("warning is logged only once across multiple method calls") {
            val logger = mockk<Logger>(relaxed = true)
            val service = NoOpGlowingService(logger)
            val player = mockPlayer()
            val entity = mockEntity()

            // Call multiple methods
            service.setGlowing(entity, player, GlowColor.RED)
            service.unsetGlowing(entity, player)
            service.isGlowing(entity, player)
            service.spawnGlowingBlock(mockLocation(), mockBlockData(), player, Color.RED)
            service.spawnGlowingItem(mockLocation(), mockItemStack(), player, Color.GREEN)
            service.spawnGlowingText(mockLocation(), Component.text("test"), player, Color.BLUE)
            service.removeDisplay(1, player)
            service.updateDisplayColor(1, player, Color.WHITE)
            service.updateDisplayTransform(1, player, mockTransformation())
            service.getActiveDisplays(player)

            // Warning should only be logged once
            verify(exactly = 1) {
                logger.warning(any<String>())
            }
        }

        test("warning is not logged for isAvailable") {
            val logger = mockk<Logger>(relaxed = true)
            val service = NoOpGlowingService(logger)

            service.isAvailable()

            verify(exactly = 0) {
                logger.warning(any<String>())
            }
        }
    }

    // =============================================================================
    // Methods don't throw exceptions
    // =============================================================================

    context("Methods don't throw exceptions") {

        test("setGlowing does not throw") {
            val logger = mockk<Logger>(relaxed = true)
            val service = NoOpGlowingService(logger)

            // Should not throw
            service.setGlowing(mockEntity(), mockPlayer(), GlowColor.RED)
            service.setGlowing(mockEntity(), mockPlayer(), null)
        }

        test("unsetGlowing does not throw") {
            val logger = mockk<Logger>(relaxed = true)
            val service = NoOpGlowingService(logger)

            // Should not throw
            service.unsetGlowing(mockEntity(), mockPlayer())
        }

        test("isGlowing does not throw and returns false") {
            val logger = mockk<Logger>(relaxed = true)
            val service = NoOpGlowingService(logger)

            val result = service.isGlowing(mockEntity(), mockPlayer())

            result shouldBe false
        }

        test("removeDisplay does not throw") {
            val logger = mockk<Logger>(relaxed = true)
            val service = NoOpGlowingService(logger)

            // Should not throw
            service.removeDisplay(123, mockPlayer())
            service.removeDisplay(-1, mockPlayer())
            service.removeDisplay(0, mockPlayer())
        }

        test("updateDisplayColor does not throw") {
            val logger = mockk<Logger>(relaxed = true)
            val service = NoOpGlowingService(logger)

            // Should not throw
            service.updateDisplayColor(123, mockPlayer(), Color.RED)
        }

        test("updateDisplayTransform does not throw") {
            val logger = mockk<Logger>(relaxed = true)
            val service = NoOpGlowingService(logger)

            // Should not throw
            service.updateDisplayTransform(123, mockPlayer(), mockTransformation())
        }
    }

    // =============================================================================
    // Spawn methods return 0
    // =============================================================================

    context("Spawn methods return 0") {

        test("spawnGlowingBlock returns 0") {
            val logger = mockk<Logger>(relaxed = true)
            val service = NoOpGlowingService(logger)

            val result = service.spawnGlowingBlock(
                mockLocation(),
                mockBlockData(),
                mockPlayer(),
                Color.RED
            )

            result shouldBe 0
        }

        test("spawnGlowingItem returns 0") {
            val logger = mockk<Logger>(relaxed = true)
            val service = NoOpGlowingService(logger)

            val result = service.spawnGlowingItem(
                mockLocation(),
                mockItemStack(),
                mockPlayer(),
                Color.GREEN
            )

            result shouldBe 0
        }

        test("spawnGlowingText returns 0") {
            val logger = mockk<Logger>(relaxed = true)
            val service = NoOpGlowingService(logger)

            val result = service.spawnGlowingText(
                mockLocation(),
                Component.text("test"),
                mockPlayer(),
                Color.BLUE
            )

            result shouldBe 0
        }
    }

    // =============================================================================
    // getActiveDisplays returns empty set
    // =============================================================================

    context("getActiveDisplays returns empty set") {

        test("getActiveDisplays returns empty set") {
            val logger = mockk<Logger>(relaxed = true)
            val service = NoOpGlowingService(logger)

            val result = service.getActiveDisplays(mockPlayer())

            result.shouldBeEmpty()
        }

        test("getActiveDisplays returns empty set even after spawn calls") {
            val logger = mockk<Logger>(relaxed = true)
            val service = NoOpGlowingService(logger)
            val player = mockPlayer()

            // Call spawn methods (they return 0 and don't track anything)
            service.spawnGlowingBlock(mockLocation(), mockBlockData(), player, Color.RED)
            service.spawnGlowingItem(mockLocation(), mockItemStack(), player, Color.GREEN)
            service.spawnGlowingText(mockLocation(), Component.text("test"), player, Color.BLUE)

            val result = service.getActiveDisplays(player)

            result.shouldBeEmpty()
        }
    }

    // =============================================================================
    // Lifecycle methods
    // =============================================================================

    context("Lifecycle methods") {

        test("initialize does not throw") {
            val logger = mockk<Logger>(relaxed = true)
            val service = NoOpGlowingService(logger)

            // Should not throw
            service.initialize()
        }

        test("shutdown does not throw") {
            val logger = mockk<Logger>(relaxed = true)
            val service = NoOpGlowingService(logger)

            // Should not throw
            service.shutdown()
        }
    }
})
