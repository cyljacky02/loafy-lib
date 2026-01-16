package me.cyljacky02.loafylib.animation.camera

import me.cyljacky02.loafylib.animation.core.AnimationAction
import me.cyljacky02.loafylib.animation.core.AnimationContext
import me.cyljacky02.loafylib.animation.core.PlayerStateSnapshot
import org.bukkit.Location

/**
 * Animation action that controls the player's camera using a display entity.
 *
 * This is the OPTIMAL approach for smooth camera animations because:
 * - Player's real body stays safe at original location
 * - Client-side interpolation provides smooth movement
 * - No velocity manipulation = no "moved too quickly" warnings
 * - No fall damage, collision, or physics concerns
 * - Lower server load (client handles interpolation)
 *
 * ## Smooth Interpolation (Based on Typewriter)
 * Unlike simple teleport-on-change, this action sends teleport packets EVERY TICK
 * with Catmull-Rom interpolated positions. Combined with client-side interpolation,
 * this creates buttery-smooth camera movement.
 *
 * Safety features (based on Typewriter + Paper best practices):
 * - Player made invulnerable (most performant - direct flag check in NMS)
 * - Player made invisible (hides body at original location)
 * - Player hidden from all other players (multiplayer safety)
 * - Y-coordinate faked (+500) to prevent self-interaction
 *
 * @param path List of locations the camera will follow
 * @param interpolationTicks Client interpolation duration (0-59, default 10)
 * @param makeInvisible Whether to make the player invisible during camera control
 * @param makeInvulnerable Whether to make the player invulnerable during camera control
 * @param hideFromOthers Whether to hide the player from other players during camera control
 */
class CameraAction(
    private val path: List<CameraPathPoint>,
    private val interpolationTicks: Int = CameraEntity.DEFAULT_INTERPOLATION,
    private val makeInvisible: Boolean = true,
    private val makeInvulnerable: Boolean = true,
    private val hideFromOthers: Boolean = true
) : AnimationAction {

    // Calculate total duration based on path segments
    // Each segment = transitionTicks (per-point or default) + holdTicks
    override val durationTicks: Int = if (path.size <= 1) {
        path.firstOrNull()?.holdTicks ?: 0
    } else {
        // For N points, we have N-1 transitions
        // Each transition uses the point's transitionTicks or the default interpolationTicks
        var total = 0
        for (i in 0 until path.size - 1) {
            total += path[i].holdTicks
            total += path[i].transitionTicks ?: interpolationTicks
        }
        // Add hold time for last point (no transition after it)
        total += path.last().holdTicks
        total
    }

    private var cameraEntity: CameraEntity? = null

    // Pre-resolved path locations for interpolation
    private var resolvedPath: List<Location> = emptyList()
    private var segmentFrames: List<SegmentInfo> = emptyList()

    // Comprehensive state snapshot for restoration (following Typewriter's approach)
    private var stateSnapshot: PlayerStateSnapshot? = null
    private var originalInvulnerable: Boolean = false

    override suspend fun setup(context: AnimationContext) {
        val player = context.player
        val plugin = context.plugin

        if (path.isEmpty()) return

        // Register packet handler
        CameraPacketHandler.register(plugin)

        // Pre-resolve all path locations
        resolvedPath = path.map { resolveLocation(it, context) }
        segmentFrames = buildSegmentFrames()

        // Capture comprehensive state for restoration (following Typewriter's approach)
        stateSnapshot = PlayerStateSnapshot.captureForCamera(
            player = player,
            captureLocation = false,
            captureVelocity = false,
            captureVisibility = hideFromOthers
        )
        originalInvulnerable = player.isInvulnerable

        // Make player invulnerable - most performant approach per Paper docs
        if (makeInvulnerable && !originalInvulnerable) {
            player.isInvulnerable = true
        }

        // Apply camera animation state (invisibility + hide from others)
        if (makeInvisible || hideFromOthers) {
            stateSnapshot?.applyCameraAnimationState(player, plugin)
        }

        // Start camera control (enables Y-offset faking + self-interaction prevention)
        CameraPacketHandler.getInstance(plugin).startCameraControl(player)

        // Create and spawn camera entity at first path point
        val startLocation = resolvedPath.first()
        cameraEntity = CameraEntity(player, interpolationTicks).apply {
            spawn(startLocation)
            startSpectating()
        }
    }

    override suspend fun tick(context: AnimationContext, tick: Int, progress: Float) {
        val camera = cameraEntity ?: return
        if (resolvedPath.isEmpty()) return

        // Interpolate position using Catmull-Rom spline (like Typewriter)
        // This sends a teleport packet EVERY TICK for smooth movement
        val interpolatedLocation = interpolatePosition(tick)
        camera.teleport(interpolatedLocation)
    }
    
    override suspend fun teardown(context: AnimationContext) {
        val player = context.player
        val plugin = context.plugin

        // Wrap each cleanup in runCatching to ensure all cleanup runs even if one fails
        runCatching {
            // Cleanup camera entity first
            cameraEntity?.cleanup()
            cameraEntity = null
        }

        runCatching {
            // Stop camera control (disables Y-offset faking)
            CameraPacketHandler.getInstance(plugin).stopCameraControl(player)
        }

        runCatching {
            // Restore original player state using comprehensive snapshot
            if (makeInvulnerable && !originalInvulnerable) {
                player.isInvulnerable = false
            }

            // Restore visibility and invisibility state from snapshot
            stateSnapshot?.restore(
                player = player,
                plugin = plugin,
                restoreLocation = false,
                restoreVelocity = false,
                restoreVisibility = hideFromOthers
            )
            stateSnapshot = null
        }
    }
    
    /**
     * Resolve a path point to an actual location.
     */
    private fun resolveLocation(point: CameraPathPoint, context: AnimationContext): Location {
        return when (point.type) {
            PathPointType.ABSOLUTE -> point.location!!
            PathPointType.RELATIVE_TO_START -> {
                context.startLocation.clone().add(point.offset!!)
            }
            PathPointType.RELATIVE_TO_TARGET -> {
                context.targetLocation?.clone()?.add(point.offset!!)
                    ?: context.startLocation.clone().add(point.offset!!)
            }
            PathPointType.PLAYER_LOCATION -> context.player.location.clone()
            PathPointType.TARGET_LOCATION -> {
                context.targetLocation?.clone() ?: context.player.location.clone()
            }
            PathPointType.BOOST_TOWARD_TARGET -> {
                // offset.x = distance toward target, offset.y = height offset from START
                val offset = point.offset ?: return context.player.location.clone()
                val distance = offset.x
                val heightOffset = offset.y

                val target = context.targetLocation ?: return context.startLocation.clone()
                val start = context.startLocation.clone()

                // Calculate HORIZONTAL direction toward target (XZ plane only)
                // This prevents the camera from dropping when target is lower
                val horizontalDir = target.toVector().subtract(start.toVector())
                horizontalDir.y = 0.0  // Zero out Y for horizontal-only movement
                if (horizontalDir.lengthSquared() > 0) {
                    horizontalDir.normalize()
                }

                // Move 'distance' blocks horizontally toward target
                val result = start.clone()
                result.add(horizontalDir.multiply(distance))
                // Height is relative to START position, not affected by target's Y
                result.y = start.y + heightOffset

                // Calculate yaw/pitch to look toward target from boost position
                val lookDir = target.toVector().subtract(result.toVector())
                result.yaw = Math.toDegrees(Math.atan2(-lookDir.x, lookDir.z)).toFloat()
                result.pitch = Math.toDegrees(-Math.atan2(lookDir.y, Math.sqrt(lookDir.x * lookDir.x + lookDir.z * lookDir.z))).toFloat()
                result
            }
            PathPointType.LAUNCH_UP_FACING_TARGET -> {
                // Position: Above start location (offset.y = height)
                // Rotation: Already facing the target (Gundam lock-on feel)
                val offset = point.offset ?: return context.player.location.clone()
                val height = offset.y

                val target = context.targetLocation ?: return context.startLocation.clone()
                val result = context.startLocation.clone().add(0.0, height, 0.0)

                // Calculate yaw/pitch to look toward target from this elevated position
                val lookDir = target.toVector().subtract(result.toVector())
                result.yaw = Math.toDegrees(Math.atan2(-lookDir.x, lookDir.z)).toFloat()
                result.pitch = Math.toDegrees(-Math.atan2(lookDir.y, Math.sqrt(lookDir.x * lookDir.x + lookDir.z * lookDir.z))).toFloat()
                result
            }
        }
    }

    /**
     * Build segment frame information for interpolation.
     * Each segment represents the transition from one point to the next.
     */
    private fun buildSegmentFrames(): List<SegmentInfo> {
        if (resolvedPath.size <= 1) {
            return listOf(SegmentInfo(0, durationTicks, 0, interpolationTicks))
        }

        val segments = mutableListOf<SegmentInfo>()
        var currentFrame = 0

        for (i in 0 until path.size - 1) {
            val holdTicks = path[i].holdTicks
            val segmentTransitionTicks = path[i].transitionTicks ?: interpolationTicks
            val segmentDuration = segmentTransitionTicks + holdTicks
            segments.add(SegmentInfo(currentFrame, currentFrame + segmentDuration, i, segmentTransitionTicks))
            currentFrame += segmentDuration
        }

        // Last point just holds
        val lastHold = path.last().holdTicks
        if (lastHold > 0) {
            segments.add(SegmentInfo(currentFrame, currentFrame + lastHold, path.size - 1, 0))
        }

        return segments
    }

    /**
     * Interpolate camera position at the given tick using Catmull-Rom spline.
     * Based on Typewriter's approach for smooth camera movement.
     */
    private fun interpolatePosition(tick: Int): Location {
        if (resolvedPath.size == 1) {
            return resolvedPath.first().clone()
        }

        // Find which segment we're in
        val segment = segmentFrames.find { tick >= it.startFrame && tick < it.endFrame }
            ?: return resolvedPath.last().clone()

        val pointIndex = segment.pointIndex
        val currentPoint = resolvedPath[pointIndex]
        val nextPoint = resolvedPath.getOrElse(pointIndex + 1) { currentPoint }

        // Calculate progress within this segment
        val segmentTick = tick - segment.startFrame
        val holdTicks = path.getOrNull(pointIndex)?.holdTicks ?: 0

        // During hold phase, stay at current point
        if (segmentTick < holdTicks) {
            return currentPoint.clone()
        }

        // During transition phase, interpolate to next point
        val transitionTick = segmentTick - holdTicks
        val segmentTransitionTicks = segment.transitionTicks
        if (segmentTransitionTicks <= 0) {
            return currentPoint.clone()
        }
        val progress = (transitionTick.toDouble() / segmentTransitionTicks).coerceIn(0.0, 1.0)

        // Use Catmull-Rom for smooth curves (like Typewriter)
        val prevPoint = resolvedPath.getOrElse(pointIndex - 1) { currentPoint }
        val nextNextPoint = resolvedPath.getOrElse(pointIndex + 2) { nextPoint }

        return catmullRomInterpolate(prevPoint, currentPoint, nextPoint, nextNextPoint, progress)
    }

    /**
     * Catmull-Rom spline interpolation for smooth camera curves.
     * Based on Typewriter's interpolatePoints function.
     */
    private fun catmullRomInterpolate(
        p0: Location, p1: Location, p2: Location, p3: Location, t: Double
    ): Location {
        val t2 = t * t
        val t3 = t2 * t

        // Catmull-Rom coefficients
        val x = 0.5 * ((2 * p1.x) +
                (-p0.x + p2.x) * t +
                (2 * p0.x - 5 * p1.x + 4 * p2.x - p3.x) * t2 +
                (-p0.x + 3 * p1.x - 3 * p2.x + p3.x) * t3)

        val y = 0.5 * ((2 * p1.y) +
                (-p0.y + p2.y) * t +
                (2 * p0.y - 5 * p1.y + 4 * p2.y - p3.y) * t2 +
                (-p0.y + 3 * p1.y - 3 * p2.y + p3.y) * t3)

        val z = 0.5 * ((2 * p1.z) +
                (-p0.z + p2.z) * t +
                (2 * p0.z - 5 * p1.z + 4 * p2.z - p3.z) * t2 +
                (-p0.z + 3 * p1.z - 3 * p2.z + p3.z) * t3)

        // Linear interpolation for yaw and pitch (smoother for rotation)
        val yaw = lerpAngle(p1.yaw, p2.yaw, t.toFloat())
        val pitch = lerp(p1.pitch, p2.pitch, t.toFloat())

        return Location(p1.world, x, y, z, yaw, pitch)
    }

    /** Linear interpolation */
    private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

    /** Linear interpolation for angles (handles wraparound) */
    private fun lerpAngle(a: Float, b: Float, t: Float): Float {
        var delta = ((b - a) % 360 + 540) % 360 - 180
        return a + delta * t
    }

    /** Segment information for interpolation */
    private data class SegmentInfo(
        val startFrame: Int,
        val endFrame: Int,
        val pointIndex: Int,
        val transitionTicks: Int  // Per-segment transition duration
    )
}

