package me.cyljacky02.loafylib.animation

import me.cyljacky02.loafylib.animation.config.AnimationLoader
import me.cyljacky02.loafylib.animation.camera.CameraPacketHandler
import me.cyljacky02.loafylib.animation.core.*
import me.cyljacky02.loafylib.animation.provider.AnimationProviderFactory
import me.cyljacky02.loafylib.plugin.PluginComponent
import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.plugin.Plugin
import java.nio.file.Path
import java.util.logging.Logger

/**
 * Main service for playing animations.
 *
 * Combines AnimationPlayer, AnimationRegistry, and AnimationLoader
 * into a single, easy-to-use service.
 *
 * Example usage:
 * ```kotlin
 * class MyPlugin : LoafyPlugin() {
 *     private lateinit var animationService: AnimationService
 *
 *     override fun components() = listOf(
 *         AnimationService(this, logger).also { animationService = it }
 *     )
 *
 *     override fun onPluginEnable() {
 *         // Load animations from config
 *         animationService.loadAnimations(dataFolder.toPath().resolve("animations.yml"))
 *
 *         // Or register programmatically
 *         animationService.register(myAnimation)
 *     }
 *
 *     // Play an animation
 *     suspend fun playRtpAnimation(player: Player, target: Location) {
 *         animationService.play(player, "rtp-launch", targetLocation = target)
 *     }
 * }
 * ```
 */
class AnimationService(
    private val plugin: Plugin,
    private val logger: Logger? = null
) : PluginComponent, Listener {

    /** The animation registry for storing named animations */
    val registry = AnimationRegistry()

    /** The animation provider (PacketEvents or Bukkit fallback) */
    val provider: AnimationProvider by lazy {
        AnimationProviderFactory.create(plugin, logger)
    }

    /** The animation player for executing animations */
    val player: AnimationPlayer by lazy {
        AnimationPlayer(plugin, provider, logger)
    }

    /** The animation loader for YAML configs */
    val loader: AnimationLoader by lazy {
        AnimationLoader(logger)
    }

    override suspend fun initialize() {
        logger?.info("AnimationService enabled - Provider: ${if (provider.isAvailable()) "ready" else "unavailable"}")
        plugin.server.onlinePlayers.forEach { player ->
            CameraPacketHandler.restorePersistentCameraControl(player)
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onPlayerJoin(event: PlayerJoinEvent) {
        CameraPacketHandler.restorePersistentCameraControl(event.player)
    }

    override suspend fun shutdown() {
        player.cancelAll()
        registry.clear()
        // Unregister packet handler to prevent memory leaks on plugin reload
        CameraPacketHandler.unregister()
        logger?.info("AnimationService disabled")
    }

    // ========== Convenience Methods ==========

    /**
     * Play a registered animation by ID.
     *
     * @param player The player to animate
     * @param animationId The animation ID from the registry
     * @param targetLocation Optional target location for directional animations
     * @param parameters Custom parameters accessible in actions
     * @param cancelExisting If true, cancels any existing animation for this player
     * @return The result of the animation execution
     */
    suspend fun play(
        player: Player,
        animationId: String,
        targetLocation: Location? = null,
        parameters: Map<String, Any> = emptyMap(),
        cancelExisting: Boolean = true
    ): AnimationResult {
        val sequence = registry.get(animationId)
            ?: return AnimationResult.Error("Animation '$animationId' not found")

        return this.player.play(player, sequence, targetLocation, parameters, cancelExisting)
    }

    /**
     * Play an animation sequence directly (not from registry).
     *
     * @param player The player to animate
     * @param sequence The animation sequence to play
     * @param targetLocation Optional target location for directional animations
     * @param parameters Custom parameters accessible in actions
     * @param cancelExisting If true, cancels any existing animation for this player
     * @return The result of the animation execution
     */
    suspend fun play(
        player: Player,
        sequence: AnimationSequence,
        targetLocation: Location? = null,
        parameters: Map<String, Any> = emptyMap(),
        cancelExisting: Boolean = true
    ): AnimationResult {
        return this.player.play(player, sequence, targetLocation, parameters, cancelExisting)
    }

    /**
     * Register an animation sequence.
     *
     * @param sequence The sequence to register
     */
    fun register(sequence: AnimationSequence) {
        registry.registerOrReplace(sequence)
    }

    /**
     * Load animations from a YAML file into the registry.
     *
     * @param path Path to the YAML file
     * @return Number of animations loaded
     */
    fun loadAnimations(path: Path): Int {
        return loader.loadIntoRegistry(path, registry)
    }

    /**
     * Cancel any active animation for a player.
     *
     * @param player The player whose animation to cancel
     * @return true if an animation was cancelled
     */
    fun cancel(player: Player): Boolean {
        return this.player.cancel(player)
    }

    /**
     * Check if a player has an active animation.
     *
     * @param player The player to check
     * @return true if the player has an active animation
     */
    fun isPlaying(player: Player): Boolean {
        return this.player.isPlaying(player)
    }
}

