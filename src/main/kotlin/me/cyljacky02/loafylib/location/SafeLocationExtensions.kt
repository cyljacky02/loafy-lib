package me.cyljacky02.loafylib.location

import kotlinx.coroutines.future.await
import org.bukkit.Location
import java.util.concurrent.CompletableFuture

/**
 * Kotlin extension functions for safe location detection on [Location].
 *
 * Provides idiomatic Kotlin API for checking location safety and finding
 * nearby safe locations. All functions delegate to [SafeLocation] and
 * [SafeLocationSearch] utility objects.
 *
 * Example usage:
 * ```kotlin
 * // Synchronous
 * if (location.isSafe()) { ... }
 * val safe = location.findSafeNearby(radiusXZ = 5)
 *
 * // Async (CompletableFuture)
 * location.isSafeAsync().thenAccept { isSafe -> ... }
 *
 * // Coroutine-friendly
 * if (location.isSafeSuspend()) { ... }
 * val safe = location.findSafeNearbySuspend()
 * ```
 *
 * @see SafeLocation for core safety check logic
 * @see SafeLocationSearch for search algorithms
 * @see SafetyOptions for configuration options
 */

/**
 * Checks if this location is safe for player teleportation.
 *
 * @param options Configuration options for the safety check (default: [SafetyOptions.DEFAULT])
 * @return true if the location is safe, false otherwise
 * @see SafeLocation.isSafe
 */
fun Location.isSafe(options: SafetyOptions = SafetyOptions.DEFAULT): Boolean =
    SafeLocation.isSafe(this, options)

/**
 * Asynchronously checks if this location is safe for player teleportation.
 *
 * Uses Paper's getChunkAtAsync() to load the chunk before performing the safety check.
 * Safe to call from any thread.
 *
 * @param options Configuration options for the safety check (default: [SafetyOptions.DEFAULT])
 * @return CompletableFuture that completes with true if safe, false otherwise
 * @see SafeLocation.isSafeAsync
 */
fun Location.isSafeAsync(options: SafetyOptions = SafetyOptions.DEFAULT): CompletableFuture<Boolean> =
    SafeLocation.isSafeAsync(this, options)

/**
 * Finds the nearest safe location within the specified radius.
 *
 * @param radiusXZ Maximum horizontal search radius (default: 3)
 * @param radiusY Maximum vertical search radius (default: 5)
 * @param options Configuration options for safety checks (default: [SafetyOptions.DEFAULT])
 * @param profile Search profile for vertical scanning strategy (default: [SearchProfile.AUTO])
 * @return The nearest safe location, or null if none found
 * @see SafeLocationSearch.findNearest
 */
fun Location.findSafeNearby(
    radiusXZ: Int = 3,
    radiusY: Int = 5,
    options: SafetyOptions = SafetyOptions.DEFAULT,
    profile: SearchProfile = SearchProfile.AUTO
): Location? = SafeLocationSearch.findNearest(this, radiusXZ, radiusY, options, profile)

/**
 * Asynchronously finds the nearest safe location within the specified radius.
 *
 * Uses Paper's getChunkAtAsync() to load the chunk before performing the search.
 * Safe to call from any thread.
 *
 * @param radiusXZ Maximum horizontal search radius (default: 3)
 * @param radiusY Maximum vertical search radius (default: 5)
 * @param options Configuration options for safety checks (default: [SafetyOptions.DEFAULT])
 * @param profile Search profile for vertical scanning strategy (default: [SearchProfile.AUTO])
 * @return CompletableFuture that completes with the nearest safe location, or null if none found
 * @see SafeLocationSearch.findNearestAsync
 */
fun Location.findSafeNearbyAsync(
    radiusXZ: Int = 3,
    radiusY: Int = 5,
    options: SafetyOptions = SafetyOptions.DEFAULT,
    profile: SearchProfile = SearchProfile.AUTO
): CompletableFuture<Location?> = SafeLocationSearch.findNearestAsync(this, radiusXZ, radiusY, options, profile)

/**
 * Coroutine-friendly version of [isSafe] that suspends until the check completes.
 *
 * Uses [isSafeAsync] internally with [CompletableFuture.await].
 *
 * @param options Configuration options for the safety check (default: [SafetyOptions.DEFAULT])
 * @return true if the location is safe, false otherwise
 */
suspend fun Location.isSafeSuspend(options: SafetyOptions = SafetyOptions.DEFAULT): Boolean =
    isSafeAsync(options).await()

/**
 * Coroutine-friendly version of [findSafeNearby] that suspends until the search completes.
 *
 * Uses [findSafeNearbyAsync] internally with [CompletableFuture.await].
 *
 * @param radiusXZ Maximum horizontal search radius (default: 3)
 * @param radiusY Maximum vertical search radius (default: 5)
 * @param options Configuration options for safety checks (default: [SafetyOptions.DEFAULT])
 * @param profile Search profile for vertical scanning strategy (default: [SearchProfile.AUTO])
 * @return The nearest safe location, or null if none found
 */
suspend fun Location.findSafeNearbySuspend(
    radiusXZ: Int = 3,
    radiusY: Int = 5,
    options: SafetyOptions = SafetyOptions.DEFAULT,
    profile: SearchProfile = SearchProfile.AUTO
): Location? = findSafeNearbyAsync(radiusXZ, radiusY, options, profile).await()

/**
 * Asynchronously finds a safe location near the surface at the given X,Z coordinates.
 *
 * Uses Paper's HeightMap API to find the surface, then searches for a safe location nearby.
 * Safe to call from any thread.
 *
 * @param x The X coordinate
 * @param z The Z coordinate
 * @param radiusXZ Maximum horizontal search radius (default: 3)
 * @param radiusY Maximum vertical search radius (default: 5)
 * @param options Configuration options for safety checks (default: [SafetyOptions.DEFAULT])
 * @return CompletableFuture that completes with a safe surface location, or null if none found
 * @see SafeLocationSearch.findSafeSurfaceAsync
 */
fun org.bukkit.World.findSafeSurfaceAsync(
    x: Int,
    z: Int,
    radiusXZ: Int = 3,
    radiusY: Int = 5,
    options: SafetyOptions = SafetyOptions.DEFAULT
): CompletableFuture<Location?> = SafeLocationSearch.findSafeSurfaceAsync(this, x, z, radiusXZ, radiusY, options)

/**
 * Coroutine-friendly version of [findSafeSurfaceAsync] that suspends until the search completes.
 *
 * Uses [findSafeSurfaceAsync] internally with [CompletableFuture.await].
 *
 * @param x The X coordinate
 * @param z The Z coordinate
 * @param radiusXZ Maximum horizontal search radius (default: 3)
 * @param radiusY Maximum vertical search radius (default: 5)
 * @param options Configuration options for safety checks (default: [SafetyOptions.DEFAULT])
 * @return A safe surface location, or null if none found
 */
suspend fun org.bukkit.World.findSafeSurfaceSuspend(
    x: Int,
    z: Int,
    radiusXZ: Int = 3,
    radiusY: Int = 5,
    options: SafetyOptions = SafetyOptions.DEFAULT
): Location? = findSafeSurfaceAsync(x, z, radiusXZ, radiusY, options).await()
