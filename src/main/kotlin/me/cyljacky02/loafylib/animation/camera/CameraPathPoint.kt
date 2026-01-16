package me.cyljacky02.loafylib.animation.camera

import org.bukkit.Location
import org.bukkit.util.Vector

/**
 * Represents a point in a camera path.
 *
 * @param type How to resolve the location
 * @param location Absolute location (for ABSOLUTE type)
 * @param offset Offset vector (for RELATIVE types)
 * @param holdTicks How long to hold at this point before moving to next
 * @param transitionTicks How many ticks to transition TO THE NEXT point (null = use action's default)
 */
data class CameraPathPoint(
    val type: PathPointType,
    val location: Location? = null,
    val offset: Vector? = null,
    val holdTicks: Int = 0,
    val transitionTicks: Int? = null  // null = use CameraAction's default interpolationTicks
) {
    companion object {
        /**
         * Create an absolute position point.
         */
        fun absolute(location: Location, holdTicks: Int = 0, transitionTicks: Int? = null): CameraPathPoint {
            return CameraPathPoint(PathPointType.ABSOLUTE, location = location, holdTicks = holdTicks, transitionTicks = transitionTicks)
        }

        /**
         * Create a point relative to the animation start location.
         */
        fun relativeToStart(offset: Vector, holdTicks: Int = 0, transitionTicks: Int? = null): CameraPathPoint {
            return CameraPathPoint(PathPointType.RELATIVE_TO_START, offset = offset, holdTicks = holdTicks, transitionTicks = transitionTicks)
        }

        /**
         * Create a point relative to the target location.
         */
        fun relativeToTarget(offset: Vector, holdTicks: Int = 0, transitionTicks: Int? = null): CameraPathPoint {
            return CameraPathPoint(PathPointType.RELATIVE_TO_TARGET, offset = offset, holdTicks = holdTicks, transitionTicks = transitionTicks)
        }

        /**
         * Create a point at the player's current location.
         */
        fun playerLocation(holdTicks: Int = 0, transitionTicks: Int? = null): CameraPathPoint {
            return CameraPathPoint(PathPointType.PLAYER_LOCATION, holdTicks = holdTicks, transitionTicks = transitionTicks)
        }

        /**
         * Create a point at the target location.
         */
        fun targetLocation(holdTicks: Int = 0, transitionTicks: Int? = null): CameraPathPoint {
            return CameraPathPoint(PathPointType.TARGET_LOCATION, holdTicks = holdTicks, transitionTicks = transitionTicks)
        }

        // ============ Movement-Feel Helpers ============

        /**
         * Create a "launch up" point - camera rises above start position.
         *
         * @param height How high above start position (blocks)
         * @param holdTicks How long to hold at apex before next movement
         * @param transitionTicks How many ticks to transition to next point (null = use default)
         */
        fun launchUp(height: Double = 30.0, holdTicks: Int = 5, transitionTicks: Int? = null): CameraPathPoint {
            return CameraPathPoint(PathPointType.RELATIVE_TO_START, offset = Vector(0.0, height, 0.0), holdTicks = holdTicks, transitionTicks = transitionTicks)
        }

        /**
         * Create a "launch up facing target" point - camera rises while locking onto target.
         *
         * @param height How high above start position (blocks)
         * @param holdTicks How long to hold at apex (already aimed at target)
         * @param transitionTicks How many ticks to transition to next point (null = use default)
         */
        fun launchUpFacingTarget(height: Double = 30.0, holdTicks: Int = 5, transitionTicks: Int? = null): CameraPathPoint {
            return CameraPathPoint(
                type = PathPointType.LAUNCH_UP_FACING_TARGET,
                offset = Vector(0.0, height, 0.0),
                holdTicks = holdTicks,
                transitionTicks = transitionTicks
            )
        }

        /**
         * Create a "boost toward target" point - camera moves partway toward destination.
         *
         * @param distance How far toward target (blocks)
         * @param heightOffset Height above start position
         * @param holdTicks How long to hold before next movement
         * @param transitionTicks How many ticks to transition to next point (null = use default)
         */
        fun boostTowardTarget(distance: Double = 50.0, heightOffset: Double = 10.0, holdTicks: Int = 0, transitionTicks: Int? = null): CameraPathPoint {
            return CameraPathPoint(
                type = PathPointType.BOOST_TOWARD_TARGET,
                offset = Vector(distance, heightOffset, 0.0),
                holdTicks = holdTicks,
                transitionTicks = transitionTicks
            )
        }

        /**
         * Create a "hover" point - camera stays at current relative position.
         *
         * @param holdTicks How long to hover
         * @param transitionTicks How many ticks to transition to next point (null = use default)
         */
        fun hover(holdTicks: Int = 10, transitionTicks: Int? = null): CameraPathPoint {
            return CameraPathPoint(PathPointType.RELATIVE_TO_START, offset = Vector(0.0, 0.0, 0.0), holdTicks = holdTicks, transitionTicks = transitionTicks)
        }
    }
}

/**
 * Types of camera path points.
 */
enum class PathPointType {
    /** Absolute world location */
    ABSOLUTE,
    /** Offset from animation start location */
    RELATIVE_TO_START,
    /** Offset from target location */
    RELATIVE_TO_TARGET,
    /** Current player location (dynamic) */
    PLAYER_LOCATION,
    /** Target location */
    TARGET_LOCATION,
    /**
     * Boost toward target - moves camera in the direction of target.
     * offset.x = distance toward target, offset.y = height offset
     */
    BOOST_TOWARD_TARGET,
    /**
     * Launch up while facing target - camera rises above start position
     * but rotates to face the target location (Gundam lock-on feel).
     * offset.y = height above start position
     */
    LAUNCH_UP_FACING_TARGET
}

