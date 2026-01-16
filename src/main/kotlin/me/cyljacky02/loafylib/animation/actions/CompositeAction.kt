package me.cyljacky02.loafylib.animation.actions

import me.cyljacky02.loafylib.animation.core.AnimationAction
import me.cyljacky02.loafylib.animation.core.AnimationContext

/**
 * Execute multiple actions simultaneously (in parallel).
 *
 * All contained actions start at the same time. The composite action
 * completes when the longest action finishes.
 *
 * Useful for combining effects like velocity + particles + sound.
 *
 * @property actions The actions to execute in parallel
 */
data class CompositeAction(
    val actions: List<AnimationAction>
) : AnimationAction {

    init {
        require(actions.isNotEmpty()) { "CompositeAction must have at least one action" }
    }

    override val durationTicks: Int
        get() = actions.maxOf { it.durationTicks }

    override suspend fun setup(context: AnimationContext) {
        actions.forEach { it.setup(context) }
    }

    override suspend fun tick(context: AnimationContext, tick: Int, progress: Float) {
        actions.forEach { action ->
            if (action.durationTicks > 0 && tick < action.durationTicks) {
                val actionProgress = tick.toFloat() / action.durationTicks
                action.tick(context, tick, actionProgress)
            }
        }
    }

    override suspend fun teardown(context: AnimationContext) {
        actions.forEach { it.teardown(context) }
    }
}

/**
 * DSL helper to create a composite action.
 */
fun parallel(vararg actions: AnimationAction): CompositeAction =
    CompositeAction(actions.toList())

