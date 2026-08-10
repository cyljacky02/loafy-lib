package me.cyljacky02.loafylib.animation.actions

import me.cyljacky02.loafylib.animation.core.AnimationAction
import me.cyljacky02.loafylib.animation.core.AnimationContext

/**
 * Freeze player movement temporarily using potion effects.
 *
 * While frozen, the player cannot move but can still look around.
 * The freeze is automatically released when the action ends.
 *
 * ## ⚠️ Important: Use Camera Hold for Camera-Based Animations!
 *
 * This action uses potion effects (Slowness + Jump Boost) which leaves the player
 * **VULNERABLE to damage and death** during the freeze period.
 *
 * For camera-based animations, use the camera path's `holdTicks` instead:
 * ```kotlin
 * // ❌ WRONG - Player can die during freeze!
 * freeze(15)
 * camera {
 *     path {
 *         playerLocation(hold = 0)  // No hold
 *         ...
 *     }
 * }
 *
 * // ✅ CORRECT - Player is protected from tick 1!
 * camera {
 *     path {
 *         playerLocation(hold = 15)  // Hold acts as "freeze"
 *         ...
 *     }
 * }
 * ```
 *
 * When the camera holds at the player's position, the player:
 * - Appears "frozen" (camera doesn't move)
 * - Is FULLY PROTECTED by CameraPacketHandler (damage/death cancelled)
 * - Is invulnerable, invisible, hidden from others
 *
 * ## When to Use FreezeAction:
 * - Non-camera animations (simple effects without camera movement)
 * - Animations where you want the player to see their own body frozen
 * - Standalone freeze effects without camera control
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

