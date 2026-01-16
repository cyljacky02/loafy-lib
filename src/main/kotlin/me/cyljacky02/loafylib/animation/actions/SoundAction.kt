package me.cyljacky02.loafylib.animation.actions

import me.cyljacky02.loafylib.animation.core.AnimationAction
import me.cyljacky02.loafylib.animation.core.AnimationContext
import org.bukkit.Sound
import org.bukkit.SoundCategory

/**
 * Play a sound to the player.
 *
 * This is an instant action (durationTicks = 0).
 * The sound plays once when the action starts.
 *
 * @property sound The sound to play
 * @property volume Sound volume (0.0 to 1.0+)
 * @property pitch Sound pitch (0.5 to 2.0)
 * @property category Sound category for volume control
 */
data class SoundAction(
    val sound: Sound,
    val volume: Float = 1.0f,
    val pitch: Float = 1.0f,
    val category: SoundCategory = SoundCategory.MASTER
) : AnimationAction {

    override suspend fun setup(context: AnimationContext) {
        context.player.playSound(
            context.player.location,
            sound,
            category,
            volume,
            pitch
        )
    }
}

