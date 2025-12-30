package me.cyljacky02.loafylib.plugin

import kotlin.reflect.KClass

/**
 * Interface for plugin components with lifecycle management.
 *
 * Components are services that have an initialization and shutdown lifecycle.
 * They can declare dependencies on other components, which ensures proper
 * initialization order (dependencies are initialized first).
 *
 * ## Usage Example
 *
 * ```kotlin
 * class DatabaseComponent(private val config: DatabaseConfig) : PluginComponent {
 *     private lateinit var dataSource: HikariDataSource
 *
 *     override suspend fun initialize() {
 *         dataSource = HikariDataSource(config.toHikariConfig())
 *     }
 *
 *     override suspend fun shutdown() {
 *         dataSource.close()
 *     }
 * }
 *
 * class CacheComponent(private val database: DatabaseComponent) : PluginComponent {
 *     override fun dependencies(): List<KClass<out PluginComponent>> = listOf(DatabaseComponent::class)
 *
 *     override suspend fun initialize() {
 *         // Database is guaranteed to be initialized before this
 *     }
 *
 *     override suspend fun shutdown() {
 *         // This will be shutdown before Database
 *     }
 * }
 * ```
 *
 * @see ComponentRegistry
 */
interface PluginComponent {

    /**
     * Returns the list of component types this component depends on.
     *
     * Dependencies are initialized before this component and shutdown after.
     * The [ComponentRegistry] uses this to determine initialization order via
     * topological sorting.
     *
     * @return list of component class references that must be initialized first
     */
    fun dependencies(): List<KClass<out PluginComponent>> = emptyList()

    /**
     * Initializes the component.
     *
     * Called by [ComponentRegistry.initializeAll] in dependency order.
     * All declared dependencies are guaranteed to be initialized before this method is called.
     *
     * @throws Exception if initialization fails; the registry will handle cleanup
     */
    suspend fun initialize()

    /**
     * Shuts down the component and releases resources.
     *
     * Called by [ComponentRegistry.shutdownAll] in reverse dependency order.
     * All components that depend on this one are guaranteed to be shutdown first.
     *
     * Implementations should be idempotent and handle being called multiple times gracefully.
     */
    suspend fun shutdown()
}
