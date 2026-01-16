package me.cyljacky02.loafylib.animation.dsl

import me.cyljacky02.loafylib.animation.actions.*
import me.cyljacky02.loafylib.animation.camera.CameraAction
import me.cyljacky02.loafylib.animation.camera.CameraEntity
import me.cyljacky02.loafylib.animation.camera.CameraPathPoint
import me.cyljacky02.loafylib.animation.core.AnimationAction
import me.cyljacky02.loafylib.animation.core.AnimationSequence
import org.bukkit.Location
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.SoundCategory
import org.bukkit.util.Vector

/**
 * DSL marker to prevent scope leakage between nested builders.
 * This ensures you can't accidentally call outer builder methods from inner blocks.
 */
@DslMarker
annotation class AnimationDsl

/**
 * Create an animation sequence using the type-safe DSL.
 *
 * Example usage:
 * ```kotlin
 * val anim = animation("epic-launch") {
 *     description = "Epic camera launch animation"
 *
 *     freeze(10)
 *     sound(Sound.ENTITY_ENDER_DRAGON_FLAP, volume = 0.5f)
 *
 *     camera {
 *         interpolation = 10
 *         invisible = true
 *         invulnerable = true
 *
 *         path {
 *             playerLocation(hold = 5)
 *             launchUp(height = 30.0, hold = 20)
 *             boostToward(distance = 50.0, height = 20.0, hold = 15)
 *             targetLocation(hold = 5)
 *         }
 *     }
 *
 *     parallel {
 *         particles(Particle.CLOUD, count = 30, trail = true)
 *         cameraShake(intensity = 0.3f, duration = 20)
 *     }
 *
 *     title("<gold>Teleporting!", fadeIn = 5, stay = 30, fadeOut = 10)
 * }
 * ```
 *
 * @param id Unique identifier for the animation
 * @param block DSL builder block
 * @return The built AnimationSequence
 */
inline fun animation(id: String, block: AnimationBuilder.() -> Unit): AnimationSequence {
    return AnimationBuilder(id).apply(block).build()
}

/**
 * Main builder for animation sequences.
 * Provides type-safe methods for all available animation actions.
 */
@AnimationDsl
class AnimationBuilder @PublishedApi internal constructor(private val id: String) {
    /** Optional description of the animation */
    var description: String = ""

    @PublishedApi
    internal val actions = mutableListOf<AnimationAction>()

    // ============ Basic Actions ============

    /** Freeze player movement for the specified duration. */
    fun freeze(durationTicks: Int) {
        actions += FreezeAction(durationTicks)
    }

    /** Wait without doing anything for the specified duration. */
    fun delay(ticks: Int) {
        actions += DelayAction(ticks)
    }

    // ============ Visual Effects ============

    /** Spawn particles at the player's location. */
    fun particles(
        particle: Particle,
        count: Int = 10,
        spread: Double = 0.5,
        speed: Double = 0.0,
        trail: Boolean = false,
        duration: Int = 0
    ) {
        actions += ParticleAction(particle, count, spread, speed, trail, duration)
    }

    /** Display a title and/or subtitle to the player. */
    fun title(
        title: String = "",
        subtitle: String = "",
        fadeIn: Int = 10,
        stay: Int = 70,
        fadeOut: Int = 20
    ) {
        actions += TitleAction(title, subtitle, fadeIn, stay, fadeOut)
    }

    /** Apply a camera shake effect (requires PacketEvents). */
    fun cameraShake(intensity: Float = 0.5f, duration: Int = 10) {
        actions += CameraShakeAction(intensity, duration)
    }

    // ============ Audio ============

    /** Play a sound to the player. */
    fun sound(
        sound: Sound,
        volume: Float = 1.0f,
        pitch: Float = 1.0f,
        category: SoundCategory = SoundCategory.MASTER
    ) {
        actions += SoundAction(sound, volume, pitch, category)
    }

    // ============ Player Control ============

    /** Make the player look at a target. */
    fun lookAt(
        target: LookTarget = LookTarget.TARGET_LOCATION,
        smooth: Boolean = true,
        duration: Int = 10
    ) {
        actions += LookAtAction(target, smooth, duration)
    }

    // ============ Camera System (Main Feature) ============

    /**
     * Create a camera animation using a virtual display entity.
     * This is the recommended approach for smooth pre-teleport animations.
     *
     * The player's camera follows a path while their body stays safe.
     */
    inline fun camera(block: CameraBuilder.() -> Unit) {
        actions += CameraBuilder().apply(block).build()
    }

    // ============ Composition ============

    /** Run multiple actions in parallel (simultaneously). */
    inline fun parallel(block: ParallelBuilder.() -> Unit) {
        actions += ParallelBuilder().apply(block).build()
    }

    /** Add a custom action directly. */
    fun action(action: AnimationAction) {
        actions += action
    }

    @PublishedApi
    internal fun build(): AnimationSequence {
        require(actions.isNotEmpty()) { "Animation must have at least one action" }
        return AnimationSequence(id, description, actions.toList())
    }
}

/**
 * Builder for camera-based animations.
 * Creates smooth camera paths using packet-based display entities.
 */
@AnimationDsl
class CameraBuilder @PublishedApi internal constructor() {
    /** Client-side interpolation duration in ticks (1-59, default 10). */
    var interpolation: Int = CameraEntity.DEFAULT_INTERPOLATION

    /** Whether to make the player invisible during camera control. */
    var invisible: Boolean = true

    /** Whether to make the player invulnerable during camera control. */
    var invulnerable: Boolean = true

    @PublishedApi
    internal val pathPoints = mutableListOf<CameraPathPoint>()

    /** Define the camera path using the path builder. */
    inline fun path(block: CameraPathBuilder.() -> Unit) {
        pathPoints += CameraPathBuilder().apply(block).build()
    }

    /** Add a single path point directly. */
    fun point(point: CameraPathPoint) {
        pathPoints += point
    }

    @PublishedApi
    internal fun build(): CameraAction {
        require(pathPoints.isNotEmpty()) { "Camera action must have at least one path point" }
        return CameraAction(pathPoints.toList(), interpolation, invisible, invulnerable)
    }
}

/**
 * Builder for camera path points.
 * Provides convenient methods for common path patterns.
 */
@AnimationDsl
class CameraPathBuilder @PublishedApi internal constructor() {
    @PublishedApi
    internal val points = mutableListOf<CameraPathPoint>()

    // ============ Location-Based Points ============

    /** Start at the player's current location. */
    fun playerLocation(hold: Int = 0) {
        points += CameraPathPoint.playerLocation(hold)
    }

    /** Move to the target location (teleport destination). */
    fun targetLocation(hold: Int = 0) {
        points += CameraPathPoint.targetLocation(hold)
    }

    /** Position relative to the player's starting location. */
    fun relativeToStart(offset: Vector, hold: Int = 0) {
        points += CameraPathPoint.relativeToStart(offset, hold)
    }

    /** Position relative to the target location. */
    fun relativeToTarget(offset: Vector, hold: Int = 0) {
        points += CameraPathPoint.relativeToTarget(offset, hold)
    }

    /** Use an absolute world location. */
    fun absolute(location: Location, hold: Int = 0) {
        points += CameraPathPoint.absolute(location, hold)
    }

    // ============ Preset Patterns ============

    /** Launch camera straight up from player position. */
    fun launchUp(height: Double = 30.0, hold: Int = 0, transition: Int? = null) {
        points += CameraPathPoint.launchUp(height, hold, transition)
    }

    /** Launch camera up while rotating to face target (Gundam lock-on feel). */
    fun launchUpFacingTarget(height: Double = 30.0, hold: Int = 0, transition: Int? = null) {
        points += CameraPathPoint.launchUpFacingTarget(height, hold, transition)
    }

    /** Boost camera toward target at specified height. */
    fun boostToward(distance: Double = 50.0, height: Double = 20.0, hold: Int = 0, transition: Int? = null) {
        points += CameraPathPoint.boostTowardTarget(distance, height, hold, transition)
    }

    /** Hover in place (useful for dramatic pauses). */
    fun hover(hold: Int = 20, transition: Int? = null) {
        points += CameraPathPoint.hover(hold, transition)
    }

    /** Add a custom path point. */
    fun point(point: CameraPathPoint) {
        points += point
    }

    @PublishedApi
    internal fun build(): List<CameraPathPoint> = points.toList()
}

/**
 * Builder for parallel (composite) actions.
 * All actions added here run simultaneously.
 */
@AnimationDsl
class ParallelBuilder @PublishedApi internal constructor() {
    @PublishedApi
    internal val actions = mutableListOf<AnimationAction>()

    fun particles(
        particle: Particle,
        count: Int = 10,
        spread: Double = 0.5,
        speed: Double = 0.0,
        trail: Boolean = false,
        duration: Int = 0
    ) {
        actions += ParticleAction(particle, count, spread, speed, trail, duration)
    }

    fun sound(sound: Sound, volume: Float = 1.0f, pitch: Float = 1.0f) {
        actions += SoundAction(sound, volume, pitch)
    }

    fun cameraShake(intensity: Float = 0.5f, duration: Int = 10) {
        actions += CameraShakeAction(intensity, duration)
    }

    fun title(
        title: String = "",
        subtitle: String = "",
        fadeIn: Int = 10,
        stay: Int = 70,
        fadeOut: Int = 20
    ) {
        actions += TitleAction(title, subtitle, fadeIn, stay, fadeOut)
    }

    fun action(action: AnimationAction) {
        actions += action
    }

    @PublishedApi
    internal fun build(): CompositeAction {
        require(actions.isNotEmpty()) { "Parallel block must have at least one action" }
        return CompositeAction(actions.toList())
    }
}

