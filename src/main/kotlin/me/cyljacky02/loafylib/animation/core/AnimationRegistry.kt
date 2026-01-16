package me.cyljacky02.loafylib.animation.core

import java.util.concurrent.ConcurrentHashMap

/**
 * Global registry for named animation sequences.
 *
 * Allows animations to be registered by ID and retrieved later.
 * Thread-safe for concurrent access.
 */
class AnimationRegistry {
    private val animations = ConcurrentHashMap<String, AnimationSequence>()

    /**
     * Register an animation sequence.
     *
     * @param sequence The sequence to register
     * @throws IllegalArgumentException if an animation with this ID already exists
     */
    fun register(sequence: AnimationSequence) {
        val existing = animations.putIfAbsent(sequence.id, sequence)
        require(existing == null) { "Animation '${sequence.id}' is already registered" }
    }

    /**
     * Register an animation sequence, replacing any existing one with the same ID.
     *
     * @param sequence The sequence to register
     */
    fun registerOrReplace(sequence: AnimationSequence) {
        animations[sequence.id] = sequence
    }

    /**
     * Get an animation sequence by ID.
     *
     * @param id The animation ID
     * @return The animation sequence, or null if not found
     */
    fun get(id: String): AnimationSequence? = animations[id]

    /**
     * Get an animation sequence by ID, throwing if not found.
     *
     * @param id The animation ID
     * @return The animation sequence
     * @throws NoSuchElementException if not found
     */
    fun getOrThrow(id: String): AnimationSequence =
        animations[id] ?: throw NoSuchElementException("Animation '$id' not found")

    /**
     * Check if an animation is registered.
     *
     * @param id The animation ID
     * @return true if registered
     */
    fun contains(id: String): Boolean = animations.containsKey(id)

    /**
     * Unregister an animation sequence.
     *
     * @param id The animation ID to remove
     * @return The removed sequence, or null if not found
     */
    fun unregister(id: String): AnimationSequence? = animations.remove(id)

    /**
     * Get all registered animation IDs.
     *
     * @return Set of all animation IDs
     */
    fun getAllIds(): Set<String> = animations.keys.toSet()

    /**
     * Get all registered animations.
     *
     * @return Collection of all animation sequences
     */
    fun getAll(): Collection<AnimationSequence> = animations.values.toList()

    /**
     * Clear all registered animations.
     */
    fun clear() = animations.clear()

    /**
     * Number of registered animations.
     */
    val size: Int get() = animations.size
}

