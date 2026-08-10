package me.cyljacky02.loafylib.plugin

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import me.cyljacky02.loafylib.event.SuspendingEventService
import me.cyljacky02.loafylib.scheduler.cancelGracefully
import me.cyljacky02.loafylib.scheduler.createPluginScope
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.plugin.java.JavaPlugin
import kotlin.reflect.KClass

/**
 * Abstract base class for plugins using LoafyLib infrastructure.
 *
 * Provides common lifecycle management including:
 * - Plugin-scoped coroutine scope with automatic cancellation
 * - Component registration and dependency-ordered initialization
 * - Automatic Bukkit listener registration for components implementing [Listener]
 * - Graceful error handling during startup
 *
 * ## Usage Example
 *
 * ```kotlin
 * class MyPlugin : LoafyPlugin() {
 *
 *     override fun components(): List<PluginComponent> {
 *         val configManager = ConfigManager(dataFolder.toPath(), logger)
 *         val config = configManager.load()
 *
 *         val redisManager = LettuceRedisManager(config.redis, logger)
 *         val databaseManager = HikariDatabaseManager(config.database, logger)
 *         val myService = MyServiceImpl(databaseManager, redisManager)
 *
 *         return listOf(
 *             configManager,
 *             redisManager,
 *             databaseManager,
 *             myService
 *         )
 *     }
 *
 *     override fun onPluginEnable() {
 *         // Register commands, listeners, etc.
 *         val myService = registry.get<MyService>()
 *         registerCommands(myService)
 *     }
 * }
 * ```
 *
 * ## Automatic Listener Registration
 *
 * Components that implement [org.bukkit.event.Listener] are automatically registered
 * as Bukkit event listeners after initialization and unregistered on shutdown.
 * Both regular and suspending event handlers are supported.
 * No annotation needed - just implement the interface:
 *
 * ```kotlin
 * class MyService : PluginComponent, Listener {
 *     @EventHandler
 *     fun onPlayerJoin(event: PlayerJoinEvent) {
 *         // Automatically registered!
 *     }
 *
 *     override suspend fun initialize() { /* ... */ }
 *     override suspend fun shutdown() { /* ... */ }
 * }
 * ```
 *
 * ## Design Notes
 *
 * This class uses [runBlocking] during [onEnable] and [onDisable] to bridge
 * Bukkit's synchronous lifecycle with suspend functions. This is intentional:
 *
 * 1. Paper's `onEnable()` MUST complete synchronously - plugins need to be
 *    fully initialized when it returns
 * 2. Database/Redis connections MUST be established before the plugin is
 *    considered "enabled"
 * 3. `runBlocking` is the official Kotlin way to bridge blocking and suspend
 *    code in main functions and initialization
 *
 * @see PluginComponent
 * @see ComponentRegistry
 */
abstract class LoafyPlugin : JavaPlugin() {

    /**
     * Plugin-scoped coroutine scope for async operations.
     *
     * Uses Paper's AsyncScheduler via [createPluginScope].
     * Automatically cancelled when the plugin is disabled.
     *
     * ```kotlin
     * pluginScope.launch {
     *     val data = fetchFromDatabase()
     *     player.sendMessage("Loaded: $data")
     * }
     * ```
     */
    val pluginScope: CoroutineScope by lazy { this.createPluginScope() }

    /**
     * Component registry for managing plugin services.
     *
     * Components are registered during [onEnable] and can be retrieved
     * using [ComponentRegistry.get] after initialization.
     */
    protected val registry = ComponentRegistry()

    /**
     * Defines and returns the list of plugin components.
     *
     * Components are registered and initialized in dependency order.
     * Use constructor injection to wire dependencies between components.
     *
     * This method is called during [onEnable] before any initialization occurs.
     *
     * @return list of plugin components to register
     */
    protected abstract fun components(): List<PluginComponent>

    /**
     * Called after all components are successfully initialized.
     *
     * Override to perform plugin-specific enable logic such as:
     * - Registering commands
     * - Registering event listeners
     * - Starting scheduled tasks
     *
     * Components can be retrieved from [registry] at this point.
     *
     * Note: This is NOT a suspend function. For async initialization,
     * use [pluginScope] to launch coroutines.
     */
    protected open fun onPluginEnable() {}

    /**
     * Called before components are shutdown.
     *
     * Override to perform plugin-specific disable logic such as:
     * - Saving state synchronously
     * - Cleanup tasks
     *
     * Components are still available from [registry] at this point.
     *
     * Note: This is NOT a suspend function. Keep disable logic fast
     * to avoid blocking server shutdown.
     */
    protected open fun onPluginDisable() {}

    /**
     * Plugin enable lifecycle - registers and initializes components.
     *
     * This method is final to ensure proper lifecycle management.
     * Override [components] and [onPluginEnable] instead.
     */
    final override fun onEnable() {
        try {
            // Register all components
            val componentList = components()
            for (component in componentList) {
                @Suppress("UNCHECKED_CAST")
                registry.register(component::class as KClass<PluginComponent>, component)
            }

            // Initialize in dependency order (blocking - must complete before plugin is "enabled")
            runBlocking { registry.initializeAll() }

            // Auto-register components that implement Listener
            registerComponentListeners()

            // Plugin-specific enable logic
            onPluginEnable()

            logger.info("${pluginMeta.name} v${pluginMeta.version} enabled")

        } catch (e: Exception) {
            logger.severe("Failed to enable ${pluginMeta.name}: ${e.message}")
            e.printStackTrace()

            // Attempt graceful shutdown of any initialized components
            try {
                runBlocking { registry.shutdownAll() }
            } catch (shutdownError: Exception) {
                logger.severe("Error during emergency shutdown: ${shutdownError.message}")
            }

            // Disable the plugin
            server.pluginManager.disablePlugin(this)
        }
    }

    /**
     * Plugin disable lifecycle - shuts down components and cancels scope.
     *
     * This method is final to ensure proper lifecycle management.
     * Override [onPluginDisable] instead.
     */
    final override fun onDisable() {
        try {
            // Plugin-specific disable logic
            onPluginDisable()
        } catch (e: Exception) {
            logger.warning("Error during plugin disable: ${e.message}")
        }

        // Unregister all component listeners first — no new events after this
        unregisterComponentListeners()

        // Cancel all coroutines BEFORE component shutdown.
        //
        // Paper sets isEnabled = false before calling onDisable(), so the
        // AsyncDispatcher already falls back to Dispatchers.IO for any
        // in-flight continuations. Cancelling the scope here ensures
        // pluginScope coroutines (e.g., StorageService writer job) receive
        // CancellationException promptly, allowing component shutdown()
        // methods that call join() to return without hanging.
        //
        // Component shutdown can still perform blocking I/O (e.g.,
        // drainRemaining) via blockingIoDispatcher which is backed by
        // Dispatchers.IO.limitedParallelism — independent of pluginScope.
        pluginScope.cancelGracefully()

        try {
            // Shutdown components in reverse dependency order
            runBlocking { registry.shutdownAll() }
        } catch (e: Exception) {
            logger.warning("Error during component shutdown: ${e.message}")
        }

        logger.info("${pluginMeta.name} disabled")
    }

    /**
     * Registers all components that implement [Listener] as Bukkit event listeners.
     *
     * This provides automatic listener registration similar to Fairy's `@RegisterAsListener`,
     * but without requiring annotations or reflection-based discovery.
     */
    private fun registerComponentListeners() {
        for (component in registry.components.values) {
            if (component is Listener) {
                SuspendingEventService.register(component, this, pluginScope)
            }
        }
    }

    /**
     * Unregisters all component listeners from Bukkit's event system.
     */
    private fun unregisterComponentListeners() {
        for (component in registry.components.values) {
            if (component is Listener) {
                HandlerList.unregisterAll(component)
            }
        }
    }
}
