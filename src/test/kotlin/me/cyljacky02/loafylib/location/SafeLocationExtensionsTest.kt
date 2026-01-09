package me.cyljacky02.loafylib.location

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.*
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.WorldBorder
import org.bukkit.block.Block
import org.bukkit.util.VoxelShape
import java.util.concurrent.CompletableFuture

/**
 * Unit tests for SafeLocationExtensions.
 *
 * Verifies that each extension function correctly delegates to SafeLocation
 * and SafeLocationSearch, and that default SafetyOptions parameter behavior works.
 */
class SafeLocationExtensionsTest : FunSpec({

    /**
     * Creates a mock world with standard bounds.
     */
    fun createMockWorld(minHeight: Int = -64, maxHeight: Int = 320): World {
        val worldBorder = mockk<WorldBorder> {
            every { isInside(any<Location>()) } returns true
        }
        return mockk<World> {
            every { this@mockk.minHeight } returns minHeight
            every { this@mockk.maxHeight } returns maxHeight
            every { this@mockk.worldBorder } returns worldBorder
            every { environment } returns World.Environment.NORMAL
        }
    }

    /**
     * Creates a mock solid, non-hazardous block (safe ground).
     */
    fun createSafeGroundBlock(): Block = mockk {
        every { isSolid } returns true
        every { isPassable } returns false
        every { type } returns Material.STONE
    }

    /**
     * Creates a mock passable, non-hazardous block (safe body space).
     */
    fun createSafeBodyBlock(): Block = mockk {
        every { isSolid } returns false
        every { isPassable } returns true
        every { type } returns Material.AIR
        every { collisionShape } returns mockk<VoxelShape> {
            every { boundingBoxes } returns emptyList()
        }
    }

    /**
     * Sets up a world with safe blocks at the given coordinates.
     */
    fun setupSafeWorld(world: World, x: Int, y: Int, z: Int) {
        val groundBlock = createSafeGroundBlock()
        val feetBlock = createSafeBodyBlock()
        val headBlock = createSafeBodyBlock()

        every { world.getBlockAt(x, y - 1, z) } returns groundBlock
        every { world.getBlockAt(x, y, z) } returns feetBlock
        every { world.getBlockAt(x, y + 1, z) } returns headBlock

        // Setup blocks for collision checking
        every { world.getBlockAt(any<Int>(), any<Int>(), any<Int>()) } answers {
            val by = secondArg<Int>()
            when {
                by == y - 1 -> groundBlock
                by == y -> feetBlock
                by == y + 1 -> headBlock
                else -> createSafeBodyBlock()
            }
        }
    }

    /**
     * Creates a mock location with the given coordinates.
     */
    fun createMockLocation(world: World?, x: Int, y: Int, z: Int, yaw: Float = 0f, pitch: Float = 0f): Location {
        return mockk {
            every { this@mockk.world } returns world
            every { blockX } returns x
            every { blockY } returns y
            every { blockZ } returns z
            every { this@mockk.x } returns x.toDouble()
            every { this@mockk.y } returns y.toDouble()
            every { this@mockk.z } returns z.toDouble()
            every { this@mockk.yaw } returns yaw
            every { this@mockk.pitch } returns pitch
        }
    }

    context("Location.isSafe() extension") {

        test("delegates to SafeLocation.isSafe with default options") {
            val world = createMockWorld()
            setupSafeWorld(world, 0, 64, 0)

            val location = createMockLocation(world, 0, 64, 0)

            // Should delegate to SafeLocation.isSafe
            val result = location.isSafe()

            result shouldBe true
        }

        test("delegates to SafeLocation.isSafe with custom options") {
            val world = createMockWorld()
            setupSafeWorld(world, 0, 64, 0)

            val location = createMockLocation(world, 0, 64, 0)
            val customOptions = SafetyOptions(checkCollision = false)

            val result = location.isSafe(customOptions)

            result shouldBe true
        }

        test("returns false for null world") {
            val location = createMockLocation(null, 0, 64, 0)

            val result = location.isSafe()

            result shouldBe false
        }
    }

    context("Location.isSafeAsync() extension") {

        test("delegates to SafeLocation.isSafeAsync with default options") {
            val world = createMockWorld()
            setupSafeWorld(world, 0, 64, 0)

            // Mock async chunk loading
            every { world.getChunkAtAsync(any<Location>()) } returns CompletableFuture.completedFuture(mockk())

            val location = createMockLocation(world, 0, 64, 0)

            val future = location.isSafeAsync()
            val result = future.get()

            result shouldBe true
        }

        test("returns completed future with false for null world") {
            val location = createMockLocation(null, 0, 64, 0)

            val future = location.isSafeAsync()
            val result = future.get()

            result shouldBe false
        }
    }

    context("Location.findSafeNearby() extension") {

        test("delegates to SafeLocationSearch.findNearest with default parameters") {
            val world = createMockWorld()
            setupSafeWorld(world, 0, 64, 0)

            val location = createMockLocation(world, 0, 64, 0, 45f, 30f)

            val result = location.findSafeNearby()

            // Should find a safe location
            result shouldBe Location(world, 0.5, 64.0, 0.5, 45f, 30f)
        }

        test("delegates with custom radius parameters") {
            val world = createMockWorld()
            setupSafeWorld(world, 0, 64, 0)

            val location = createMockLocation(world, 0, 64, 0, 90f, -45f)

            val result = location.findSafeNearby(radiusXZ = 5, radiusY = 10)

            result shouldBe Location(world, 0.5, 64.0, 0.5, 90f, -45f)
        }

        test("returns null for null world") {
            val location = createMockLocation(null, 0, 64, 0)

            val result = location.findSafeNearby()

            result shouldBe null
        }
    }

    context("Location.findSafeNearbyAsync() extension") {

        test("delegates to SafeLocationSearch.findNearestAsync with default parameters") {
            val world = createMockWorld()
            setupSafeWorld(world, 0, 64, 0)

            // Mock async chunk loading
            every { world.getChunkAtAsync(any<Location>()) } returns CompletableFuture.completedFuture(mockk())

            val location = createMockLocation(world, 0, 64, 0, 45f, 30f)

            val future = location.findSafeNearbyAsync()
            val result = future.get()

            result shouldBe Location(world, 0.5, 64.0, 0.5, 45f, 30f)
        }

        test("returns completed future with null for null world") {
            val location = createMockLocation(null, 0, 64, 0)

            val future = location.findSafeNearbyAsync()
            val result = future.get()

            result shouldBe null
        }
    }

    context("Default SafetyOptions parameter behavior") {

        test("isSafe() uses SafetyOptions.DEFAULT when no options provided") {
            val world = createMockWorld()
            setupSafeWorld(world, 0, 64, 0)

            val location = createMockLocation(world, 0, 64, 0)

            // Both calls should produce the same result
            val withDefault = location.isSafe()
            val withExplicit = location.isSafe(SafetyOptions.DEFAULT)

            withDefault shouldBe withExplicit
        }

        test("findSafeNearby() uses SafetyOptions.DEFAULT when no options provided") {
            val world = createMockWorld()
            setupSafeWorld(world, 0, 64, 0)

            val location = createMockLocation(world, 0, 64, 0, 0f, 0f)

            // Both calls should produce the same result
            val withDefault = location.findSafeNearby()
            val withExplicit = location.findSafeNearby(options = SafetyOptions.DEFAULT)

            withDefault shouldBe withExplicit
        }
    }
})
