package me.cyljacky02.loafylib.animation.core

/**
 * A single action in an animation sequence.
 *
 * All built-in actions implement this interface. Custom actions can also
 * implement this interface to create new animation effects.
 *
 * Lifecycle:
 * 1. [setup] - Called once when the action starts
 * 2. [tick] - Called each tick during the action (if durationTicks > 0)
 * 3. [teardown] - Called when the action completes or is cancelled
 *
 * Actions with [durationTicks] = 0 are instant (only setup/teardown called).
 * Actions with [durationTicks] > 0 will have tick() called for each tick.
 *
 * Thread Safety: All methods are called on the player's EntityScheduler thread.
 */
interface AnimationAction {
    /**
     * Duration of this action in ticks.
     * - 0 = instant action (only setup/teardown called)
     * - >0 = timed action (tick called for each tick)
     */
    val durationTicks: Int get() = 0

    /**
     * Called once when the action starts.
     * Use for initial setup like freezing player, starting effects, etc.
     *
     * @param context The animation context
     */
    suspend fun setup(context: AnimationContext) {}

    /**
     * Called each tick during the action.
     * Only called if [durationTicks] > 0.
     *
     * @param context The animation context
     * @param tick Current tick (0-indexed, 0 to durationTicks-1)
     * @param progress Progress from 1/n to 1.0 (ensures final tick reaches 100%)
     */
    suspend fun tick(context: AnimationContext, tick: Int, progress: Float) {}

    /**
     * Called when the action completes or is cancelled.
     * Use for cleanup like unfreezing player, stopping effects, etc.
     * Always called, even if the animation is cancelled mid-action.
     *
     * @param context The animation context
     */
    suspend fun teardown(context: AnimationContext) {}
}

