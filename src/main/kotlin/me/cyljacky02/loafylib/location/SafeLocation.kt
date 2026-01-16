package me.cyljacky02.loafylib.location

import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.entity.*
import org.bukkit.util.BoundingBox
import java.util.EnumSet
import java.util.concurrent.CompletableFuture

/**
 * Core utility object for safe location detection.
 *
 * Provides stateless functions to check if a location is safe for player teleportation.
 * Uses modern Paper APIs (Block.isSolid(), Block.isPassable(), World.getNearbyEntities())
 * for accurate safety checks without maintaining material whitelists.
 *
 * ## Thread Safety
 *
 * **Synchronous methods** ([isSafe]):
 * - MUST be called from the region thread that owns the location (Folia requirement)
 * - On Paper (non-Folia), this means the main server thread
 * - Calling from the wrong thread will result in undefined behavior or exceptions
 *
 * **Asynchronous methods** ([isSafeAsync]):
 * - Safe to call from any thread (main thread, async thread, scheduler thread, etc.)
 * - Uses Paper's `getChunkAtAsync()` to load the chunk before performing checks
 * - The callback (where actual safety check occurs) runs on:
 *   - **Folia**: The region thread that owns the location's chunk
 *   - **Paper**: The main server thread
 * - The returned [CompletableFuture] can be composed with other async operations
 *
 * ## Block.isPassable() Behavior Note
 *
 * The Paper API `Block.isPassable()` returns `false` for open doors, fence gates, and trapdoors
 * because they still have collision parts. This is acceptable for safe teleportation since we want
 * to avoid placing players inside any block with collision parts, even if technically passable.
 *
 * @see SafetyOptions for configuration options
 * @see SafeLocationSearch for finding nearby safe locations
 */
object SafeLocation {

    /** Player bounding box width (Minecraft standard) */
    const val PLAYER_WIDTH: Double = 0.6

    /** Player bounding box height (Minecraft standard) */
    const val PLAYER_HEIGHT: Double = 1.8

    /**
     * Ground hazards - blocks that damage when standing ON them.
     * Mapped to EntityDamageEvent.DamageCause types.
     */
    @JvmStatic
    val HAZARDOUS_GROUND: Set<Material> = EnumSet.of(
        // LAVA damage - DamageTypes.LAVA
        Material.LAVA,
        // HOT_FLOOR damage - DamageTypes.HOT_FLOOR (1 damage)
        Material.MAGMA_BLOCK,
        // CAMPFIRE damage - DamageTypes.CAMPFIRE (1-2 damage)
        Material.CAMPFIRE,
        Material.SOUL_CAMPFIRE,
        // CONTACT damage - DamageTypes.CACTUS
        Material.CACTUS,
        // CONTACT damage - DamageTypes.STALAGMITE
        Material.POINTED_DRIPSTONE
    )

    /**
     * Body hazards - blocks that damage when standing IN them (body space).
     * Mapped to EntityDamageEvent.DamageCause types.
     */
    @JvmStatic
    val HAZARDOUS_BODY: Set<Material> = EnumSet.of(
        // LAVA damage - DamageTypes.LAVA
        Material.LAVA,
        // FIRE damage - DamageTypes.IN_FIRE
        Material.FIRE,
        Material.SOUL_FIRE,
        // CONTACT damage - DamageTypes.SWEET_BERRY_BUSH (1.0F damage)
        Material.SWEET_BERRY_BUSH,
        // Applies WITHER effect (leads to WITHER damage over time)
        Material.WITHER_ROSE,
        // FREEZE damage - DamageTypes.FREEZE (increases ticksFrozen)
        Material.POWDER_SNOW
    )


    /**
     * Checks if a location is safe for player teleportation.
     *
     * A location is considered safe when:
     * - The world is not null
     * - Y coordinate is within world bounds (when checkYBounds enabled)
     * - Location is inside world border (when checkWorldBorder enabled)
     * - Ground block (y-1) is solid and not hazardous
     * - Body space blocks (y and y+1) are passable and not hazardous
     * - Player bounding box doesn't collide with partial blocks (when checkCollision enabled)
     * - No collidable entities obstruct the location (when checkEntities enabled)
     *
     * ## Thread Safety
     *
     * This is a **synchronous** method that accesses block data directly.
     * - **Folia**: MUST be called from the region thread that owns this location
     * - **Paper**: MUST be called from the main server thread
     *
     * For thread-safe usage from any thread, use [isSafeAsync] instead.
     *
     * @param location The location to check
     * @param options Configuration options for the safety check
     * @return true if the location is safe, false otherwise
     */
    @JvmStatic
    @JvmOverloads
    fun isSafe(location: Location, options: SafetyOptions = SafetyOptions.DEFAULT): Boolean {
        val world = location.world ?: return false // Requirement 1.9

        val x = location.blockX
        val y = location.blockY
        val z = location.blockZ

        // Requirement 1.1: Y bounds check
        if (options.checkYBounds) {
            if (y < world.minHeight || y >= world.maxHeight - 1) {
                return false
            }
        }

        // Requirement 1.2: World border check
        if (options.checkWorldBorder) {
            if (!world.worldBorder.isInside(location)) {
                return false
            }
        }

        val groundBlock = world.getBlockAt(x, y - 1, z)
        val feetBlock = world.getBlockAt(x, y, z)
        val headBlock = world.getBlockAt(x, y + 1, z)

        // Requirement 1.3: Ground must be solid (supports player weight)
        if (!groundBlock.isSolid) {
            return false
        }

        // Requirement 1.4: Ground must not be hazardous
        if (groundBlock.type in HAZARDOUS_GROUND) {
            return false
        }

        // Requirements 1.5, 1.6, 1.8: Body space validation
        if (!isBodySpaceSafe(feetBlock.type, headBlock.type, options)) {
            return false
        }

        // Check passability of body blocks
        if (!isPassable(feetBlock, options) || !isPassable(headBlock, options)) {
            return false
        }

        // Requirement 1.7: Block collision check for partial blocks
        if (options.checkCollision) {
            if (hasBlockCollision(location, world)) {
                return false
            }
        }

        // Requirements 7.1, 7.2, 7.3: Entity collision check
        if (options.checkEntities) {
            if (hasEntityCollision(location, world)) {
                return false
            }
        }

        return true
    }

    /**
     * Checks if a block is passable, with special handling for water.
     *
     * Uses Paper's `Block.isPassable()` API which checks if the block's collision shape is empty.
     *
     * ## Block.isPassable() Behavior Note
     *
     * The Paper API `Block.isPassable()` returns `false` for:
     * - Open doors (even when visually open)
     * - Open fence gates
     * - Open trapdoors
     *
     * This is because these blocks still have collision parts even in their "open" state.
     * This behavior is **acceptable for safe teleportation** since we want to avoid placing
     * players inside any block with collision parts, even if technically passable by walking.
     *
     * Requirement: 1.5
     *
     * @param block The block to check
     * @param options Safety options (for water allowance)
     * @return true if the block is passable (empty collision shape or allowed water)
     */
    private fun isPassable(block: org.bukkit.block.Block, options: SafetyOptions): Boolean {
        val type = block.type

        // Water handling - Requirement 1.8
        if (type == Material.WATER) {
            return options.allowWater
        }

        return block.isPassable
    }

    /**
     * Checks if body space materials are safe.
     *
     * @param feetType Material at feet position
     * @param headType Material at head position
     * @param options Safety options
     * @return true if body space is safe
     */
    private fun isBodySpaceSafe(feetType: Material, headType: Material, options: SafetyOptions): Boolean {
        // Check feet block for hazardous materials (lava, fire, soul fire)
        if (feetType in HAZARDOUS_BODY) {
            return false
        }

        // Check head block for hazardous materials
        if (headType in HAZARDOUS_BODY) {
            return false
        }

        return true
    }


    /**
     * Checks if the player bounding box collides with partial blocks.
     *
     * Uses Block.getCollisionShape() to check for collision with partial blocks
     * like fences, walls, and open trapdoors.
     *
     * Optimization: Only checks the feet and head blocks since we already verified
     * they are passable. This handles edge cases like standing next to a fence.
     *
     * @param location The location to check
     * @param world The world
     * @return true if there is a collision
     */
    private fun hasBlockCollision(location: Location, world: World): Boolean {
        val x = location.x
        val y = location.y
        val z = location.z

        // Create player bounding box centered on location
        val halfWidth = PLAYER_WIDTH / 2.0
        val playerBox = BoundingBox(
            x - halfWidth, y, z - halfWidth,
            x + halfWidth, y + PLAYER_HEIGHT, z + halfWidth
        )

        // Calculate block range that player bounding box might intersect
        // Using floor for min and floor for max since we want inclusive block coordinates
        val minX = kotlin.math.floor(x - halfWidth).toInt()
        val maxX = kotlin.math.floor(x + halfWidth).toInt()
        val minY = kotlin.math.floor(y).toInt()
        val maxY = kotlin.math.floor(y + PLAYER_HEIGHT).toInt()
        val minZ = kotlin.math.floor(z - halfWidth).toInt()
        val maxZ = kotlin.math.floor(z + halfWidth).toInt()

        for (bx in minX..maxX) {
            for (by in minY..maxY) {
                for (bz in minZ..maxZ) {
                    val block = world.getBlockAt(bx, by, bz)

                    // Skip passable blocks - they have empty collision shapes
                    // This is a fast check that avoids VoxelShape operations
                    if (block.isPassable) continue

                    // Get collision shape and check overlap
                    val collisionShape = block.collisionShape
                    for (box in collisionShape.boundingBoxes) {
                        // Offset the collision box to world coordinates
                        val worldBox = box.shift(bx.toDouble(), by.toDouble(), bz.toDouble())
                        if (playerBox.overlaps(worldBox)) {
                            return true
                        }
                    }
                }
            }
        }

        return false
    }

    /**
     * Checks if any collidable entities obstruct the location.
     *
     * Uses World.getNearbyEntities() with a Predicate filter to check for
     * collidable entities. This is used instead of RegionAccessor.hasCollisionsIn() because
     * hasCollisionsIn() only checks "hard colliding" entities via moonrise$isHardColliding():
     * - Boat, AbstractMinecart, Shulker, HappyGhast
     * - Entities where canBeCollidedWith(null) returns true
     *
     * This means hasCollisionsIn() **misses**: Players, ArmorStands, and most LivingEntities.
     * Using getNearbyEntities() catches ALL entity types within the bounding box.
     *
     * @param location The location to check
     * @param world The world
     * @return true if there is an entity collision
     */
    private fun hasEntityCollision(location: Location, world: World): Boolean {
        val x = location.x
        val y = location.y
        val z = location.z

        // Create player bounding box
        val halfWidth = PLAYER_WIDTH / 2.0
        val playerBox = BoundingBox(
            x - halfWidth, y, z - halfWidth,
            x + halfWidth, y + PLAYER_HEIGHT, z + halfWidth
        )

        // Get nearby entities, filtering out non-collidable ones
        // Requirement 7.3: Exclude non-collidable entities
        val collidableEntities = world.getNearbyEntities(playerBox) { entity ->
            !isNonCollidableEntity(entity)
        }

        return collidableEntities.isNotEmpty()
    }

    /**
     * Checks if an entity is non-collidable (should be ignored for collision checks).
     *
     * Requirement 7.3: Exclude items, experience orbs, arrows, and other non-collidable entities.
     *
     * @param entity The entity to check
     * @return true if the entity should be ignored for collision
     */
    private fun isNonCollidableEntity(entity: Entity): Boolean {
        return entity is Item ||
                entity is ExperienceOrb ||
                entity is Arrow ||
                entity is Projectile ||
                entity is AreaEffectCloud ||
                entity is Marker ||
                entity is Display
    }


    /**
     * Asynchronously checks if a location is safe for player teleportation.
     *
     * Uses Paper's `getChunkAtAsync()` to load the chunk before performing the safety check.
     * The safety check is performed in the `thenApply` callback which runs on the appropriate thread.
     *
     * ## Thread Safety
     *
     * This method is **safe to call from any thread** (main thread, async thread, scheduler, etc.).
     *
     * The `getChunkAtAsync()` callback behavior:
     * - **Folia**: Callback runs on the region thread that owns the location's chunk
     * - **Paper**: Callback runs on the main server thread
     *
     * This ensures the actual block access in [isSafe] always occurs on the correct thread.
     *
     * @param location The location to check
     * @param options Configuration options for the safety check
     * @return CompletableFuture that completes with true if safe, false otherwise
     */
    @JvmStatic
    @JvmOverloads
    fun isSafeAsync(location: Location, options: SafetyOptions = SafetyOptions.DEFAULT): CompletableFuture<Boolean> {
        val world = location.world
            ?: return CompletableFuture.completedFuture(false) // Requirement 4.5

        // Load chunk asynchronously, then perform safety check on region thread
        return world.getChunkAtAsync(location).thenApply { _ ->
            // This callback runs on the region thread (Folia) or main thread (Paper)
            isSafe(location, options)
        }
    }
}
