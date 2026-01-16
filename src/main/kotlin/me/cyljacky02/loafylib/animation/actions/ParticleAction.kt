package me.cyljacky02.loafylib.animation.actions

import me.cyljacky02.loafylib.animation.core.AnimationAction
import me.cyljacky02.loafylib.animation.core.AnimationContext
import org.bukkit.Particle

/**
 * Spawn particles at the player's location.
 *
 * Can be instant (durationTicks = 0) or continuous (durationTicks > 0).
 * When trail is enabled, particles follow the player's movement.
 *
 * @property particle The particle type to spawn
 * @property count Number of particles per spawn
 * @property spread Spread/offset of particles from center
 * @property speed Particle speed/extra data
 * @property trail If true, spawn particles each tick (follows player)
 * @property durationTicks Duration for continuous particles (0 = instant)
 */
data class ParticleAction(
    val particle: Particle,
    val count: Int = 10,
    val spread: Double = 0.5,
    val speed: Double = 0.0,
    val trail: Boolean = false,
    override val durationTicks: Int = 0
) : AnimationAction {

    override suspend fun setup(context: AnimationContext) {
        // For instant particles (duration = 0), spawn once in setup
        if (durationTicks == 0) {
            spawnParticles(context)
        }
    }

    override suspend fun tick(context: AnimationContext, tick: Int, progress: Float) {
        // For continuous particles or trails, spawn each tick
        if (trail || durationTicks > 0) {
            spawnParticles(context)
        }
    }

    private fun spawnParticles(context: AnimationContext) {
        context.provider.spawnParticles(
            player = context.player,
            particle = particle,
            location = context.player.location,
            count = count,
            spread = spread,
            speed = speed
        )
    }
}

