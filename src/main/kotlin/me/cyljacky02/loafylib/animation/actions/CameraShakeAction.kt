package me.cyljacky02.loafylib.animation.actions

import me.cyljacky02.loafylib.animation.core.AnimationAction
import me.cyljacky02.loafylib.animation.core.AnimationContext

/**
 * Apply a camera shake effect to the player.
 *
 * This creates a visual shake effect without actually moving the player.
 * The intensity decreases over time based on progress.
 *
 * Note: This effect requires PacketEvents. The Bukkit fallback provider
 * will do nothing for this action.
 *
 * @property intensity Shake intensity (0.0 to 1.0)
 * @property durationTicks How long to shake
 */
data class CameraShakeAction(
    val intensity: Float = 0.5f,
    override val durationTicks: Int = 10
) : AnimationAction {

    init {
        require(intensity in 0f..1f) { "Intensity must be between 0.0 and 1.0" }
        require(durationTicks > 0) { "Duration must be positive" }
    }

    override suspend fun tick(context: AnimationContext, tick: Int, progress: Float) {
        // Intensity decreases as progress increases
        val currentIntensity = intensity * (1f - progress)
        context.provider.shakeCamera(context.player, currentIntensity)
    }
}

