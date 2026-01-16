package me.cyljacky02.loafylib.animation.config

import me.cyljacky02.loafylib.animation.core.AnimationRegistry
import me.cyljacky02.loafylib.animation.core.AnimationSequence
import me.cyljacky02.loafylib.config.ConfigurateUtils
import me.cyljacky02.loafylib.config.loadConfig
import org.spongepowered.configurate.objectmapping.ConfigSerializable
import java.nio.file.Path
import java.util.logging.Level
import java.util.logging.Logger

/**
 * Root configuration for animations file.
 */
@ConfigSerializable
data class AnimationsFileConfig(
    val animations: Map<String, AnimationConfig> = emptyMap()
)

/**
 * Loads animation sequences from YAML configuration files.
 *
 * Example usage:
 * ```kotlin
 * val loader = AnimationLoader(logger)
 * val count = loader.loadIntoRegistry(
 *     path = dataFolder.resolve("animations.yml"),
 *     registry = animationRegistry
 * )
 * logger.info("Loaded $count animations")
 * ```
 */
class AnimationLoader(
    private val logger: Logger? = null
) {
    /**
     * Load animations from a YAML file into a registry.
     *
     * @param path Path to the YAML file
     * @param registry The registry to load animations into
     * @param replace If true, replace existing animations with same ID
     * @return Number of animations loaded
     */
    fun loadIntoRegistry(
        path: Path,
        registry: AnimationRegistry,
        replace: Boolean = true
    ): Int {
        val config = try {
            val loader = ConfigurateUtils.createYamlLoader(path)
            loader.loadConfig<AnimationsFileConfig>()
        } catch (e: Exception) {
            logger?.log(Level.WARNING, "Failed to load animations from $path", e)
            return 0
        }

        var loaded = 0
        for ((id, animConfig) in config.animations) {
            try {
                val sequence = animConfig.toSequence(id)
                if (replace) {
                    registry.registerOrReplace(sequence)
                } else {
                    registry.register(sequence)
                }
                loaded++
                logger?.fine("Loaded animation: $id")
            } catch (e: Exception) {
                logger?.log(Level.WARNING, "Failed to load animation '$id'", e)
            }
        }

        logger?.info("Loaded $loaded animations from ${path.fileName}")
        return loaded
    }

    /**
     * Load animations from a YAML file.
     *
     * @param path Path to the YAML file
     * @return List of loaded animation sequences
     */
    fun load(path: Path): List<AnimationSequence> {
        val config = try {
            val loader = ConfigurateUtils.createYamlLoader(path)
            loader.loadConfig<AnimationsFileConfig>()
        } catch (e: Exception) {
            logger?.log(Level.WARNING, "Failed to load animations from $path", e)
            return emptyList()
        }

        return config.animations.mapNotNull { (id, animConfig) ->
            try {
                animConfig.toSequence(id)
            } catch (e: Exception) {
                logger?.log(Level.WARNING, "Failed to parse animation '$id'", e)
                null
            }
        }
    }

    /**
     * Load a single animation from a config map entry.
     *
     * @param id The animation ID
     * @param config The animation configuration
     * @return The animation sequence, or null if parsing failed
     */
    fun loadSingle(id: String, config: AnimationConfig): AnimationSequence? {
        return try {
            config.toSequence(id)
        } catch (e: Exception) {
            logger?.log(Level.WARNING, "Failed to parse animation '$id'", e)
            null
        }
    }
}

