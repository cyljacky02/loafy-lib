package me.cyljacky02.loafylib.scheduler

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.withContext
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import java.util.logging.Level
import kotlin.coroutines.cancellation.CancellationException

// =============================================================================
// Top-Level Dispatchers (Plugin-Independent)
// =============================================================================

/**
 * Global async dispatcher for background operations.
 *
 * Uses [kotlinx.coroutines.Dispatchers.IO] which is optimized for blocking I/O
 * operations like database queries, file I/O, and network requests.
 *
 * This is Folia-compatible since async operations don't touch game state.
 * The behavior is equivalent to Paper's AsyncScheduler - both use thread pools
 * for non-blocking background work.
 *
 * ```kotlin
 * // In plugin class
 * val pluginScope by lazy { CoroutineScope(asyncDispatcher + SupervisorJob()) }
 *
 * // Usage
 * pluginScope.launch {
 *     val data = database.fetch()  // runs on IO thread pool
 *     player.sendMessage("Done!")  // safe from async (canSendImmediate)
 * }
 * ```
 *
 * ## Why Dispatchers.IO instead of Paper's AsyncScheduler?
 *
 * 1. **No plugin instance required** - Can be used at class initialization
 * 2. **Same thread pool behavior** - Both use cached thread pools for I/O
 * 3. **Better coroutine integration** - Native dispatcher, no wrapper overhead
 * 4. **Folia-safe** - Async work doesn't touch game state anyway
 *
 * For plugin-bound async tasks with better profiling/debugging in Paper's
 * timings, use [Plugin.asyncDispatcher] extension property instead.
 */
val asyncDispatcher: CoroutineDispatcher = kotlinx.coroutines.Dispatchers.IO

/**
 * Kotlin coroutine extensions for Paper's scheduler APIs.
 *
 * Provides idiomatic Kotlin integration with Paper's Folia-compatible schedulers.
 *
 * ## Quick Start
 *
 * ```kotlin
 * class MyPlugin : JavaPlugin() {
 *     val pluginScope by lazy { createPluginScope() }
 *
 *     override fun onDisable() {
 *         pluginScope.cancel()
 *     }
 * }
 * ```
 *
 * ## Common Patterns
 *
 * ```kotlin
 * // Async database operation - sendMessage is safe from async
 * pluginScope.launch {
 *     val data = fetchFromDatabase()
 *     player.sendMessage("Loaded: $data")  // No thread switch needed!
 * }
 *
 * // Player GUI operation - requires entity thread
 * pluginScope.launch {
 *     val result = doAsyncWork()
 *     player.withPlayerContext(plugin) {
 *         gui.open(player)
 *     }
 * }
 *
 * // Block modification - requires region thread
 * pluginScope.launch {
 *     location.withRegionContext(plugin) {
 *         location.block.type = Material.STONE
 *     }
 * }
 *
 * // Global broadcast - requires global region thread
 * pluginScope.launch {
 *     plugin.withMainContext {
 *         Bukkit.broadcastMessage("Server message!")
 *     }
 * }
 * ```
 *
 * @see PaperDispatchers for detailed thread safety documentation
 */

// =============================================================================
// Plugin Extension Properties
// =============================================================================

/**
 * Gets an async dispatcher using Paper's AsyncScheduler.
 *
 * Use instead of `Dispatchers.IO` for Folia compatibility.
 */
val Plugin.asyncDispatcher: CoroutineDispatcher
    get() = PaperDispatchers.async(this)

/**
 * Gets a global region thread dispatcher.
 *
 * Use for server-wide operations not tied to specific entities or regions.
 */
val Plugin.mainDispatcher: CoroutineDispatcher
    get() = PaperDispatchers.main(this)

// =============================================================================
// Plugin Extension Functions
// =============================================================================

/**
 * Creates an entity-bound dispatcher.
 *
 * Use for player-specific operations like GUI updates.
 *
 * @param entity The entity to bind execution to
 * @return An entity-bound dispatcher
 */
fun Plugin.entityDispatcher(entity: Entity): CoroutineDispatcher =
    PaperDispatchers.entity(this, entity)

/**
 * Creates a region-bound dispatcher for chunk coordinates.
 *
 * Use for block/chunk operations at a specific location.
 *
 * @param world The world containing the region
 * @param chunkX The chunk X coordinate
 * @param chunkZ The chunk Z coordinate
 * @return A region-bound dispatcher
 */
fun Plugin.regionDispatcher(world: World, chunkX: Int, chunkZ: Int): CoroutineDispatcher =
    PaperDispatchers.region(this, world, chunkX, chunkZ)

/**
 * Creates a region-bound dispatcher for a location.
 *
 * Convenience overload that extracts chunk coordinates automatically.
 *
 * @param location The location to bind execution to
 * @return A region-bound dispatcher
 * @throws IllegalArgumentException if location has no world
 */
fun Plugin.regionDispatcher(location: Location): CoroutineDispatcher =
    PaperDispatchers.region(this, location)

/**
 * Creates a plugin-scoped CoroutineScope with proper exception handling.
 *
 * Features:
 * - **SupervisorJob**: Child coroutines can fail independently without cancelling siblings
 * - **Exception Handler**: Logs uncaught exceptions to the plugin's logger (except CancellationException)
 * - **Async Dispatcher**: Uses Dispatchers.IO for background work
 *
 * This scope should be cancelled in onDisable() using [cancelPluginScope].
 *
 * ```kotlin
 * class MyPlugin : JavaPlugin() {
 *     val pluginScope by lazy { createPluginScope() }
 *
 *     override fun onDisable() {
 *         cancelPluginScope(pluginScope)
 *     }
 * }
 * ```
 *
 * @return A new CoroutineScope with SupervisorJob, exception handler, and async dispatcher
 */
fun Plugin.createPluginScope(): CoroutineScope {
    val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        // Don't log CancellationException - it's expected during shutdown
        if (throwable !is CancellationException) {
            logger.log(
                Level.SEVERE,
                "Uncaught exception in coroutine for plugin ${name}",
                throwable
            )
        }
    }
    
    return CoroutineScope(exceptionHandler + SupervisorJob() + asyncDispatcher)
}

/**
 * Creates a plugin-scoped CoroutineScope without a plugin reference.
 *
 * Use this when you don't have access to a Plugin instance.
 * Exceptions are logged to stderr instead of a plugin logger.
 *
 * @return A new CoroutineScope with SupervisorJob, exception handler, and async dispatcher
 */
fun createPluginScope(): CoroutineScope {
    val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        if (throwable !is CancellationException) {
            System.err.println("Uncaught exception in coroutine: ${throwable.message}")
            throwable.printStackTrace()
        }
    }
    
    return CoroutineScope(exceptionHandler + SupervisorJob() + asyncDispatcher)
}

/**
 * Gracefully cancels a plugin scope.
 *
 * This follows MCCoroutine's best practice for graceful shutdown:
 * 1. First cancels all child coroutines via [cancelChildren]
 * 2. Then cancels the scope itself via [cancel]
 *
 * This two-step process ensures that:
 * - Child coroutines receive cancellation signals and can clean up
 * - The scope is properly marked as cancelled
 * - No new coroutines can be launched after cancellation
 *
 * ```kotlin
 * class MyPlugin : JavaPlugin() {
 *     val pluginScope by lazy { createPluginScope() }
 *
 *     override fun onDisable() {
 *         cancelPluginScope(pluginScope)
 *     }
 * }
 * ```
 *
 * @param scope The coroutine scope to cancel
 */
fun cancelPluginScope(scope: CoroutineScope) {
    scope.coroutineContext.cancelChildren()
    scope.cancel()
}

/**
 * Extension function for gracefully cancelling a plugin scope.
 *
 * @see cancelPluginScope
 */
fun CoroutineScope.cancelGracefully() {
    coroutineContext.cancelChildren()
    cancel()
}


// =============================================================================
// Context Switching Functions
// =============================================================================

/**
 * Executes a block on the global region thread.
 *
 * Use for server-wide operations like broadcasts, world time changes, etc.
 *
 * ```kotlin
 * pluginScope.launch {
 *     val data = fetchData()  // async
 *     plugin.withMainContext {
 *         Bukkit.broadcastMessage("Result: $data")
 *     }
 * }
 * ```
 *
 * @param block The suspend block to execute on the main thread
 * @return The result of the block
 */
suspend inline fun <T> Plugin.withMainContext(
    crossinline block: suspend CoroutineScope.() -> T
): T = withContext(mainDispatcher) { block() }

/**
 * Executes a block asynchronously using Paper's AsyncScheduler.
 *
 * Useful when you need to explicitly switch to async from a tick thread.
 *
 * ```kotlin
 * // From a command handler (tick thread)
 * plugin.withAsyncContext {
 *     val result = database.query(...)
 * }
 * ```
 *
 * @param block The suspend block to execute asynchronously
 * @return The result of the block
 */
suspend inline fun <T> Plugin.withAsyncContext(
    crossinline block: suspend CoroutineScope.() -> T
): T = withContext(asyncDispatcher) { block() }

/**
 * Executes a block on the entity's owning thread.
 *
 * Essential for entity state modifications in Folia.
 *
 * ```kotlin
 * pluginScope.launch {
 *     val data = fetchData()
 *     entity.withEntityContext(plugin) {
 *         entity.teleport(newLocation)
 *     }
 * }
 * ```
 *
 * @param plugin The plugin instance
 * @param block The suspend block to execute on the entity's thread
 * @return The result of the block
 */
suspend inline fun <T> Entity.withEntityContext(
    plugin: Plugin,
    crossinline block: suspend CoroutineScope.() -> T
): T = withContext(PaperDispatchers.entity(plugin, this)) { block() }

/**
 * Executes a block on the player's owning thread.
 *
 * Convenience wrapper for player-specific operations like GUI updates.
 *
 * ```kotlin
 * pluginScope.launch {
 *     tagService.selectTag(player.uniqueId, tagId)
 *     player.sendMessage("Tag selected!")  // Safe from async
 *     player.withPlayerContext(plugin) {
 *         gui.refresh()  // Requires player's thread
 *     }
 * }
 * ```
 *
 * @param plugin The plugin instance
 * @param block The suspend block to execute on the player's thread
 * @return The result of the block
 */
suspend inline fun <T> Player.withPlayerContext(
    plugin: Plugin,
    crossinline block: suspend CoroutineScope.() -> T
): T = withEntityContext(plugin, block)

/**
 * Executes a block on the thread owning the specified region.
 *
 * Use for block/chunk modifications at a specific location.
 *
 * ```kotlin
 * pluginScope.launch {
 *     location.withRegionContext(plugin) {
 *         location.block.type = Material.STONE
 *     }
 * }
 * ```
 *
 * @param plugin The plugin instance
 * @param block The suspend block to execute on the region's thread
 * @return The result of the block
 * @throws IllegalArgumentException if location has no world
 */
suspend inline fun <T> Location.withRegionContext(
    plugin: Plugin,
    crossinline block: suspend CoroutineScope.() -> T
): T = withContext(PaperDispatchers.region(plugin, this)) { block() }
