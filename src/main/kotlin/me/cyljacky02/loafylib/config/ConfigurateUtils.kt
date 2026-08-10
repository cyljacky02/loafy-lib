package me.cyljacky02.loafylib.config

import org.bukkit.plugin.Plugin
import org.spongepowered.configurate.ConfigurateException
import org.spongepowered.configurate.ConfigurationNode
import org.spongepowered.configurate.ConfigurationOptions
import org.spongepowered.configurate.kotlin.extensions.get
import org.spongepowered.configurate.kotlin.extensions.typedSet
import org.spongepowered.configurate.kotlin.kotlinCommentsProcessor
import io.leangen.geantyref.TypeToken
import org.spongepowered.configurate.objectmapping.ObjectMapper
import org.spongepowered.configurate.objectmapping.meta.Comment
import org.spongepowered.configurate.serialize.TypeSerializerCollection
import org.spongepowered.configurate.yaml.NodeStyle
import org.spongepowered.configurate.yaml.YamlConfigurationLoader
import java.nio.file.Files
import java.nio.file.Path
import me.cyljacky02.loafylib.scheduler.asyncDispatcher
import kotlinx.coroutines.withContext
import kotlin.time.Duration

/**
 * Utility functions for working with Sponge Configurate YAML configuration.
 *
 * Provides standardized configuration loading with:
 * - BLOCK node style for readable YAML output
 * - Kotlin data class support via dataClassFieldDiscoverer()
 * - kotlinCommentsProcessor for proper @Comment handling with trimIndent()
 * - shouldCopyDefaults(true) for automatic default population
 * - Extension functions for common operations
 *
 * ## Usage Example
 * ```kotlin
 * // Create a loader for a config file
 * val loader = ConfigurateUtils.createYamlLoader(
 *     path = plugin.ensureDataFolder().resolve("config.yml"),
 *     header = "My Plugin Configuration"
 * )
 *
 * // Load configuration
 * val node = loader.load()
 * val config = node.get<MyConfig>() ?: MyConfig()
 *
 * // Save with defaults populated
 * node.set(config)
 * loader.save(node)
 * ```
 */
object ConfigurateUtils {

    /**
     * Kotlin-optimized ObjectMapper factory configured with:
     * - [LoafyDataClassFieldDiscoverer] for Kotlin data class constructor parameter mapping
     *   while preserving generic type information (e.g., List<String>)
     * - [kotlinCommentsProcessor] for @Comment with trimIndent() on multi-line strings
     *
     * Note: We build manually rather than using objectMapperFactory() because
     * the pre-built factory only includes DataClassFieldDiscoverer but not
     * kotlinCommentsProcessor. The @field: prefix is NOT required on @Comment
     * annotations - the discoverer combines annotations from parameter,
     * parameter type, backing field, and getter automatically.
     */
    private val kotlinObjectMapperFactory: ObjectMapper.Factory = ObjectMapper.factoryBuilder()
        .addDiscoverer(LoafyDataClassFieldDiscoverer)
        .addProcessor(Comment::class.java, kotlinCommentsProcessor())
        .build()

    /**
     * Creates a YamlConfigurationLoader with standard settings for Loafy plugins.
     *
     * The loader is configured with:
     * - BLOCK node style for human-readable YAML
     * - shouldCopyDefaults(true) to populate missing values from defaults
     * - Optional header comment at the top of the file
     * - Kotlin data class support (no @field: prefix needed for annotations)
     * - kotlinCommentsProcessor for multi-line comment trimIndent()
     *
     * @param path Path to the YAML configuration file
     * @param header Optional header comment to include at the top of the file
     * @return Configured YamlConfigurationLoader
     */
    fun createYamlLoader(path: Path, header: String? = null): YamlConfigurationLoader {
        return YamlConfigurationLoader.builder()
            .path(path)
            .nodeStyle(NodeStyle.BLOCK)
            .defaultOptions { options ->
                options
                    .serializers {
                        it.registerAnnotatedObjects(kotlinObjectMapperFactory)
                        it.register(Duration::class.java, DurationTypeSerializer())
                    }
                    .shouldCopyDefaults(true).let { opts ->
                    if (header != null) opts.header(header) else opts
                }
            }
            .build()
    }

    fun createYamlLoader(
        path: Path,
        header: String? = null,
        extraSerializers: (TypeSerializerCollection.Builder) -> Unit
    ): YamlConfigurationLoader {
        return YamlConfigurationLoader.builder()
            .path(path)
            .nodeStyle(NodeStyle.BLOCK)
            .defaultOptions { options ->
                options
                    .serializers {
                        it.registerAnnotatedObjects(kotlinObjectMapperFactory)
                        it.register(Duration::class.java, DurationTypeSerializer())
                        extraSerializers(it)
                    }
                    .shouldCopyDefaults(true).let { opts ->
                        if (header != null) opts.header(header) else opts
                    }
            }
            .build()
    }

    /**
     * Creates a YamlConfigurationLoader with custom configuration options.
     *
     * @param path Path to the YAML configuration file
     * @param optionsBuilder Function to customize ConfigurationOptions
     * @return Configured YamlConfigurationLoader
     */
    fun createYamlLoader(
        path: Path,
        optionsBuilder: (ConfigurationOptions) -> ConfigurationOptions
    ): YamlConfigurationLoader {
        return YamlConfigurationLoader.builder()
            .path(path)
            .nodeStyle(NodeStyle.BLOCK)
            .defaultOptions { options ->
                optionsBuilder(options
                    .serializers {
                        it.registerAnnotatedObjects(kotlinObjectMapperFactory)
                        it.register(Duration::class.java, DurationTypeSerializer())
                    }
                    .shouldCopyDefaults(true))
            }
            .build()
    }
}

/**
 * Ensures the plugin's data folder exists, creating it if necessary.
 *
 * @return Path to the plugin's data folder
 * @throws IllegalStateException if the folder cannot be created
 */
fun Plugin.ensureDataFolder(): Path {
    val folder = dataFolder.toPath()
    if (!Files.exists(folder)) {
        Files.createDirectories(folder)
    }
    return folder
}

/**
 * Loads a configuration object from this loader.
 *
 * @param T The configuration class type (must be @ConfigSerializable)
 * @param default Default value to use if the file doesn't exist or is empty
 * @return The loaded configuration object
 * @throws ConfigurateException if loading fails
 */
inline fun <reified T : Any> YamlConfigurationLoader.loadConfig(default: T? = null): T {
    val node = load()
    return node.get<T>() ?: default ?: throw ConfigurateException(
        "Failed to load configuration of type ${T::class.simpleName}"
    )
}

/**
 * Loads a configuration object from this loader, returning null if not found.
 *
 * @param T The configuration class type (must be @ConfigSerializable)
 * @return The loaded configuration object, or null if not found
 * @throws ConfigurateException if loading fails due to parsing errors
 */
inline fun <reified T : Any> YamlConfigurationLoader.loadConfigOrNull(): T? {
    val node = load()
    return node.get<T>()
}

/**
 * Saves a configuration object using this loader.
 *
 * This will serialize the object to YAML and write it to the configured path.
 * If shouldCopyDefaults is enabled, default values will be included in the output.
 *
 * @param T The configuration class type (must be @ConfigSerializable)
 * @param config The configuration object to save
 * @throws ConfigurateException if saving fails
 */
inline fun <reified T : Any> YamlConfigurationLoader.saveConfig(config: T) {
    val node = createNode()
    node.typedSet(config)
    save(node)
}

/**
 * Loads a configuration, applies defaults, and saves back to ensure the file
 * contains all default values.
 *
 * This is useful for initial configuration setup where you want to:
 * 1. Load existing values (if any)
 * 2. Merge with defaults for any missing values
 * 3. Save the complete configuration back to disk
 *
 * **First-run behavior**: When the file is missing or empty, the provided [default]
 * is used directly. This is critical because Configurate's ObjectMapper always
 * constructs a non-null instance from empty nodes using constructor defaults
 * (e.g. `emptyMap()`) — the `?: default` fallback would never trigger, causing
 * rich first-run defaults (like pre-populated drop tables) to be lost.
 *
 * @param T The configuration class type (must be @ConfigSerializable)
 * @param default The default configuration to use on first run
 * @return The loaded configuration with defaults applied
 * @throws ConfigurateException if loading or saving fails
 */
inline fun <reified T : Any> YamlConfigurationLoader.loadAndSaveDefaults(default: T): T {
    val node = load()
    // When the node is empty (file missing or blank), ObjectMapper would construct
    // from constructor defaults (e.g. emptyMap) instead of our rich defaults.
    // Use the provided default directly in that case.
    val config = if (node.empty()) default else (node.get<T>() ?: default)
    node.typedSet(config)
    save(node)
    return config
}

/**
 * Gets a value from this node, or returns the default if not present.
 *
 * @param T The value type
 * @param default Default value to return if the node is empty
 * @return The value from the node, or the default
 */
inline fun <reified T : Any> ConfigurationNode.getOrDefault(default: T): T {
    return get<T>() ?: default
}

/**
 * Asynchronously loads a configuration object from this loader.
 *
 * Uses [asyncDispatcher] (Dispatchers.IO) for non-blocking file I/O.
 * This is Folia-compatible since file operations don't touch game state.
 *
 * ## Thread Safety
 * - File I/O runs on IO thread pool (Dispatchers.IO)
 * - Safe to call from any thread (async, tick, region, entity)
 * - The returned config object can be used anywhere
 *
 * ## When to use sync vs async
 * - Use sync ([loadConfig]) during plugin enable/disable (runs in runBlocking)
 * - Use async for runtime config reloading within a coroutine scope
 *
 * @param T The configuration class type (must be @ConfigSerializable)
 * @param default Default value to use if the file doesn't exist or is empty
 * @return The loaded configuration object
 * @throws ConfigurateException if loading fails
 */
suspend inline fun <reified T : Any> YamlConfigurationLoader.loadConfigAsync(default: T? = null): T {
    val typeToken = object : TypeToken<T>() {}
    return withContext(asyncDispatcher) {
        val node = load()
        node.get(typeToken) ?: default ?: throw ConfigurateException(
            "Failed to load configuration of type ${T::class.simpleName}"
        )
    }
}

/**
 * Asynchronously loads a configuration object, returning null if not found.
 *
 * Uses [asyncDispatcher] for non-blocking file I/O. Folia-compatible.
 *
 * @param T The configuration class type (must be @ConfigSerializable)
 * @return The loaded configuration object, or null if not found
 * @throws ConfigurateException if loading fails due to parsing errors
 */
suspend inline fun <reified T : Any> YamlConfigurationLoader.loadConfigOrNullAsync(): T? {
    val typeToken = object : TypeToken<T>() {}
    return withContext(asyncDispatcher) {
        val node = load()
        node.get(typeToken)
    }
}

/**
 * Asynchronously saves a configuration object.
 *
 * Uses [asyncDispatcher] for non-blocking file I/O. Folia-compatible.
 *
 * @param T The configuration class type (must be @ConfigSerializable)
 * @param config The configuration object to save
 * @throws ConfigurateException if saving fails
 */
suspend inline fun <reified T : Any> YamlConfigurationLoader.saveConfigAsync(config: T) {
    val typeToken = object : TypeToken<T>() {}
    withContext(asyncDispatcher) {
        val node = createNode()
        node.set(typeToken, config)
        save(node)
    }
}

/**
 * Asynchronously loads a configuration, applies defaults, and saves back.
 *
 * Uses [asyncDispatcher] for non-blocking file I/O. Folia-compatible.
 * Ideal for initial plugin setup where you want to ensure the config file
 * exists with all default values.
 *
 * See [loadAndSaveDefaults] for details on first-run behavior.
 *
 * @param T The configuration class type (must be @ConfigSerializable)
 * @param default The default configuration to use on first run
 * @return The loaded configuration with defaults applied
 * @throws ConfigurateException if loading or saving fails
 */
suspend inline fun <reified T : Any> YamlConfigurationLoader.loadAndSaveDefaultsAsync(default: T): T {
    val typeToken = object : TypeToken<T>() {}
    return withContext(asyncDispatcher) {
        val node = load()
        val config = if (node.empty()) default else (node.get(typeToken) ?: default)
        node.set(typeToken, config)
        save(node)
        config
    }
}
