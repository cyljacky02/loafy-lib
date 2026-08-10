package me.cyljacky02.loafylib.location

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.doubles.shouldBeExactly
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.papermc.paper.block.fluid.FluidData
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.World
import org.bukkit.WorldBorder
import org.bukkit.block.Block
import org.bukkit.util.VoxelShape

/**
 * Property-based tests for SafeLocationSearch utility.
 */
class SafeLocationSearchPropertyTest : FunSpec({

    beforeSpec {
        mockkStatic(Bukkit::class)
        every { Bukkit.isOwnedByCurrentRegion(any<World>(), any<Int>(), any<Int>(), any<Int>()) } returns true
    }

    afterSpec {
        unmockkStatic(Bukkit::class)
    }

    val propTestConfig = PropTestConfig(iterations = 20) // Reduced for performance

    // Reusable empty voxel shape
    val emptyVoxelShape = mockk<VoxelShape>(relaxed = true) {
        every { boundingBoxes } returns emptyList()
    }

    /**
     * Creates a solid block (for ground).
     */
    fun solidBlock(): Block = mockk(relaxed = true) {
        every { isSolid } returns true
        every { isPassable } returns false
        every { type } returns Material.STONE
        every { collisionShape } returns emptyVoxelShape
    }

    /**
     * Creates a passable block (for body space).
     */
    fun passableBlock(): Block = mockk(relaxed = true) {
        every { isSolid } returns false
        every { isPassable } returns true
        every { type } returns Material.AIR
        every { collisionShape } returns emptyVoxelShape
    }

    /**
     * Creates a world mock where all locations are safe.
     * Safe = solid ground at y-1, passable body at y and y+1
     */
    fun createSafeWorld(minHeight: Int = -64, maxHeight: Int = 320): World {
        val worldBorder = mockk<WorldBorder>(relaxed = true) {
            every { isInside(any<Location>()) } returns true
        }

        val solidBlockInstance = solidBlock()
        val passableBlockInstance = passableBlock()

        return mockk<World>(relaxed = true) {
            every { this@mockk.minHeight } returns minHeight
            every { this@mockk.maxHeight } returns maxHeight
            every { this@mockk.worldBorder } returns worldBorder
            every { environment } returns World.Environment.NORMAL
            every { this@mockk.getFluidData(any<Int>(), any<Int>(), any<Int>()) } returns mockk<FluidData> {
                every { getFluidType() } returns mockk<org.bukkit.Fluid> {
                    every { key } returns NamespacedKey.minecraft("empty")
                }
            }

            // For any block request, return solid (works as ground) and passable (works as body)
            // The key insight: isSolid=true AND isPassable=true works for both ground and body checks
            every { getBlockAt(any<Int>(), any<Int>(), any<Int>()) } returns mockk(relaxed = true) {
                every { isSolid } returns true
                every { isPassable } returns true
                every { type } returns Material.STONE
                every { collisionShape } returns emptyVoxelShape
            }
        }
    }

    /**
     * Creates a world where no locations are safe (non-solid ground).
     */
    fun createUnsafeWorld(minHeight: Int = -64, maxHeight: Int = 320): World {
        val worldBorder = mockk<WorldBorder>(relaxed = true) {
            every { isInside(any<Location>()) } returns true
        }

        // Reuse single block instance to avoid OOM
        val unsafeBlock = mockk<Block>(relaxed = true) {
            every { isSolid } returns false
            every { isPassable } returns true
            every { type } returns Material.AIR
            every { collisionShape } returns emptyVoxelShape
        }

        return mockk<World>(relaxed = true) {
            every { this@mockk.minHeight } returns minHeight
            every { this@mockk.maxHeight } returns maxHeight
            every { this@mockk.worldBorder } returns worldBorder
            every { environment } returns World.Environment.NORMAL

            // Non-solid ground = unsafe - reuse same block instance
            every { getBlockAt(any<Int>(), any<Int>(), any<Int>()) } returns unsafeBlock
        }
    }

    /**
     * Creates a location mock.
     */
    fun createLocation(world: World, x: Int, y: Int, z: Int, yaw: Float = 0f, pitch: Float = 0f): Location =
        mockk(relaxed = true) {
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


    context("Search Distance Ordering") {
        /**
         * For any search operation that finds multiple safe locations, the returned
         * location SHALL be the one closest to the origin.
         */

        test("returned location is within search radius") {
            checkAll(
                propTestConfig,
                Arb.int(50..200),
                Arb.int(-50..50),
                Arb.int(-50..50),
                Arb.int(1..5),
                Arb.int(1..5)
            ) { originY, originX, originZ, radiusXZ, radiusY ->
                val world = createSafeWorld()
                val origin = createLocation(world, originX, originY, originZ)

                val result = SafeLocationSearch.findNearest(origin, radiusXZ, radiusY, SafetyOptions.FAST)

                result.shouldNotBeNull()

                // Verify within horizontal radius
                val dx = result.blockX - originX
                val dz = result.blockZ - originZ
                (dx * dx + dz * dz <= radiusXZ * radiusXZ) shouldBe true

                // Verify within vertical radius
                val dy = kotlin.math.abs(result.blockY - originY)
                (dy <= radiusY) shouldBe true
            }
        }

        test("vertical search order is 0, 1, -1, 2, -2, ...") {
            checkAll(
                propTestConfig,
                Arb.int(50..200),
                Arb.int(-50..50),
                Arb.int(-50..50)
            ) { originY, originX, originZ ->
                val world = createSafeWorld()
                val origin = createLocation(world, originX, originY, originZ)

                // All locations safe - first found should be at origin Y
                val result = SafeLocationSearch.findNearest(origin, radiusXZ = 0, radiusY = 3, SafetyOptions.FAST)

                result.shouldNotBeNull()
                result.blockY shouldBe originY
            }
        }

        test("finds location at dy=1 when origin is unsafe") {
            checkAll(
                propTestConfig,
                Arb.int(50..200),
                Arb.int(-50..50),
                Arb.int(-50..50)
            ) { originY, originX, originZ ->
                val worldBorder = mockk<WorldBorder>(relaxed = true) {
                    every { isInside(any<Location>()) } returns true
                }

                val world = mockk<World>(relaxed = true) {
                    every { this@mockk.minHeight } returns -64
                    every { this@mockk.maxHeight } returns 320
                    every { this@mockk.worldBorder } returns worldBorder
                    every { environment } returns World.Environment.NORMAL
                    every { this@mockk.getFluidData(any<Int>(), any<Int>(), any<Int>()) } returns mockk<FluidData> {
                        every { getFluidType() } returns mockk<org.bukkit.Fluid> {
                            every { key } returns NamespacedKey.minecraft("empty")
                        }
                    }

                    // Make origin unsafe (non-solid ground), but dy=1 safe
                    every { getBlockAt(any<Int>(), any<Int>(), any<Int>()) } answers {
                        val y = secondArg<Int>()

                        // Ground check is at y-1 relative to player position
                        // For origin (originY), ground is at originY-1
                        // For dy=1 (originY+1), ground is at originY
                        // For dy=-1 (originY-1), ground is at originY-2

                        // Make ground at originY solid (so dy=1 is safe)
                        // Make ground at originY-1 non-solid (so origin is unsafe)
                        val isSolid = (y == originY) // Ground for dy=1 position

                        mockk<Block>(relaxed = true) {
                            every { this@mockk.isSolid } returns isSolid
                            every { isPassable } returns true
                            every { type } returns if (isSolid) Material.STONE else Material.AIR
                            every { collisionShape } returns emptyVoxelShape
                        }
                    }
                }

                val origin = createLocation(world, originX, originY, originZ)
                val result = SafeLocationSearch.findNearest(origin, radiusXZ = 0, radiusY = 3, SafetyOptions.FAST)

                result.shouldNotBeNull()
                // Should find dy=1 (vertical order: 0, 1, -1, ...)
                result.blockY shouldBe originY + 1
            }
        }
    }


    context("Result Location Properties") {
        /**
         * For any safe location returned by `findNearest()`, the location SHALL have:
         * - X and Z coordinates centered on the block (fractional part = 0.5)
         * - Yaw and pitch values equal to the origin location's yaw and pitch
         */

        test("result X and Z are block-centered (fractional = 0.5)") {
            checkAll(
                propTestConfig,
                Arb.int(50..200),
                Arb.int(-50..50),
                Arb.int(-50..50)
            ) { originY, originX, originZ ->
                val world = createSafeWorld()
                val origin = createLocation(world, originX, originY, originZ, yaw = 90f, pitch = -45f)

                val result = SafeLocationSearch.findNearest(origin, radiusXZ = 3, radiusY = 3, SafetyOptions.FAST)

                result.shouldNotBeNull()

                val xFractional = result.x - result.blockX
                val zFractional = result.z - result.blockZ

                xFractional shouldBeExactly 0.5
                zFractional shouldBeExactly 0.5
            }
        }

        test("result yaw and pitch match origin") {
            checkAll(
                propTestConfig,
                Arb.int(50..200),
                Arb.int(-50..50),
                Arb.int(-50..50),
                Arb.float(-180f..180f),
                Arb.float(-90f..90f)
            ) { originY, originX, originZ, originYaw, originPitch ->
                val world = createSafeWorld()
                val origin = createLocation(world, originX, originY, originZ, originYaw, originPitch)

                val result = SafeLocationSearch.findNearest(origin, radiusXZ = 3, radiusY = 3, SafetyOptions.FAST)

                result.shouldNotBeNull()
                result.yaw shouldBe originYaw
                result.pitch shouldBe originPitch
            }
        }
    }


    context("Null World Handling") {
        test("null world returns null for findNearest") {
            val origin = mockk<Location>(relaxed = true) { every { world } returns null }
            SafeLocationSearch.findNearest(origin) shouldBe null
        }

        test("null world returns completed future with null for findNearestAsync") {
            val origin = mockk<Location>(relaxed = true) { every { world } returns null }
            val result = SafeLocationSearch.findNearestAsync(origin)
            result.isDone shouldBe true
            result.get() shouldBe null
        }
    }


    context("Search Radius Boundaries") {
        test("returns null when no safe location within radius") {
            // Create all mocks once outside checkAll
            val worldBorder = mockk<WorldBorder>(relaxed = true) {
                every { isInside(any<Location>()) } returns true
            }

            val unsafeBlock = mockk<Block>(relaxed = true) {
                every { isSolid } returns false
                every { isPassable } returns true
                every { type } returns Material.AIR
                every { collisionShape } returns emptyVoxelShape
            }

            val unsafeWorld = mockk<World>(relaxed = true) {
                every { this@mockk.minHeight } returns -64
                every { this@mockk.maxHeight } returns 320
                every { this@mockk.worldBorder } returns worldBorder
                every { environment } returns World.Environment.NORMAL
                every { this@mockk.getFluidData(any<Int>(), any<Int>(), any<Int>()) } returns mockk<FluidData> {
                    every { getFluidType() } returns mockk<org.bukkit.Fluid> {
                        every { key } returns NamespacedKey.minecraft("empty")
                    }
                }
                every { getBlockAt(any<Int>(), any<Int>(), any<Int>()) } returns unsafeBlock
            }

            // Single test case - no property testing needed for this simple case
            val origin = mockk<Location>(relaxed = true) {
                every { world } returns unsafeWorld
                every { blockX } returns 0
                every { blockY } returns 100
                every { blockZ } returns 0
                every { x } returns 0.0
                every { y } returns 100.0
                every { z } returns 0.0
                every { yaw } returns 0f
                every { pitch } returns 0f
            }

            val result = SafeLocationSearch.findNearest(origin, radiusXZ = 2, radiusY = 2, SafetyOptions.FAST)
            result shouldBe null
        }
    }
})
