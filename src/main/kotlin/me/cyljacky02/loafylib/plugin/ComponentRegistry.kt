package me.cyljacky02.loafylib.plugin

import kotlin.reflect.KClass

/**
 * Registry for managing plugin components with dependency-ordered lifecycle.
 *
 * The registry handles:
 * - Component registration with type-safe retrieval
 * - Topological sorting for dependency-ordered initialization
 * - Circular dependency detection at registration time
 * - Graceful failure handling (shutdown already-initialized components on failure)
 *
 * ## Thread Safety
 *
 * This registry is designed for single-threaded access during Bukkit plugin lifecycle:
 * - Registration, initialization, and shutdown occur on the main thread in `onEnable`/`onDisable`
 * - Component retrieval via [get] is safe after initialization completes
 *
 * Do NOT register or initialize components from coroutines or async threads.
 *
 * ## Usage Example
 *
 * ```kotlin
 * val registry = ComponentRegistry()
 *
 * // Register components (order doesn't matter - sorted by dependencies)
 * registry.register(CacheComponent::class, cacheComponent)
 * registry.register(DatabaseComponent::class, databaseComponent)
 *
 * // Initialize in dependency order
 * registry.initializeAll()
 *
 * // Retrieve components
 * val cache = registry.get<CacheComponent>()
 *
 * // Shutdown in reverse order
 * registry.shutdownAll()
 * ```
 *
 * @see PluginComponent
 */
class ComponentRegistry {

    @PublishedApi
    internal val components = mutableMapOf<KClass<*>, PluginComponent>()
    private val initializedComponents = mutableListOf<KClass<*>>()

    /**
     * Registers a component with the registry.
     *
     * Components can be registered in any order; the registry will determine
     * the correct initialization order based on declared dependencies.
     *
     * @param type the component's class reference for type-safe retrieval
     * @param component the component instance
     * @throws IllegalStateException if a component of the same type is already registered
     */
    fun <T : PluginComponent> register(type: KClass<T>, component: T) {
        require(type !in components) { "Component ${type.simpleName} is already registered" }
        components[type] = component
    }


    /**
     * Retrieves a registered component by type.
     *
     * @return the component instance
     * @throws IllegalStateException if no component of the specified type is registered
     */
    inline fun <reified T : PluginComponent> get(): T {
        val component = components[T::class]
            ?: throw IllegalStateException("Component ${T::class.simpleName} is not registered")
        return component as T
    }

    /**
     * Retrieves a registered component by class reference.
     *
     * @param type the component's class reference
     * @return the component instance
     * @throws IllegalStateException if no component of the specified type is registered
     */
    @Suppress("UNCHECKED_CAST")
    fun <T : PluginComponent> get(type: KClass<T>): T {
        val component = components[type]
            ?: throw IllegalStateException("Component ${type.simpleName} is not registered")
        return component as T
    }

    /**
     * Initializes all registered components in dependency order.
     *
     * Components are sorted topologically based on their declared dependencies,
     * ensuring that dependencies are initialized before dependents.
     *
     * If any component fails to initialize, all previously initialized components
     * are shutdown in reverse order before the exception is propagated.
     *
     * @throws IllegalStateException if circular dependencies are detected
     * @throws Exception if any component fails to initialize
     */
    suspend fun initializeAll() {
        initializedComponents.clear()

        // Perform topological sort to determine initialization order
        val initOrder = topologicalSort()

        // Initialize components in dependency order
        for (type in initOrder) {
            val component = components[type]!!
            try {
                component.initialize()
                initializedComponents.add(type)
            } catch (e: Exception) {
                // Shutdown already-initialized components in reverse order
                try {
                    shutdownInitialized()
                } catch (shutdownError: Exception) {
                    e.addSuppressed(shutdownError)
                }
                throw e
            }
        }
    }

    /**
     * Shuts down all initialized components in reverse dependency order.
     *
     * Components are shutdown in reverse initialization order, ensuring that
     * dependents are shutdown before their dependencies.
     *
     * Shutdown errors do not prevent other components from shutting down.
     * If any shutdown errors occur, they are collected and thrown after all
     * components have been given a chance to shutdown.
     */
    suspend fun shutdownAll() {
        shutdownInitialized()
    }

    /**
     * Shuts down all components that were successfully initialized, in reverse order.
     */
    private suspend fun shutdownInitialized() {
        var firstError: Exception? = null
        for (type in initializedComponents.reversed()) {
            try {
                components[type]?.shutdown()
            } catch (e: Exception) {
                if (firstError == null) {
                    firstError = e
                } else {
                    firstError.addSuppressed(e)
                }
            }
        }
        initializedComponents.clear()

        if (firstError != null) {
            throw firstError
        }
    }


    /**
     * Performs topological sort on registered components based on dependencies.
     *
     * Uses Kahn's algorithm to detect circular dependencies and produce a valid
     * initialization order.
     *
     * @return list of component types in dependency order (dependencies first)
     * @throws IllegalStateException if circular dependencies are detected
     */
    private fun topologicalSort(): List<KClass<*>> {
        // Build adjacency list and in-degree map
        val inDegree = mutableMapOf<KClass<*>, Int>()
        val dependents = mutableMapOf<KClass<*>, MutableList<KClass<*>>>()

        // Initialize all components with zero in-degree
        for (type in components.keys) {
            inDegree[type] = 0
            dependents[type] = mutableListOf()
        }

        // Build the dependency graph
        for ((type, component) in components) {
            for (depType in component.dependencies()) {
                // Find a registered component that satisfies the dependency
                // (exact match or implements/extends the dependency type)
                val resolvedDep = components.keys.find { registeredType ->
                    depType.java.isAssignableFrom(registeredType.java)
                }
                if (resolvedDep == null) {
                    throw IllegalStateException(
                        "Component ${type.simpleName} depends on ${depType.simpleName} which is not registered"
                    )
                }
                // resolvedDep -> type (dependency must be initialized before dependent)
                dependents[resolvedDep]!!.add(type)
                inDegree[type] = inDegree[type]!! + 1
            }
        }

        // Kahn's algorithm
        val queue = ArrayDeque<KClass<*>>()
        val result = mutableListOf<KClass<*>>()

        // Start with components that have no dependencies
        for ((type, degree) in inDegree) {
            if (degree == 0) {
                queue.add(type)
            }
        }

        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            result.add(current)

            for (dependent in dependents[current]!!) {
                inDegree[dependent] = inDegree[dependent]!! - 1
                if (inDegree[dependent] == 0) {
                    queue.add(dependent)
                }
            }
        }

        // Check for circular dependencies
        if (result.size != components.size) {
            val cycleComponents = components.keys.filter { it !in result }
            val cycleNames = cycleComponents.map { it.simpleName }.joinToString(", ")
            throw IllegalStateException(
                "Circular dependency detected among components: $cycleNames"
            )
        }

        return result
    }

    /**
     * Returns the number of registered components.
     */
    fun size(): Int = components.size

    /**
     * Checks if a component of the specified type is registered.
     */
    inline fun <reified T : PluginComponent> contains(): Boolean = T::class in components

    /**
     * Checks if a component of the specified type is registered.
     */
    fun contains(type: KClass<out PluginComponent>): Boolean = type in components
}
