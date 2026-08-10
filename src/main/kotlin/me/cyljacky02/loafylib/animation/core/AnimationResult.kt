package me.cyljacky02.loafylib.animation.core

/**
 * Result of an animation execution.
 *
 * Uses sealed class for exhaustive when expressions and type safety.
 */
sealed class AnimationResult {
    /** Animation completed successfully */
    data object Completed : AnimationResult()

    /** Animation was cancelled (by code, new animation, or external factors) */
    data class Cancelled(val reason: CancellationReason = CancellationReason.UNKNOWN) : AnimationResult()

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

/**
 * Reasons why an animation was cancelled.
 */
enum class CancellationReason {
    /** Cancelled by code (e.g., AnimationPlayer.cancel()) */
    CODE,
    /** Cancelled because a new animation started */
    NEW_ANIMATION,
    /** Cancelled because player was teleported by external source */
    EXTERNAL_TELEPORT,
    /** Cancelled because player died */
    PLAYER_DEATH,
    /** Unknown or unspecified reason */
    UNKNOWN
}

/**
 * Exception thrown when an animation is cancelled due to external factors.
 * This is NOT a CancellationException - it's caught and converted to AnimationResult.Cancelled.
 */
class AnimationCancelledException(
    val reason: CancellationReason,
    message: String = "Animation cancelled: $reason"
) : Exception(message)

