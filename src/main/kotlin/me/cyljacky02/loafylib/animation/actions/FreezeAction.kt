package me.cyljacky02.loafylib.animation.actions

import me.cyljacky02.loafylib.animation.core.AnimationAction
import me.cyljacky02.loafylib.animation.core.AnimationContext

/**
 * Freeze player movement temporarily.
 *
 * While frozen, the player cannot move but can still look around.
 * The freeze is automatically released when the action ends.
 *
 * @property durationTicks How long to freeze the player
 */
data class FreezeAction(
    override val durationTicks: Int
) : AnimationAction {

    override suspend fun setup(context: AnimationContext) {
        context.provider.freezePlayer(context.player, frozen = true)
    }

    override suspend fun teardown(context: AnimationContext) {
        context.provider.freezePlayer(context.player, frozen = false)
    }
}

