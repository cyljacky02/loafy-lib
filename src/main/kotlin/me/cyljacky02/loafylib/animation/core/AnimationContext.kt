package me.cyljacky02.loafylib.animation.core

import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import org.bukkit.util.Vector

/**
 * Context passed to all animation actions during execution.
 *
 * Contains the player, plugin reference, locations, and custom parameters.
 * Properties are immutable, but actions can store temporary state via [setParam]/[getParam].
 *
 * @property player The player being animated
 * @property plugin The plugin executing the animation
 * @property startLocation The player's location when the animation started
 * @property targetLocation Optional target location for directional animations
 * @property parameters Custom parameters accessible by actions
 * @property provider The animation provider for effects
 */
data class AnimationContext(
    val player: Player,
    val plugin: Plugin,
    val startLocation: Location,
    val targetLocation: Location? = null,
    val parameters: Map<String, Any> = emptyMap(),
    val provider: AnimationProvider
) {
    // Mutable state storage for actions to use during animation execution
    // This allows actions to store state without having mutable instance variables
    // Uses ConcurrentHashMap for thread-safety in case of future parallel action execution
    private val actionState: MutableMap<String, Any> = java.util.concurrent.ConcurrentHashMap()
    
    /**
     * Get a typed parameter with a default value.
     *
     * @param key The parameter key
     * @param default The default value if key is missing or wrong type
     * @return The parameter value or default
     */
    inline fun <reified T> param(key: String, default: T): T =
        parameters[key] as? T ?: default

    /**
     * Get a typed parameter or null if missing/wrong type.
     *
     * @param key The parameter key
     * @return The parameter value or null
     */
    inline fun <reified T> paramOrNull(key: String): T? =
        parameters[key] as? T

    /**
     * Store action-specific state during animation execution.
     * This allows stateless action data classes to store temporary state.
     *
     * @param key The state key (recommend prefixing with action name)
     * @param value The state value
     */
    fun setParam(key: String, value: Any) {
        actionState[key] = value
    }

    /**
     * Get action-specific state stored during animation execution.
     */
    @Suppress("UNCHECKED_CAST")
    fun <T> getParam(key: String): T? =
        actionState[key] as? T

    /**
     * Direction vector from start location to target location (normalized).
     * Returns null if no target location is set.
     */
    val directionToTarget: Vector?
        get() = targetLocation?.let {
            it.toVector().subtract(startLocation.toVector()).normalize()
        }

    /**
     * Distance from start location to target location.
     * Returns null if no target location is set.
     */
    val distanceToTarget: Double?
        get() = targetLocation?.distance(startLocation)
}

