package me.cyljacky02.loafylib.location

import org.bukkit.Location
import org.bukkit.World
import java.util.concurrent.CompletableFuture

/**
 * Utility object for finding safe locations near a given origin.
 *
 * Implements search patterns sorted by distance from origin:
 * - **ALTERNATING**: Checks origin Y first, then alternates up/down (0, 1, -1, 2, -2, ...)
 * - **MIDDLE_OUT**: Starts from middle of Y range, optimal for cave environments like Nether
 * - **AUTO**: Automatically selects optimal strategy based on world environment
 *
 * ## Thread Safety
 *
 * **Synchronous methods** ([findNearest]):
 * - MUST be called from the region thread that owns the origin location (Folia requirement)
 * - On Paper (non-Folia), this means the main server thread
 *
 * **Asynchronous methods** ([findNearestAsync]):
 * - Safe to call from any thread
 * - Uses `getChunkAtAsync()` to load the origin chunk before searching
 *
 * ## Multi-Chunk Search Considerations (Folia-Critical)
 *
 * **Important**: Large search radii (>16 blocks) may span multiple chunks.
 *
 * - The async method ([findNearestAsync]) only loads the **origin chunk** before searching
 * - If the search extends into neighboring chunks:
 *   - **Folia**: Will cause `IllegalStateException` if accessing blocks owned by a different
 *     region thread. Folia enforces strict thread context checks via `TickThread.isTickThreadFor()`.
 *     Regions tick in parallel and do NOT share data - cross-region access causes data corruption.
 *   - **Paper**: May trigger synchronous chunk loading (performance impact)
 *
 * **Recommendations for large radii**:
 * - Keep `radiusXZ` ≤ 16 for predictable single-chunk behavior
 * - For larger searches on Folia, use `RegionScheduler` to ensure correct thread context
 * - Consider pre-loading relevant chunks or implementing chunk-aware search at plugin level
 * - The default radius (3 blocks) is safe for single-chunk operations
 *
 * ## Nether-Specific Behavior
 *
 * When using [SearchProfile.AUTO] (default), the search automatically:
 * - Detects Nether worlds via `World.Environment.NETHER`
 * - Switches to [SearchProfile.MIDDLE_OUT] for optimal cave searching
 * - Caps maximum Y at 126 to avoid bedrock ceiling (Y=127)
 *
 * @see SafeLocation for safety check logic
 * @see SafetyOptions for configuration options
 * @see SearchProfile for search strategy options
 */
object SafeLocationSearch {

    /** Maximum search radius for pre-computed spiral pattern */
    private const val MAX_RADIUS = 16

    /**
     * Pre-computed spiral pattern as Array<IntArray> for memory efficiency.
     * Each IntArray contains [dx, dz] offsets sorted by distance from origin.
     *
     * Using Array<IntArray> instead of List<IntArray> for:
     * - Contiguous memory layout
     * - Direct array access (faster iteration)
     * - No boxing overhead
     */
    private val SPIRAL_PATTERN: Array<IntArray> by lazy {
        buildList {
            for (dx in -MAX_RADIUS..MAX_RADIUS) {
                for (dz in -MAX_RADIUS..MAX_RADIUS) {
                    add(intArrayOf(dx, dz))
                }
            }
        }.sortedBy { (dx, dz) -> dx * dx + dz * dz }
            .toTypedArray()
    }

    /**
     * Generates a vertical search sequence that checks origin Y first,
     * then alternates up/down: 0, 1, -1, 2, -2, ...
     *
     * @param radiusY Maximum vertical distance to search
     * @return Sequence of Y offsets in search order
     */
    private fun generateAlternatingSequence(radiusY: Int): Sequence<Int> = sequence {
        yield(0) // Check origin Y first
        for (i in 1..radiusY) {
            yield(i)   // Check above
            yield(-i)  // Check below
        }
    }

    /**
     * Generates a middle-out vertical search sequence.
     * Starts from the middle of the Y range and alternates up/down.
     *
     * @param startY The Y coordinate to start from (middle of range)
     * @param minY Minimum Y bound
     * @param maxY Maximum Y bound
     * @return Sequence of absolute Y coordinates in search order
     */
    private fun generateMiddleOutSequence(startY: Int, minY: Int, maxY: Int): Sequence<Int> = sequence {
        yield(startY)
        var offset = 1
        while (true) {
            val up = startY + offset
            val down = startY - offset
            val yieldedAny = (up <= maxY) || (down >= minY)
            if (!yieldedAny) break

            if (up <= maxY) yield(up)
            if (down >= minY) yield(down)
            offset++
        }
    }

    /**
     * Finds the nearest safe location within the specified radius.
     *
     * Search algorithm:
     * 1. Iterate through spiral pattern (XZ plane) sorted by distance
     * 2. For each XZ position, iterate vertical sequence based on [profile]
     * 3. Skip locations outside world Y bounds
     * 4. Return first safe location found (centered on block)
     *
     * The returned location:
     * - Has X and Z centered on the block (fractional = 0.5)
     * - Preserves original yaw and pitch from origin
     * - Returns null if no safe location found within radius
     *
     * ## Thread Safety
     *
     * This is a **synchronous** method that accesses block data directly.
     * - **Folia**: MUST be called from the region thread that owns the origin location
     * - **Paper**: MUST be called from the main server thread
     *
     * For thread-safe usage from any thread, use [findNearestAsync] instead.
     *
     * ## Nether Optimization
     *
     * When [profile] is [SearchProfile.AUTO] and the world is Nether:
     * - Automatically uses [SearchProfile.MIDDLE_OUT] for optimal cave searching
     * - Caps maximum Y at 126 to avoid bedrock ceiling
     *
     * @param origin The starting location for the search
     * @param radiusXZ Maximum horizontal search radius (default: 3). Values >16 may span multiple chunks.
     * @param radiusY Maximum vertical search radius (default: 5)
     * @param options Configuration options for safety checks
     * @param profile Search profile for vertical scanning strategy (default: AUTO)
     * @return The nearest safe location, or null if none found
     */
    @JvmStatic
    @JvmOverloads
    fun findNearest(
        origin: Location,
        radiusXZ: Int = 3,
        radiusY: Int = 5,
        options: SafetyOptions = SafetyOptions.DEFAULT,
        profile: SearchProfile = SearchProfile.AUTO
    ): Location? {
        val world = origin.world ?: return null

        val originX = origin.blockX
        val originY = origin.blockY
        val originZ = origin.blockZ
        val minHeight = world.minHeight

        // Resolve profile and get effective bounds
        val resolvedProfile = SearchProfile.resolve(profile, world)
        val effectiveMaxY = SearchProfile.getEffectiveMaxY(world, profile)

        // Pre-compute squared radius for distance check
        val radiusXZSquared = radiusXZ * radiusXZ

        // Generate vertical sequence based on profile
        val verticalSequence: (Int, Int) -> Sequence<Int> = when (resolvedProfile) {
            SearchProfile.MIDDLE_OUT -> { checkX, checkZ ->
                val startY = SearchProfile.getMiddleOutStartY(world, originY)
                generateMiddleOutSequence(startY, minHeight, effectiveMaxY)
            }
            else -> { _, _ ->
                generateAlternatingSequence(radiusY).map { dy -> originY + dy }
            }
        }

        // Iterate spiral pattern within radiusXZ
        for (offset in SPIRAL_PATTERN) {
            val dx = offset[0]
            val dz = offset[1]

            // Skip if outside horizontal radius
            if (dx * dx + dz * dz > radiusXZSquared) continue

            val checkX = originX + dx
            val checkZ = originZ + dz

            // Iterate vertical sequence
            for (checkY in verticalSequence(checkX, checkZ)) {
                // Skip locations outside Y bounds
                if (checkY < minHeight || checkY > effectiveMaxY) continue

                // For alternating profile, also check radiusY constraint
                if (resolvedProfile != SearchProfile.MIDDLE_OUT) {
                    if (kotlin.math.abs(checkY - originY) > radiusY) continue
                }

                // Create location for safety check
                val checkLocation = Location(world, checkX.toDouble(), checkY.toDouble(), checkZ.toDouble())

                if (SafeLocation.isSafe(checkLocation, options)) {
                    // Center on block (x+0.5, z+0.5) and preserve original yaw and pitch
                    return Location(
                        world,
                        checkX + 0.5,
                        checkY.toDouble(),
                        checkZ + 0.5,
                        origin.yaw,
                        origin.pitch
                    )
                }
            }
        }

        return null
    }

    /**
     * Asynchronously finds the nearest safe location within the specified radius.
     *
     * Uses Paper's `getChunkAtAsync()` to load the origin chunk before performing the search.
     * The search is performed in the `thenApply` callback which runs on the appropriate thread.
     *
     * ## Thread Safety
     *
     * This method is **safe to call from any thread** (main thread, async thread, scheduler, etc.).
     *
     * The `getChunkAtAsync()` callback behavior:
     * - **Folia**: Callback runs on the region thread that owns the origin chunk
     * - **Paper**: Callback runs on the main server thread (always synchronously)
     *
     * ## Multi-Chunk Limitation (Folia-Critical)
     *
     * **Important**: This method only loads the **origin chunk** before searching.
     *
     * If `radiusXZ` > 16, the search may extend into neighboring chunks. On Folia, this will
     * cause `IllegalStateException` if those chunks are owned by a different region thread.
     * Folia's `TickThread.isTickThreadFor()` enforces strict region ownership.
     *
     * For large search radii on Folia, consider:
     * - Using `RegionScheduler` to schedule tasks on the correct region threads
     * - Pre-loading all relevant chunks before searching
     * - Implementing chunk-aware search logic at the plugin level
     *
     * ## Nether Optimization
     *
     * When [profile] is [SearchProfile.AUTO] and the world is Nether:
     * - Automatically uses [SearchProfile.MIDDLE_OUT] for optimal cave searching
     * - Caps maximum Y at 126 to avoid bedrock ceiling
     *
     * @param origin The starting location for the search
     * @param radiusXZ Maximum horizontal search radius (default: 3). Values >16 may span multiple chunks.
     * @param radiusY Maximum vertical search radius (default: 5)
     * @param options Configuration options for safety checks
     * @param profile Search profile for vertical scanning strategy (default: AUTO)
     * @return CompletableFuture that completes with the nearest safe location, or null if none found
     */
    @JvmStatic
    @JvmOverloads
    fun findNearestAsync(
        origin: Location,
        radiusXZ: Int = 3,
        radiusY: Int = 5,
        options: SafetyOptions = SafetyOptions.DEFAULT,
        profile: SearchProfile = SearchProfile.AUTO
    ): CompletableFuture<Location?> {
        val world = origin.world
            ?: return CompletableFuture.completedFuture(null)

        // Load chunk asynchronously, then perform search on region thread
        return world.getChunkAtAsync(origin).thenApply { _ ->
            // This callback runs on the region thread (Folia) or main thread (Paper)
            findNearest(origin, radiusXZ, radiusY, options, profile)
        }
    }
}
