package me.cyljacky02.loafylib.animation.core

/**
 * Result of an animation execution.
 *
 * Uses sealed class for exhaustive when expressions and type safety.
 */
sealed class AnimationResult {
    /** Animation completed successfully */
    data object Completed : AnimationResult()

    /** Animation was cancelled (by code or new animation) */
    data object Cancelled : AnimationResult()

    /** Player disconnected during animation */
    data object PlayerDisconnected : AnimationResult()

    /** Another animation is already playing for this player */
    data object AlreadyPlaying : AnimationResult()

    /** An error occurred during animation */
    data class Error(val message: String, val cause: Throwable? = null) : AnimationResult()

    /** Check if the animation completed successfully */
    val isSuccess: Boolean get() = this is Completed

    /** Check if the animation failed for any reason */
    val isFailure: Boolean get() = !isSuccess
}

