package me.cyljacky02.loafylib.location

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll
import io.mockk.every
import io.mockk.mockk
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.WorldBorder
import org.bukkit.block.Block
import org.bukkit.entity.*
import org.bukkit.util.BoundingBox
import org.bukkit.util.VoxelShape

/**
 * Property-based tests for SafeLocation utility.
 *
 * Tests validate the correctness properties defined in the design document.
 * Each property test runs minimum 100 iterations with randomly generated inputs.
 */
class SafeLocationPropertyTest : FunSpec({

    // Common test configuration
    val propTestConfig = PropTestConfig(iterations = 100)

    // Arbitrary generators for test data
    val arbValidY = Arb.int(-64..318) // Valid Y range for standard world
    val arbBelowMinY = Arb.int(-1000..-65) // Below min height
    val arbAboveMaxY = Arb.int(319..1000) // At or above maxHeight - 1

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


    context("Y Bounds Validation") {
        /**
         * For any location with a Y coordinate outside world bounds (below minHeight
         * or at/above maxHeight-1), `isSafe()` SHALL return false regardless of other
         * conditions. Conversely, locations within Y bounds SHALL NOT be rejected
         * solely due to Y coordinate.
         */

        test("locations below minHeight return false when checkYBounds=true") {
            checkAll(propTestConfig, arbBelowMinY, Arb.int(-1000..1000), Arb.int(-1000..1000)) { y, x, z ->
                val world = createMockWorld()
                setupSafeWorld(world, x, y, z)

                val location = mockk<Location> {
                    every { this@mockk.world } returns world
                    every { blockX } returns x
                    every { blockY } returns y
                    every { blockZ } returns z
                    every { this@mockk.x } returns x.toDouble()
                    every { this@mockk.y } returns y.toDouble()
                    every { this@mockk.z } returns z.toDouble()
                }

                SafeLocation.isSafe(location, SafetyOptions(checkYBounds = true)) shouldBe false
            }
        }

        test("locations at or above maxHeight-1 return false when checkYBounds=true") {
            checkAll(propTestConfig, arbAboveMaxY, Arb.int(-1000..1000), Arb.int(-1000..1000)) { y, x, z ->
                val world = createMockWorld()
                setupSafeWorld(world, x, y, z)

                val location = mockk<Location> {
                    every { this@mockk.world } returns world
                    every { blockX } returns x
                    every { blockY } returns y
                    every { blockZ } returns z
                    every { this@mockk.x } returns x.toDouble()
                    every { this@mockk.y } returns y.toDouble()
                    every { this@mockk.z } returns z.toDouble()
                }

                SafeLocation.isSafe(location, SafetyOptions(checkYBounds = true)) shouldBe false
            }
        }

        test("locations within Y bounds are not rejected due to Y coordinate alone") {
            checkAll(propTestConfig, arbValidY, Arb.int(-1000..1000), Arb.int(-1000..1000)) { y, x, z ->
                val world = createMockWorld()
                setupSafeWorld(world, x, y, z)

                val location = mockk<Location> {
                    every { this@mockk.world } returns world
                    every { blockX } returns x
                    every { blockY } returns y
                    every { blockZ } returns z
                    every { this@mockk.x } returns x.toDouble()
                    every { this@mockk.y } returns y.toDouble()
                    every { this@mockk.z } returns z.toDouble()
                }

                // With all other conditions safe, location should be safe
                SafeLocation.isSafe(location, SafetyOptions(checkYBounds = true, checkCollision = false)) shouldBe true
            }
        }

        test("Y bounds check is skipped when checkYBounds=false") {
            checkAll(propTestConfig, arbBelowMinY, Arb.int(-1000..1000), Arb.int(-1000..1000)) { y, x, z ->
                val world = createMockWorld()
                setupSafeWorld(world, x, y, z)

                val location = mockk<Location> {
                    every { this@mockk.world } returns world
                    every { blockX } returns x
                    every { blockY } returns y
                    every { blockZ } returns z
                    every { this@mockk.x } returns x.toDouble()
                    every { this@mockk.y } returns y.toDouble()
                    every { this@mockk.z } returns z.toDouble()
                }

                // With checkYBounds=false, Y coordinate should not cause rejection
                SafeLocation.isSafe(location, SafetyOptions(checkYBounds = false, checkCollision = false)) shouldBe true
            }
        }
    }


    context("Ground Block Validation") {
        /**
         * For any location where the ground block (y-1) is either non-solid OR is a
         * hazardous ground material (LAVA, MAGMA_BLOCK, CAMPFIRE, SOUL_CAMPFIRE, CACTUS,
         * POINTED_DRIPSTONE), `isSafe()` SHALL return false.
         */

        val hazardousGroundMaterials = listOf(
            Material.LAVA,
            Material.MAGMA_BLOCK,
            Material.CAMPFIRE,
            Material.SOUL_CAMPFIRE,
            Material.CACTUS,
            Material.POINTED_DRIPSTONE
        )

        test("non-solid ground blocks return false") {
            checkAll(propTestConfig, arbValidY, Arb.int(-1000..1000), Arb.int(-1000..1000)) { y, x, z ->
                val world = createMockWorld()

                // Non-solid ground block
                val groundBlock = mockk<Block> {
                    every { isSolid } returns false
                    every { isPassable } returns true
                    every { type } returns Material.AIR
                }
                val feetBlock = createSafeBodyBlock()
                val headBlock = createSafeBodyBlock()

                every { world.getBlockAt(x, y - 1, z) } returns groundBlock
                every { world.getBlockAt(x, y, z) } returns feetBlock
                every { world.getBlockAt(x, y + 1, z) } returns headBlock

                val location = mockk<Location> {
                    every { this@mockk.world } returns world
                    every { blockX } returns x
                    every { blockY } returns y
                    every { blockZ } returns z
                    every { this@mockk.x } returns x.toDouble()
                    every { this@mockk.y } returns y.toDouble()
                    every { this@mockk.z } returns z.toDouble()
                }

                SafeLocation.isSafe(location, SafetyOptions(checkCollision = false)) shouldBe false
            }
        }

        test("hazardous ground materials return false") {
            checkAll(
                propTestConfig,
                Arb.element(hazardousGroundMaterials),
                arbValidY,
                Arb.int(-1000..1000),
                Arb.int(-1000..1000)
            ) { hazardMaterial, y, x, z ->
                val world = createMockWorld()

                // Hazardous but solid ground block
                val groundBlock = mockk<Block> {
                    every { isSolid } returns true
                    every { isPassable } returns false
                    every { type } returns hazardMaterial
                }
                val feetBlock = createSafeBodyBlock()
                val headBlock = createSafeBodyBlock()

                every { world.getBlockAt(x, y - 1, z) } returns groundBlock
                every { world.getBlockAt(x, y, z) } returns feetBlock
                every { world.getBlockAt(x, y + 1, z) } returns headBlock

                val location = mockk<Location> {
                    every { this@mockk.world } returns world
                    every { blockX } returns x
                    every { blockY } returns y
                    every { blockZ } returns z
                    every { this@mockk.x } returns x.toDouble()
                    every { this@mockk.y } returns y.toDouble()
                    every { this@mockk.z } returns z.toDouble()
                }

                SafeLocation.isSafe(location, SafetyOptions(checkCollision = false)) shouldBe false
            }
        }

        test("solid non-hazardous ground blocks are accepted") {
            val safeMaterials = listOf(Material.STONE, Material.GRASS_BLOCK, Material.DIRT, Material.OAK_PLANKS)

            checkAll(
                propTestConfig,
                Arb.element(safeMaterials),
                arbValidY,
                Arb.int(-1000..1000),
                Arb.int(-1000..1000)
            ) { safeMaterial, y, x, z ->
                val world = createMockWorld()

                val groundBlock = mockk<Block> {
                    every { isSolid } returns true
                    every { isPassable } returns false
                    every { type } returns safeMaterial
                }
                val feetBlock = createSafeBodyBlock()
                val headBlock = createSafeBodyBlock()

                every { world.getBlockAt(x, y - 1, z) } returns groundBlock
                every { world.getBlockAt(x, y, z) } returns feetBlock
                every { world.getBlockAt(x, y + 1, z) } returns headBlock
                every { world.getBlockAt(any<Int>(), any<Int>(), any<Int>()) } answers {
                    val by = secondArg<Int>()
                    when {
                        by == y - 1 -> groundBlock
                        by == y -> feetBlock
                        by == y + 1 -> headBlock
                        else -> createSafeBodyBlock()
                    }
                }

                val location = mockk<Location> {
                    every { this@mockk.world } returns world
                    every { blockX } returns x
                    every { blockY } returns y
                    every { blockZ } returns z
                    every { this@mockk.x } returns x.toDouble()
                    every { this@mockk.y } returns y.toDouble()
                    every { this@mockk.z } returns z.toDouble()
                }

                SafeLocation.isSafe(location, SafetyOptions(checkCollision = false)) shouldBe true
            }
        }
    }


    context("Body Space Validation") {
        /**
         * For any location where either body space block (feet at y, head at y+1) is
         * either non-passable OR is a hazardous body material (LAVA, FIRE, SOUL_FIRE,
         * SWEET_BERRY_BUSH, WITHER_ROSE, POWDER_SNOW), `isSafe()` SHALL return false.
         */

        val hazardousBodyMaterials = listOf(
            Material.LAVA,
            Material.FIRE,
            Material.SOUL_FIRE,
            Material.SWEET_BERRY_BUSH,
            Material.WITHER_ROSE,
            Material.POWDER_SNOW
        )

        test("non-passable feet block returns false") {
            checkAll(propTestConfig, arbValidY, Arb.int(-1000..1000), Arb.int(-1000..1000)) { y, x, z ->
                val world = createMockWorld()

                val groundBlock = createSafeGroundBlock()
                // Non-passable feet block (like a solid block)
                val feetBlock = mockk<Block> {
                    every { isSolid } returns true
                    every { isPassable } returns false
                    every { type } returns Material.STONE
                }
                val headBlock = createSafeBodyBlock()

                every { world.getBlockAt(x, y - 1, z) } returns groundBlock
                every { world.getBlockAt(x, y, z) } returns feetBlock
                every { world.getBlockAt(x, y + 1, z) } returns headBlock

                val location = mockk<Location> {
                    every { this@mockk.world } returns world
                    every { blockX } returns x
                    every { blockY } returns y
                    every { blockZ } returns z
                    every { this@mockk.x } returns x.toDouble()
                    every { this@mockk.y } returns y.toDouble()
                    every { this@mockk.z } returns z.toDouble()
                }

                SafeLocation.isSafe(location, SafetyOptions(checkCollision = false)) shouldBe false
            }
        }

        test("non-passable head block returns false") {
            checkAll(propTestConfig, arbValidY, Arb.int(-1000..1000), Arb.int(-1000..1000)) { y, x, z ->
                val world = createMockWorld()

                val groundBlock = createSafeGroundBlock()
                val feetBlock = createSafeBodyBlock()
                // Non-passable head block
                val headBlock = mockk<Block> {
                    every { isSolid } returns true
                    every { isPassable } returns false
                    every { type } returns Material.STONE
                }

                every { world.getBlockAt(x, y - 1, z) } returns groundBlock
                every { world.getBlockAt(x, y, z) } returns feetBlock
                every { world.getBlockAt(x, y + 1, z) } returns headBlock

                val location = mockk<Location> {
                    every { this@mockk.world } returns world
                    every { blockX } returns x
                    every { blockY } returns y
                    every { blockZ } returns z
                    every { this@mockk.x } returns x.toDouble()
                    every { this@mockk.y } returns y.toDouble()
                    every { this@mockk.z } returns z.toDouble()
                }

                SafeLocation.isSafe(location, SafetyOptions(checkCollision = false)) shouldBe false
            }
        }

        test("hazardous body materials in feet position return false") {
            checkAll(
                propTestConfig,
                Arb.element(hazardousBodyMaterials),
                arbValidY,
                Arb.int(-1000..1000),
                Arb.int(-1000..1000)
            ) { hazardMaterial, y, x, z ->
                val world = createMockWorld()

                val groundBlock = createSafeGroundBlock()
                // Hazardous feet block (passable but dangerous)
                val feetBlock = mockk<Block> {
                    every { isSolid } returns false
                    every { isPassable } returns true
                    every { type } returns hazardMaterial
                }
                val headBlock = createSafeBodyBlock()

                every { world.getBlockAt(x, y - 1, z) } returns groundBlock
                every { world.getBlockAt(x, y, z) } returns feetBlock
                every { world.getBlockAt(x, y + 1, z) } returns headBlock

                val location = mockk<Location> {
                    every { this@mockk.world } returns world
                    every { blockX } returns x
                    every { blockY } returns y
                    every { blockZ } returns z
                    every { this@mockk.x } returns x.toDouble()
                    every { this@mockk.y } returns y.toDouble()
                    every { this@mockk.z } returns z.toDouble()
                }

                SafeLocation.isSafe(location, SafetyOptions(checkCollision = false, allowWater = false)) shouldBe false
            }
        }

        test("hazardous body materials in head position return false") {
            checkAll(
                propTestConfig,
                Arb.element(hazardousBodyMaterials),
                arbValidY,
                Arb.int(-1000..1000),
                Arb.int(-1000..1000)
            ) { hazardMaterial, y, x, z ->
                val world = createMockWorld()

                val groundBlock = createSafeGroundBlock()
                val feetBlock = createSafeBodyBlock()
                // Hazardous head block
                val headBlock = mockk<Block> {
                    every { isSolid } returns false
                    every { isPassable } returns true
                    every { type } returns hazardMaterial
                }

                every { world.getBlockAt(x, y - 1, z) } returns groundBlock
                every { world.getBlockAt(x, y, z) } returns feetBlock
                every { world.getBlockAt(x, y + 1, z) } returns headBlock

                val location = mockk<Location> {
                    every { this@mockk.world } returns world
                    every { blockX } returns x
                    every { blockY } returns y
                    every { blockZ } returns z
                    every { this@mockk.x } returns x.toDouble()
                    every { this@mockk.y } returns y.toDouble()
                    every { this@mockk.z } returns z.toDouble()
                }

                SafeLocation.isSafe(location, SafetyOptions(checkCollision = false, allowWater = false)) shouldBe false
            }
        }
    }


    context("Water Allowance Behavior") {
        /**
         * For any location with water in body space, `isSafe()` SHALL return true
         * when `allowWater=true` and SHALL return false when `allowWater=false`
         * (assuming all other conditions are met).
         */

        test("water in body space returns false when allowWater=false") {
            checkAll(propTestConfig, arbValidY, Arb.int(-1000..1000), Arb.int(-1000..1000)) { y, x, z ->
                val world = createMockWorld()

                val groundBlock = createSafeGroundBlock()
                // Water in feet position
                val feetBlock = mockk<Block> {
                    every { isSolid } returns false
                    every { isPassable } returns true // Water is passable in Minecraft
                    every { type } returns Material.WATER
                }
                val headBlock = createSafeBodyBlock()

                every { world.getBlockAt(x, y - 1, z) } returns groundBlock
                every { world.getBlockAt(x, y, z) } returns feetBlock
                every { world.getBlockAt(x, y + 1, z) } returns headBlock
                every { world.getBlockAt(any<Int>(), any<Int>(), any<Int>()) } answers {
                    val by = secondArg<Int>()
                    when {
                        by == y - 1 -> groundBlock
                        by == y -> feetBlock
                        by == y + 1 -> headBlock
                        else -> createSafeBodyBlock()
                    }
                }

                val location = mockk<Location> {
                    every { this@mockk.world } returns world
                    every { blockX } returns x
                    every { blockY } returns y
                    every { blockZ } returns z
                    every { this@mockk.x } returns x.toDouble()
                    every { this@mockk.y } returns y.toDouble()
                    every { this@mockk.z } returns z.toDouble()
                }

                SafeLocation.isSafe(location, SafetyOptions(allowWater = false, checkCollision = false)) shouldBe false
            }
        }

        test("water in body space returns true when allowWater=true") {
            checkAll(propTestConfig, arbValidY, Arb.int(-1000..1000), Arb.int(-1000..1000)) { y, x, z ->
                val world = createMockWorld()

                val groundBlock = createSafeGroundBlock()
                // Water in feet position
                val feetBlock = mockk<Block> {
                    every { isSolid } returns false
                    every { isPassable } returns true
                    every { type } returns Material.WATER
                }
                val headBlock = createSafeBodyBlock()

                every { world.getBlockAt(x, y - 1, z) } returns groundBlock
                every { world.getBlockAt(x, y, z) } returns feetBlock
                every { world.getBlockAt(x, y + 1, z) } returns headBlock
                every { world.getBlockAt(any<Int>(), any<Int>(), any<Int>()) } answers {
                    val by = secondArg<Int>()
                    when {
                        by == y - 1 -> groundBlock
                        by == y -> feetBlock
                        by == y + 1 -> headBlock
                        else -> createSafeBodyBlock()
                    }
                }

                val location = mockk<Location> {
                    every { this@mockk.world } returns world
                    every { blockX } returns x
                    every { blockY } returns y
                    every { blockZ } returns z
                    every { this@mockk.x } returns x.toDouble()
                    every { this@mockk.y } returns y.toDouble()
                    every { this@mockk.z } returns z.toDouble()
                }

                SafeLocation.isSafe(location, SafetyOptions(allowWater = true, checkCollision = false)) shouldBe true
            }
        }

        test("water in head position follows allowWater setting") {
            checkAll(propTestConfig, Arb.boolean(), arbValidY, Arb.int(-1000..1000), Arb.int(-1000..1000)) { allowWater, y, x, z ->
                val world = createMockWorld()

                val groundBlock = createSafeGroundBlock()
                val feetBlock = createSafeBodyBlock()
                // Water in head position
                val headBlock = mockk<Block> {
                    every { isSolid } returns false
                    every { isPassable } returns true
                    every { type } returns Material.WATER
                }

                every { world.getBlockAt(x, y - 1, z) } returns groundBlock
                every { world.getBlockAt(x, y, z) } returns feetBlock
                every { world.getBlockAt(x, y + 1, z) } returns headBlock
                every { world.getBlockAt(any<Int>(), any<Int>(), any<Int>()) } answers {
                    val by = secondArg<Int>()
                    when {
                        by == y - 1 -> groundBlock
                        by == y -> feetBlock
                        by == y + 1 -> headBlock
                        else -> createSafeBodyBlock()
                    }
                }

                val location = mockk<Location> {
                    every { this@mockk.world } returns world
                    every { blockX } returns x
                    every { blockY } returns y
                    every { blockZ } returns z
                    every { this@mockk.x } returns x.toDouble()
                    every { this@mockk.y } returns y.toDouble()
                    every { this@mockk.z } returns z.toDouble()
                }

                SafeLocation.isSafe(location, SafetyOptions(allowWater = allowWater, checkCollision = false)) shouldBe allowWater
            }
        }
    }


    context("Entity Obstruction Validation") {
        /**
         * For any location where `SafetyOptions.checkEntities` is true AND a collidable
         * entity (Boat, Player, ArmorStand, etc.) intersects the player bounding box at
         * that location, `isSafe()` SHALL return false. Non-collidable entities (Item,
         * ExperienceOrb, Arrow) SHALL NOT cause the check to fail.
         */

        test("collidable entities cause failure when checkEntities=true") {
            checkAll(propTestConfig, arbValidY, Arb.int(-1000..1000), Arb.int(-1000..1000)) { y, x, z ->
                val world = createMockWorld()
                setupSafeWorld(world, x, y, z)

                // Mock a collidable entity (Player)
                val collidableEntity = mockk<Player>()

                every { world.getNearbyEntities(any<BoundingBox>(), any()) } answers {
                    val predicate = secondArg<java.util.function.Predicate<Entity>>()
                    if (predicate.test(collidableEntity)) {
                        listOf(collidableEntity)
                    } else {
                        emptyList()
                    }
                }

                val location = mockk<Location> {
                    every { this@mockk.world } returns world
                    every { blockX } returns x
                    every { blockY } returns y
                    every { blockZ } returns z
                    every { this@mockk.x } returns x.toDouble()
                    every { this@mockk.y } returns y.toDouble()
                    every { this@mockk.z } returns z.toDouble()
                }

                SafeLocation.isSafe(location, SafetyOptions(checkEntities = true, checkCollision = false)) shouldBe false
            }
        }

        test("non-collidable entities do not cause failure") {
            checkAll(propTestConfig, arbValidY, Arb.int(-1000..1000), Arb.int(-1000..1000)) { y, x, z ->
                val world = createMockWorld()
                setupSafeWorld(world, x, y, z)

                // Mock non-collidable entities
                val itemEntity = mockk<Item>()
                val xpOrb = mockk<ExperienceOrb>()
                val arrow = mockk<Arrow>()

                every { world.getNearbyEntities(any<BoundingBox>(), any()) } answers {
                    val predicate = secondArg<java.util.function.Predicate<Entity>>()
                    // Filter returns empty because all entities are non-collidable
                    listOf(itemEntity, xpOrb, arrow).filter { predicate.test(it) }
                }

                val location = mockk<Location> {
                    every { this@mockk.world } returns world
                    every { blockX } returns x
                    every { blockY } returns y
                    every { blockZ } returns z
                    every { this@mockk.x } returns x.toDouble()
                    every { this@mockk.y } returns y.toDouble()
                    every { this@mockk.z } returns z.toDouble()
                }

                SafeLocation.isSafe(location, SafetyOptions(checkEntities = true, checkCollision = false)) shouldBe true
            }
        }

        test("entity check is skipped when checkEntities=false") {
            checkAll(propTestConfig, arbValidY, Arb.int(-1000..1000), Arb.int(-1000..1000)) { y, x, z ->
                val world = createMockWorld()
                setupSafeWorld(world, x, y, z)

                // Mock a collidable entity that would normally block
                val collidableEntity = mockk<Player>()

                every { world.getNearbyEntities(any<BoundingBox>(), any()) } answers {
                    val predicate = secondArg<java.util.function.Predicate<Entity>>()
                    if (predicate.test(collidableEntity)) {
                        listOf(collidableEntity)
                    } else {
                        emptyList()
                    }
                }

                val location = mockk<Location> {
                    every { this@mockk.world } returns world
                    every { blockX } returns x
                    every { blockY } returns y
                    every { blockZ } returns z
                    every { this@mockk.x } returns x.toDouble()
                    every { this@mockk.y } returns y.toDouble()
                    every { this@mockk.z } returns z.toDouble()
                }

                // With checkEntities=false, entity should not cause rejection
                SafeLocation.isSafe(location, SafetyOptions(checkEntities = false, checkCollision = false)) shouldBe true
            }
        }
    }

    context("Null World Handling") {
        /**
         * If the world is null, the Safe Location Checker SHALL return false.
         */
        test("null world returns false") {
            val location = mockk<Location> {
                every { world } returns null
            }

            SafeLocation.isSafe(location) shouldBe false
        }
    }
})
