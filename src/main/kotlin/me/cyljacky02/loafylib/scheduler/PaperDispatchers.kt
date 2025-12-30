package me.cyljacky02.loafylib.scheduler

import kotlinx.coroutines.CoroutineDispatcher
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.entity.Entity
import org.bukkit.plugin.Plugin
import kotlin.coroutines.CoroutineContext

/**
 * Paper scheduler-based coroutine dispatchers for Folia compatibility.
 *
 * Provides coroutine dispatchers that integrate with Paper's modern scheduler APIs,
 * ensuring proper thread safety in both standard Paper and Folia environments.
 *
 * ## Dispatcher Selection Guide
 *
 * | Operation | Dispatcher | Example |
 * |-----------|------------|---------|
 * | Database, Redis, HTTP, file I/O | [async] | `withContext(plugin.asyncDispatcher) { db.query() }` |
 * | Broadcasts, world time, weather, console commands | [main] | `withContext(plugin.mainDispatcher) { Bukkit.broadcastMessage() }` |
 * | Player GUI, inventory, teleport, entity state | [entity] | `withContext(plugin.entityDispatcher(player)) { gui.open() }` |
 * | Block placement, chunk operations | [region] | `withContext(plugin.regionDispatcher(loc)) { block.type = X }` |
 *
 * ## Thread Safety Quick Reference
 *
 * ### Safe from ANY thread (Paper's canSendImmediate whitelist):
 * - `player.sendMessage()` / `sendActionBar()` / `sendTitle()`
 * - `player.playSound()` / `stopSound()`
 * - `player.showBossBar()` / `hideBossBar()`
 * - `player.spawnParticle()`
 * - `player.hasPermission()` / `getUniqueId()` / `getName()`
 *
 * ### Requires entity's thread ([entity] dispatcher):
 * - `player.getLocation()` - live position
 * - `player.teleport()` - movement
 * - `player.getInventory().setItem()` - inventory modification
 * - `player.openInventory()` - GUI operations
 * - Any entity state modification
 *
 * ### Requires region's thread ([region] dispatcher):
 * - `block.setType()` - block modification
 * - `world.spawnEntity()` - entity spawning at location
 * - `chunk.getEntities()` - chunk queries
 *
 * ### Requires global region thread ([main] dispatcher):
 * - `Bukkit.broadcastMessage()`
 * - `world.setTime()` / `setStorm()`
 * - `Bukkit.dispatchCommand()` - console commands
 * - `Bukkit.getOnlinePlayers()` - use snapshot: `toList()` or `toTypedArray()`
 *
 * ## Common Patterns
 *
 * ```kotlin
 * // Pattern 1: Async work → notify player (no thread switch needed)
 * pluginScope.launch {
 *     val data = database.fetch(playerId)  // async
 *     player.sendMessage("Loaded: $data")  // safe from async
 * }
 *
 * // Pattern 2: Async work → modify player state (needs entity thread)
 * pluginScope.launch {
 *     val items = database.fetchItems(playerId)
 *     player.withEntityContext(plugin) {
 *         items.forEach { player.inventory.addItem(it) }
 *     }
 * }
 *
 * // Pattern 3: Async work → modify blocks (needs region thread)
 * pluginScope.launch {
 *     val blocks = database.fetchBlocks()
 *     blocks.forEach { (loc, type) ->
 *         loc.withRegionContext(plugin) {
 *             loc.block.type = type
 *         }
 *     }
 * }
 *
 * // Pattern 4: Shared plugin state (use Mutex, not a dedicated thread)
 * private val mutex = Mutex()
 * private val cache = mutableMapOf<UUID, Data>()
 *
 * suspend fun updateCache(uuid: UUID, data: Data) {
 *     mutex.withLock { cache[uuid] = data }
 * }
 * ```
 *
 * ## Why No "Plugin Main Thread"?
 *
 * Some libraries provide a dedicated thread per plugin. We intentionally don't because:
 * 1. It's a legacy pattern workaround, not a best practice
 * 2. Kotlin's [Mutex] and [Channel] are better for state synchronization
 * 3. Wastes resources (idle thread per plugin)
 * 4. The 4 dispatchers above cover all Minecraft operations
 *
 * For shared state protection, use [kotlinx.coroutines.sync.Mutex] or [java.util.concurrent.ConcurrentHashMap].
 *
 * @see <a href="https://docs.papermc.io/folia/reference/region-threading">Folia Region Threading</a>
 * @see <a href="https://docs.papermc.io/paper/dev/scheduler">Paper Scheduler API</a>
 */
object PaperDispatchers {

    /**
     * Creates a dispatcher that executes coroutines asynchronously.
     *
     * Uses Paper's [AsyncScheduler][io.papermc.paper.threadedregions.scheduler.AsyncScheduler]
     * for Folia compatibility.
     *
     * Use for:
     * - Database operations
     * - File I/O
     * - HTTP requests
     * - Any work that doesn't touch Bukkit API
     *
     * @param plugin The plugin instance
     * @return An async dispatcher
     */
    fun async(plugin: Plugin): CoroutineDispatcher = AsyncDispatcher(plugin)

    /**
     * Creates a dispatcher that executes on the global region thread.
     *
     * Uses Paper's [GlobalRegionScheduler][io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler].
     *
     * Use for:
     * - Server-wide broadcasts
     * - World time/weather changes
     * - Console command execution
     * - Operations not tied to specific entity or region
     *
     * Note: In Folia, this is the "global region" thread, not a single main thread.
     * For entity-specific operations, use [entity] dispatcher instead.
     *
     * @param plugin The plugin instance
     * @return A global region dispatcher
     */
    fun main(plugin: Plugin): CoroutineDispatcher = MainDispatcher(plugin)

    /**
     * Creates a dispatcher that executes on the thread owning the entity.
     *
     * Uses Paper's [EntityScheduler][io.papermc.paper.threadedregions.scheduler.EntityScheduler]
     * for Folia compatibility.
     *
     * Use for:
     * - GUI operations (InvUI, etc.)
     * - Player state modifications
     * - Entity teleportation
     * - Any operation requiring the entity's tick thread
     *
     * The dispatcher "follows" the entity - if it teleports to another region,
     * tasks execute on the new region's thread.
     *
     * If the entity is removed before execution, tasks are silently dropped.
     *
     * @param plugin The plugin instance
     * @param entity The entity to bind execution to
     * @return An entity-bound dispatcher
     */
    fun entity(plugin: Plugin, entity: Entity): CoroutineDispatcher = EntityDispatcher(plugin, entity)

    /**
     * Creates a dispatcher that executes on the thread owning a specific region.
     *
     * Uses Paper's [RegionScheduler][io.papermc.paper.threadedregions.scheduler.RegionScheduler]
     * for Folia compatibility.
     *
     * Use for:
     * - Block modifications at a specific location
     * - Chunk operations
     * - Spawning entities at a location
     *
     * Note: This does NOT follow entities. For entity-bound execution, use [entity] instead.
     *
     * @param plugin The plugin instance
     * @param world The world containing the region
     * @param chunkX The chunk X coordinate
     * @param chunkZ The chunk Z coordinate
     * @return A region-bound dispatcher
     */
    fun region(plugin: Plugin, world: World, chunkX: Int, chunkZ: Int): CoroutineDispatcher =
        RegionDispatcher(plugin, world, chunkX, chunkZ)

    /**
     * Creates a dispatcher that executes on the thread owning a specific location.
     *
     * Convenience overload that extracts chunk coordinates from location.
     *
     * @param plugin The plugin instance
     * @param location The location to bind execution to
     * @return A region-bound dispatcher
     * @throws IllegalArgumentException if location has no world
     */
    fun region(plugin: Plugin, location: Location): CoroutineDispatcher {
        val world = location.world ?: throw IllegalArgumentException("Location must have a world")
        return RegionDispatcher(plugin, world, location.blockX shr 4, location.blockZ shr 4)
    }
}


// =============================================================================
// Dispatcher Implementations
// =============================================================================

/**
 * Dispatcher using Paper's AsyncScheduler.
 * Folia-compatible alternative to Dispatchers.IO.
 * 
 * Handles plugin disable gracefully by running tasks directly when the plugin
 * is no longer enabled, avoiding IllegalPluginAccessException during shutdown.
 */
internal class AsyncDispatcher(private val plugin: Plugin) : CoroutineDispatcher() {

    override fun dispatch(context: CoroutineContext, block: Runnable) {
        // When plugin is disabled, Paper rejects new task scheduling.
        // Run directly on current thread to allow coroutine cancellation to complete.
        if (!plugin.isEnabled) {
            block.run()
            return
        }
        Bukkit.getAsyncScheduler().runNow(plugin) { block.run() }
    }

    override fun toString(): String = "PaperAsyncDispatcher"
}

/**
 * Dispatcher using Paper's GlobalRegionScheduler.
 *
 * Note: We intentionally do NOT override isDispatchNeeded() because:
 * - In Folia, Bukkit.isPrimaryThread() returns true for ANY tick thread
 * - This would cause incorrect behavior when called from a region tick thread
 * - GlobalRegionScheduler.execute() handles optimization internally
 * 
 * Handles plugin disable gracefully by running tasks directly when the plugin
 * is no longer enabled, avoiding IllegalPluginAccessException during shutdown.
 */
internal class MainDispatcher(private val plugin: Plugin) : CoroutineDispatcher() {

    override fun dispatch(context: CoroutineContext, block: Runnable) {
        if (!plugin.isEnabled) {
            block.run()
            return
        }
        Bukkit.getGlobalRegionScheduler().execute(plugin, block)
    }

    override fun toString(): String = "PaperMainDispatcher"
}

/**
 * Dispatcher using Paper's EntityScheduler.
 * Essential for Folia where entities can be in different regions.
 *
 * If the entity is removed before execution, the task is silently dropped.
 * Handles plugin disable gracefully by running tasks directly when the plugin
 * is no longer enabled, avoiding IllegalPluginAccessException during shutdown.
 */
internal class EntityDispatcher(
    private val plugin: Plugin,
    private val entity: Entity
) : CoroutineDispatcher() {

    override fun dispatch(context: CoroutineContext, block: Runnable) {
        if (!plugin.isEnabled) {
            block.run()
            return
        }
        // retired callback is null - silently drop if entity is gone
        entity.scheduler.run(plugin, { block.run() }, null)
    }

    override fun toString(): String = "PaperEntityDispatcher(${entity.uniqueId})"
}

/**
 * Dispatcher using Paper's RegionScheduler.
 * Location-bound, does not follow entities.
 * 
 * Handles plugin disable gracefully by running tasks directly when the plugin
 * is no longer enabled, avoiding IllegalPluginAccessException during shutdown.
 */
internal class RegionDispatcher(
    private val plugin: Plugin,
    private val world: World,
    private val chunkX: Int,
    private val chunkZ: Int
) : CoroutineDispatcher() {

    override fun dispatch(context: CoroutineContext, block: Runnable) {
        if (!plugin.isEnabled) {
            block.run()
            return
        }
        Bukkit.getRegionScheduler().execute(plugin, world, chunkX, chunkZ, block)
    }

    override fun toString(): String = "PaperRegionDispatcher(${world.name}, $chunkX, $chunkZ)"
}
