package me.cyljacky02.loafylib.config

import org.bukkit.plugin.Plugin
import org.spongepowered.configurate.ConfigurateException
import org.spongepowered.configurate.ConfigurationNode
import org.spongepowered.configurate.ConfigurationOptions
import org.spongepowered.configurate.kotlin.dataClassFieldDiscoverer
import org.spongepowered.configurate.kotlin.extensions.get
import org.spongepowered.configurate.kotlin.kotlinCommentsProcessor
import org.spongepowered.configurate.objectmapping.ObjectMapper
import org.spongepowered.configurate.objectmapping.meta.Comment
import org.spongepowered.configurate.yaml.NodeStyle
import org.spongepowered.configurate.yaml.YamlConfigurationLoader
import java.nio.file.Files
import java.nio.file.Path
import me.cyljacky02.loafylib.scheduler.asyncDispatcher
import kotlinx.coroutines.withContext

/**
 * Utility functions for working with Sponge Configurate YAML configuration.
 *
 * Provides standardized configuration loading with:
 * - BLOCK node style for readable YAML output
 * - Kotlin data class support via objectMapperFactory()
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
     * - [dataClassFieldDiscoverer] for Kotlin data class constructor parameter mapping
     * - [kotlinCommentsProcessor] for @Comment with trimIndent() on multi-line strings
     *
     * Note: We build manually rather than using objectMapperFactory() because
     * the pre-built factory only includes DataClassFieldDiscoverer but not
     * kotlinCommentsProcessor. The @field: prefix is NOT required on @Comment
     * annotations - DataClassFieldDiscoverer combines annotations from parameter,
     * parameter type, and backing field automatically.
     */
    private val kotlinObjectMapperFactory: ObjectMapper.Factory = ObjectMapper.factoryBuilder()
        .addDiscoverer(dataClassFieldDiscoverer())
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
                    .serializers { it.registerAnnotatedObjects(kotlinObjectMapperFactory) }
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
                    .serializers { it.registerAnnotatedObjects(kotlinObjectMapperFactory) }
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
    node.set(config)
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
 * @param T The configuration class type (must be @ConfigSerializable)
 * @param default The default configuration to use for missing values
 * @return The loaded configuration with defaults applied
 * @throws ConfigurateException if loading or saving fails
 */
inline fun <reified T : Any> YamlConfigurationLoader.loadAndSaveDefaults(default: T): T {
    val node = load()
    val config = node.get<T>() ?: default
    node.set(config)
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
 * - Use async during plugin enable/disable in a coroutine scope
 * - Use sync ([loadConfig]) for one-time blocking loads (e.g., in onEnable before any async work)
 *
 * @param T The configuration class type (must be @ConfigSerializable)
 * @param default Default value to use if the file doesn't exist or is empty
 * @return The loaded configuration object
 * @throws ConfigurateException if loading fails
 */
suspend inline fun <reified T : Any> YamlConfigurationLoader.loadConfigAsync(default: T? = null): T {
    val type = T::class.java
    return withContext(asyncDispatcher) {
        val node = load()
        node.get(type) ?: default ?: throw ConfigurateException(
            "Failed to load configuration of type ${type.simpleName}"
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
    val type = T::class.java
    return withContext(asyncDispatcher) {
        val node = load()
        node.get(type)
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
    withContext(asyncDispatcher) {
        val node = createNode()
        node.set(T::class.java, config)
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
 * @param T The configuration class type (must be @ConfigSerializable)
 * @param default The default configuration to use for missing values
 * @return The loaded configuration with defaults applied
 * @throws ConfigurateException if loading or saving fails
 */
suspend inline fun <reified T : Any> YamlConfigurationLoader.loadAndSaveDefaultsAsync(default: T): T {
    val type = T::class.java
    return withContext(asyncDispatcher) {
        val node = load()
        val config = node.get(type) ?: default
        node.set(type, config)
        save(node)
        config
    }
}
