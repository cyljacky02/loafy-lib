package me.cyljacky02.loafylib.animation.actions

import me.cyljacky02.loafylib.animation.core.AnimationAction
import me.cyljacky02.loafylib.animation.core.AnimationContext
import org.bukkit.Location
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * Makes the player look at a specific target.
 *
 * Can smoothly rotate the player's view toward the target location
 * over the specified duration, or instantly snap to it.
 *
 * @property target What to look at (target location or velocity direction)
 * @property smooth If true, smoothly interpolate rotation over duration
 * @property durationTicks Duration for smooth rotation (0 = instant)
 */
data class LookAtAction(
    val target: LookTarget = LookTarget.TARGET_LOCATION,
    val smooth: Boolean = true,
    override val durationTicks: Int = 10
) : AnimationAction {

    // State holder stored in AnimationContext to avoid mutable state in data class
    private data class LookAtState(
        val startYaw: Float, val startPitch: Float,
        val targetYaw: Float, val targetPitch: Float
    )

    override suspend fun setup(context: AnimationContext) {
        val targetLoc = getTargetLocation(context) ?: return
        val player = context.player

        // Store starting rotation
        val startYaw = player.location.yaw
        val startPitch = player.location.pitch

        // Calculate target rotation
        val (yaw, pitch) = calculateRotation(player.location, targetLoc)
        
        // Store state in context for use in tick()
        context.setParam("lookAt.state", LookAtState(startYaw, startPitch, yaw, pitch))

        if (!smooth || durationTicks == 0) {
            // Instant look
            player.setRotation(yaw, pitch)
        }
    }

    override suspend fun tick(context: AnimationContext, tick: Int, progress: Float) {
        if (smooth && durationTicks > 0) {
            val player = context.player
            val state = context.getParam<LookAtState>("lookAt.state") ?: return
            
            val (startYaw, startPitch, targetYaw, targetPitch) = state

            // Interpolate yaw (handle wrap-around)
            val yawDiff = normalizeAngle(targetYaw - startYaw)
            val currentYaw = startYaw + yawDiff * progress

            // Interpolate pitch (no wrap-around needed)
            val currentPitch = startPitch + (targetPitch - startPitch) * progress

            player.setRotation(currentYaw, currentPitch)
        }
    }

    private fun getTargetLocation(context: AnimationContext): Location? {
        return when (target) {
            LookTarget.TARGET_LOCATION -> context.targetLocation
            LookTarget.VELOCITY_DIRECTION -> {
                val velocity = context.player.velocity
                if (velocity.lengthSquared() > 0.001) {
                    context.player.location.clone().add(velocity.normalize().multiply(10))
                } else {
                    null
                }
            }
        }
    }

    /**
     * Calculate yaw and pitch to look from one location to another.
     */
    private fun calculateRotation(from: Location, to: Location): Pair<Float, Float> {
        val dx = to.x - from.x
        val dy = to.y - from.y
        val dz = to.z - from.z

        val distXZ = sqrt(dx * dx + dz * dz)

        // Yaw: angle in XZ plane (0 = south, 90 = west, etc.)
        val yaw = Math.toDegrees(-atan2(dx, dz)).toFloat()

        // Pitch: angle from horizontal (-90 = up, 90 = down)
        val pitch = Math.toDegrees(-atan2(dy, distXZ)).toFloat()

        return yaw to pitch
    }

    /**
     * Normalize angle to be between -180 and 180.
     */
    private fun normalizeAngle(angle: Float): Float {
        var result = angle % 360
        if (result > 180) result -= 360
        if (result < -180) result += 360
        return result
    }
}

/**
 * Target for LookAtAction.
 */
enum class LookTarget {
    /** Look at the animation's target location */
    TARGET_LOCATION,
    /** Look in the direction of current velocity */
    VELOCITY_DIRECTION
}

