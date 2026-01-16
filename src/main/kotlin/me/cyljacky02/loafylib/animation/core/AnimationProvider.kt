package me.cyljacky02.loafylib.animation.core

import net.kyori.adventure.text.Component
import org.bukkit.Location
import org.bukkit.Particle
import org.bukkit.entity.Player
import org.bukkit.util.Vector

/**
 * Provider interface for animation effects.
 *
 * Abstracts the underlying implementation (PacketEvents vs Bukkit API).
 * Implementations handle the actual effect rendering.
 *
 * Thread Safety: All methods should be safe to call from EntityScheduler threads.
 */
interface AnimationProvider {

    /**
     * Check if this provider is available and functional.
     * @return true if the provider can be used
     */
    fun isAvailable(): Boolean

    /**
     * Set the player's velocity.
     *
     * @param player The player to affect
     * @param velocity The velocity vector to apply
     */
    fun setVelocity(player: Player, velocity: Vector)

    /**
     * Freeze or unfreeze player movement.
     *
     * When frozen, the player cannot move but can still look around.
     * Implementation may use packets or Bukkit API.
     *
     * @param player The player to affect
     * @param frozen true to freeze, false to unfreeze
     */
    fun freezePlayer(player: Player, frozen: Boolean)

    /**
     * Spawn particles at a location visible to a player.
     *
     * @param player The player who will see the particles
     * @param particle The particle type
     * @param location The location to spawn particles
     * @param count Number of particles
     * @param spread Spread/offset of particles
     * @param speed Particle speed/extra data
     */
    fun spawnParticles(
        player: Player,
        particle: Particle,
        location: Location,
        count: Int,
        spread: Double,
        speed: Double
    )

    /**
     * Show a title and subtitle to the player.
     *
     * @param player The player to show the title to
     * @param title The main title component
     * @param subtitle The subtitle component
     * @param fadeInTicks Fade in duration in ticks
     * @param stayTicks Stay duration in ticks
     * @param fadeOutTicks Fade out duration in ticks
     */
    fun showTitle(
        player: Player,
        title: Component,
        subtitle: Component,
        fadeInTicks: Int,
        stayTicks: Int,
        fadeOutTicks: Int
    )

    /**
     * Apply a camera shake effect to the player.
     *
     * This is a visual effect only - it doesn't actually move the player.
     * May not be supported by all providers (Bukkit fallback does nothing).
     *
     * @param player The player to shake
     * @param intensity Shake intensity (0.0 to 1.0)
     */
    fun shakeCamera(player: Player, intensity: Float)

    /**
     * Clear any active effects on the player.
     *
     * Called during cleanup to ensure player state is restored.
     *
     * @param player The player to clear effects from
     */
    fun clearEffects(player: Player)
}

