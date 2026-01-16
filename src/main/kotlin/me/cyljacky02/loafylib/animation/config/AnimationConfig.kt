package me.cyljacky02.loafylib.animation.config

import me.cyljacky02.loafylib.animation.actions.*
import me.cyljacky02.loafylib.animation.camera.CameraAction
import me.cyljacky02.loafylib.animation.camera.CameraPathPoint
import me.cyljacky02.loafylib.animation.core.AnimationAction
import me.cyljacky02.loafylib.animation.core.AnimationSequence
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.SoundCategory
import org.bukkit.util.Vector
import org.spongepowered.configurate.objectmapping.ConfigSerializable

/**
 * YAML-serializable animation sequence definition.
 *
 * Example YAML:
 * ```yaml
 * animations:
 *   rtp-launch:
 *     description: "Launch animation for RTP"
 *     actions:
 *       - type: freeze
 *         duration: 10
 *       - type: sound
 *         sound: ENTITY_ENDER_DRAGON_FLAP
 *         volume: 0.5
 *       - type: velocity
 *         direction: UP
 *         speed: 2.0
 *         duration: 20
 *         easing: EASE_OUT_CUBIC
 * ```
 */
@ConfigSerializable
data class AnimationConfig(
    val description: String = "",
    val actions: List<ActionConfig> = emptyList()
) {
    /**
     * Convert this config to an AnimationSequence.
     *
     * @param id The animation ID (from the config key)
     * @return The built AnimationSequence
     * @throws IllegalArgumentException if the config is invalid
     */
    fun toSequence(id: String): AnimationSequence {
        require(actions.isNotEmpty()) { "Animation '$id' must have at least one action" }
        return AnimationSequence(
            id = id,
            description = description,
            actions = actions.map { it.toAction() }
        )
    }
}

/**
 * YAML-serializable action definition.
 *
 * Uses a discriminated union pattern with the 'type' field.
 */
@ConfigSerializable
data class ActionConfig(
    // Common
    val type: String = "",
    val duration: Int? = null,

    // Particles
    val particle: String? = null,
    val count: Int? = null,
    val spread: Double? = null,
    val particleSpeed: Double? = null,
    val trail: Boolean? = null,

    // Sound
    val sound: String? = null,
    val volume: Float? = null,
    val pitch: Float? = null,
    val category: String? = null,

    // Title
    val title: String? = null,
    val subtitle: String? = null,
    val fadeIn: Int? = null,
    val stay: Int? = null,
    val fadeOut: Int? = null,

    // Camera shake
    val intensity: Float? = null,

    // Look at
    val target: String? = null,
    val smooth: Boolean? = null,

    // Composite/parallel
    val actions: List<ActionConfig>? = null,

    // Camera path
    val path: List<CameraPathConfig>? = null,
    val interpolationTicks: Int? = null,
    val makeInvisible: Boolean? = null,
    val makeInvulnerable: Boolean? = null
) {
    /**
     * Convert this config to an AnimationAction.
     *
     * @return The built AnimationAction
     * @throws IllegalArgumentException if the config is invalid
     */
    fun toAction(): AnimationAction = when (type.lowercase()) {
        "freeze" -> FreezeAction(
            durationTicks = requireDuration("freeze")
        )

        "particles" -> ParticleAction(
            particle = Particle.valueOf(
                requireNotNull(particle) { "particles action requires 'particle'" }.uppercase()
            ),
            count = count ?: 10,
            spread = spread ?: 0.5,
            speed = particleSpeed ?: 0.0,
            trail = trail ?: false,
            durationTicks = duration ?: 0
        )

        "sound" -> SoundAction(
            sound = Sound.valueOf(
                requireNotNull(sound) { "sound action requires 'sound'" }.uppercase()
            ),
            volume = volume ?: 1.0f,
            pitch = pitch ?: 1.0f,
            category = category?.let { SoundCategory.valueOf(it.uppercase()) } ?: SoundCategory.MASTER
        )

        "title" -> TitleAction(
            title = title ?: "",
            subtitle = subtitle ?: "",
            fadeInTicks = fadeIn ?: 10,
            stayTicks = stay ?: 70,
            fadeOutTicks = fadeOut ?: 20
        )

        "delay" -> DelayAction(
            durationTicks = requireDuration("delay")
        )

        "camera_shake", "camerashake", "shake" -> CameraShakeAction(
            intensity = intensity ?: 0.5f,
            durationTicks = requireDuration("camera_shake")
        )

        "look_at", "lookat", "look" -> LookAtAction(
            target = target?.let { LookTarget.valueOf(it.uppercase()) } ?: LookTarget.TARGET_LOCATION,
            smooth = smooth ?: true,
            durationTicks = duration ?: 10
        )

        "parallel", "composite" -> CompositeAction(
            actions = requireNotNull(actions) { "parallel action requires 'actions' list" }
                .map { it.toAction() }
        )

        "camera" -> CameraAction(
            path = requireNotNull(path) { "camera action requires 'path' list" }
                .map { it.toPathPoint() },
            interpolationTicks = interpolationTicks ?: 10,
            makeInvisible = makeInvisible ?: true,
            makeInvulnerable = makeInvulnerable ?: true
        )

        else -> throw IllegalArgumentException("Unknown action type: '$type'")
    }

    private fun requireDuration(actionType: String): Int =
        requireNotNull(duration) { "$actionType action requires 'duration'" }
}

/**
 * YAML-serializable camera path point configuration.
 *
 * Example YAML:
 * ```yaml
 * path:
 *   - type: PLAYER_LOCATION
 *     hold: 0
 *   - type: LAUNCH_UP
 *     height: 30
 *     hold: 5
 *   - type: BOOST_TOWARD_TARGET
 *     distance: 50
 *     height: 10
 *     hold: 0
 * ```
 */
@ConfigSerializable
data class CameraPathConfig(
    val type: String = "PLAYER_LOCATION",
    val hold: Int = 0,
    // Per-point transition speed (null = use action's default interpolationTicks)
    val transition: Int? = null,
    // For RELATIVE_TO_START, RELATIVE_TO_TARGET
    val offsetX: Double = 0.0,
    val offsetY: Double = 0.0,
    val offsetZ: Double = 0.0,
    // For LAUNCH_UP helper
    val height: Double = 30.0,
    // For BOOST_TOWARD_TARGET helper
    val distance: Double = 50.0
) {
    /**
     * Convert this config to a CameraPathPoint.
     */
    fun toPathPoint(): CameraPathPoint = when (type.uppercase()) {
        "PLAYER_LOCATION", "PLAYER" -> CameraPathPoint.playerLocation(hold, transition)
        "TARGET_LOCATION", "TARGET" -> CameraPathPoint.targetLocation(hold, transition)
        "RELATIVE_TO_START", "RELATIVE_START" -> CameraPathPoint.relativeToStart(
            Vector(offsetX, offsetY, offsetZ), hold, transition
        )
        "RELATIVE_TO_TARGET", "RELATIVE_TARGET" -> CameraPathPoint.relativeToTarget(
            Vector(offsetX, offsetY, offsetZ), hold, transition
        )
        "LAUNCH_UP", "LAUNCH" -> CameraPathPoint.launchUp(height, hold, transition)
        "LAUNCH_UP_FACING_TARGET", "LAUNCH_FACING_TARGET", "LAUNCH_LOCK_ON" ->
            CameraPathPoint.launchUpFacingTarget(height, hold, transition)
        "BOOST_TOWARD_TARGET", "BOOST" -> CameraPathPoint.boostTowardTarget(distance, height, hold, transition)
        "HOVER" -> CameraPathPoint.hover(hold, transition)
        else -> throw IllegalArgumentException("Unknown camera path type: '$type'")
    }
}

