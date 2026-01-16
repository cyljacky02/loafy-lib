package me.cyljacky02.loafylib.animation.actions

import me.cyljacky02.loafylib.animation.core.AnimationAction

/**
 * Wait for a specified number of ticks.
 *
 * This is a no-op action that simply waits. Useful for adding
 * delays between other actions in a sequence.
 *
 * @property durationTicks How long to wait in ticks
 */
data class DelayAction(
    override val durationTicks: Int
) : AnimationAction {
    init {
        require(durationTicks > 0) { "DelayAction duration must be positive" }
    }
}

