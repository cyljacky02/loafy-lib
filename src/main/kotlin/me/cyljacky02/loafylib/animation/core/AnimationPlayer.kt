package me.cyljacky02.loafylib.animation.core

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import me.cyljacky02.loafylib.scheduler.delayTicks
import me.cyljacky02.loafylib.scheduler.withEntityContext
import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.logging.Level
import java.util.logging.Logger

/**
 * Executes animation sequences on players.
 *
 * Thread-safe and Folia-compatible using EntityScheduler.
 * Handles concurrent animations, cancellation, and cleanup.
 *
 * ## Thread Safety
 * All animation actions are executed on the player's owning region thread
 * (EntityScheduler), ensuring Folia compatibility. This is critical because
 * actions like VelocityAction and LookAtAction modify entity state.
 *
 * ## Safety Features
 * - Automatic timeout after [DEFAULT_TIMEOUT_MS] (30 seconds by default)
 * - Proper cleanup on cancellation, timeout, or player disconnect
 * - Effects are always cleared in finally block
 *
 * @property plugin The plugin instance for scheduling
 * @property provider The animation provider for effects
 * @property logger Optional logger for debug output
 * @property timeoutMs Maximum animation duration in milliseconds (default: 30 seconds)
 */
class AnimationPlayer(
    private val plugin: Plugin,
    private val provider: AnimationProvider,
    private val logger: Logger? = null,
    private val timeoutMs: Long = DEFAULT_TIMEOUT_MS
) {
    companion object {
        /** Default timeout for animations: 30 seconds (600 ticks) */
        const val DEFAULT_TIMEOUT_MS = 30_000L

        /** Maximum allowed timeout: 5 minutes */
        const val MAX_TIMEOUT_MS = 300_000L
    }

    // Track active animations per player
    private val activeAnimations = ConcurrentHashMap<UUID, Job>()

    /**
     * Play an animation sequence for a player.
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
        val playerId = player.uniqueId

        // Check player is online
        if (!player.isOnline) {
            return AnimationResult.PlayerDisconnected
        }

        // Handle existing animation atomically using compute()
        // This prevents race conditions where two concurrent play() calls could both proceed
        var existingJob: Job? = null
        val shouldProceed = activeAnimations.compute(playerId) { _, current ->
            if (current != null && current.isActive) {
                if (cancelExisting) {
                    existingJob = current
                    current // Will be replaced after we start new job
                } else {
                    return@compute current // Keep existing, signal not to proceed
                }
            }
            current // Placeholder, will be replaced
        }
        
        // If not cancelling and animation exists, return early
        if (!cancelExisting && shouldProceed != null && shouldProceed.isActive) {
            return AnimationResult.AlreadyPlaying
        }
        
        // Cancel existing job outside of compute to avoid holding lock
        existingJob?.cancel()

        logger?.fine("Starting animation '${sequence.id}' for player ${player.name}")

        return try {
            // Use timeout to prevent infinite animations from breaking the server
            withTimeout(timeoutMs.coerceAtMost(MAX_TIMEOUT_MS)) {
                coroutineScope {
                    val job = launch {
                        executeAnimation(player, sequence, targetLocation, parameters)
                    }
                    activeAnimations[playerId] = job
                    job.join()
                }
            }
            logger?.fine("Animation '${sequence.id}' completed for player ${player.name}")
            AnimationResult.Completed
        } catch (e: TimeoutCancellationException) {
            logger?.warning("Animation '${sequence.id}' timed out for player ${player.name} (max: ${timeoutMs}ms)")
            AnimationResult.Error("Animation timed out after ${timeoutMs}ms")
        } catch (e: CancellationException) {
            logger?.fine("Animation '${sequence.id}' cancelled for player ${player.name}")
            AnimationResult.Cancelled
        } catch (e: PlayerDisconnectedException) {
            logger?.fine("Animation '${sequence.id}' stopped - player ${player.name} disconnected")
            AnimationResult.PlayerDisconnected
        } catch (e: Exception) {
            logger?.log(Level.WARNING, "Animation '${sequence.id}' failed for player ${player.name}", e)
            AnimationResult.Error(e.message ?: "Unknown error", e)
        } finally {
            activeAnimations.remove(playerId)
            // Ensure effects are cleared
            if (player.isOnline) {
                provider.clearEffects(player)
            }
        }
    }

    private suspend fun executeAnimation(
        player: Player,
        sequence: AnimationSequence,
        targetLocation: Location?,
        parameters: Map<String, Any>
    ) {
        // CRITICAL: Switch to entity's region thread before executing any actions.
        // This ensures Folia compatibility for actions that modify entity state
        // (e.g., VelocityAction, LookAtAction, FreezeAction).
        player.withEntityContext(plugin) {
            // Capture player state before animation (following Typewriter's pattern)
            // This allows safe restoration even if animation is cancelled or errors
            val stateSnapshot = PlayerStateSnapshot.capture(player)

            try {
                // Apply safe animation state (enable flight, disable fall damage)
                // Based on Paper's best practices and Typewriter's approach
                stateSnapshot.applySafeAnimationState(player)

                val context = AnimationContext(
                    player = player,
                    plugin = plugin,
                    startLocation = player.location.clone(),
                    targetLocation = targetLocation,
                    parameters = parameters,
                    provider = provider
                )

                for (action in sequence.actions) {
                    coroutineScope { ensureActive() }
                    checkPlayerOnline(player)

                    executeAction(action, context)
                }
            } finally {
                // Always restore player state, even on cancellation/error
                stateSnapshot.restore(player)
            }
        }
    }

    private suspend fun executeAction(action: AnimationAction, context: AnimationContext) {
        try {
            // Setup phase
            action.setup(context)

            // Tick phase (if action has duration)
            if (action.durationTicks > 0) {
                for (tick in 0 until action.durationTicks) {
                    coroutineScope { ensureActive() }
                    checkPlayerOnline(context.player)

                    // Progress goes from 1/n to n/n (1.0), ensuring final tick reaches 100%
                    val progress = (tick + 1).toFloat() / action.durationTicks
                    action.tick(context, tick, progress)

                    // Wait one tick using EntityScheduler
                    context.player.delayTicks(plugin, 1)
                }
            }
        } finally {
            // Teardown always runs (even on cancellation)
            try {
                action.teardown(context)
            } catch (e: Exception) {
                logger?.log(Level.WARNING, "Error in action teardown", e)
            }
        }
    }

    private fun checkPlayerOnline(player: Player) {
        if (!player.isOnline) {
            throw PlayerDisconnectedException()
        }
    }

    /**
     * Cancel any active animation for a player.
     *
     * @param player The player whose animation to cancel
     * @return true if an animation was cancelled, false if none was active
     */
    fun cancel(player: Player): Boolean {
        val job = activeAnimations[player.uniqueId] ?: return false
        job.cancel()
        return true
    }

    /**
     * Check if a player has an active animation.
     *
     * @param player The player to check
     * @return true if the player has an active animation
     */
    fun isPlaying(player: Player): Boolean =
        activeAnimations.containsKey(player.uniqueId)

    /**
     * Cancel all active animations.
     * Useful for plugin shutdown.
     */
    fun cancelAll() {
        activeAnimations.values.forEach { it.cancel() }
        activeAnimations.clear()
    }
}

/** Internal exception for player disconnect detection */
private class PlayerDisconnectedException : Exception()

