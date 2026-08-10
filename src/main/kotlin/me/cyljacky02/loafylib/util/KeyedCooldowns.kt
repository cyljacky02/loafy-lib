package me.cyljacky02.loafylib.util

import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Generic cooldown tracker for any key type.
 *
 * Thread-safe cooldown management that tracks expiration times by arbitrary keys.
 * Useful for rate-limiting, spam protection, or any scenario where you need to
 * track "has this thing happened recently?"
 *
 * ## Usage
 *
 * ```kotlin
 * // Simple string keys
 * val cooldowns = KeyedCooldowns<String>()
 * cooldowns.setCooldown("action_a", 5.seconds)
 * if (cooldowns.isOnCooldown("action_a")) { ... }
 *
 * // Composite keys for per-player, per-item tracking
 * data class PlayerItemKey(val playerId: UUID, val itemId: Int)
 * val tracker = KeyedCooldowns<PlayerItemKey>()
 * tracker.setCooldown(PlayerItemKey(player.uniqueId, itemId), 10.seconds)
 * ```
 *
 * @param K The key type (must have proper equals/hashCode, e.g., data class)
 */
class KeyedCooldowns<K : Any> {

    /**
     * Maps keys to their expiration timestamp (milliseconds since epoch).
     */
    private val expirations = ConcurrentHashMap<K, Long>()

    /**
     * Checks if a key is currently on cooldown.
     *
     * @param key The key to check
     * @return true if on cooldown (not yet expired), false otherwise
     */
    fun isOnCooldown(key: K): Boolean {
        val expiration = expirations[key] ?: return false
        if (System.currentTimeMillis() >= expiration) {
            // Expired - clean up lazily
            expirations.remove(key, expiration)
            return false
        }
        return true
    }

    /**
     * Sets a cooldown for a key.
     *
     * @param key The key to set cooldown for
     * @param duration How long the cooldown should last
     */
    fun setCooldown(key: K, duration: Duration) {
        val expiration = System.currentTimeMillis() + duration.inWholeMilliseconds
        expirations[key] = expiration
    }

    /**
     * Sets a permanent "cooldown" that never expires.
     * Useful for one-time-only operations.
     *
     * @param key The key to mark as permanently used
     */
    fun setPermanent(key: K) {
        expirations[key] = Long.MAX_VALUE
    }

    /**
     * Checks if a key has been permanently marked.
     *
     * @param key The key to check
     * @return true if permanently marked, false otherwise
     */
    fun isPermanent(key: K): Boolean {
        return expirations[key] == Long.MAX_VALUE
    }

    /**
     * Gets the remaining cooldown duration, or null if not on cooldown.
     *
     * @param key The key to check
     * @return Remaining duration, or null if no active cooldown
     */
    fun getRemainingCooldown(key: K): Duration? {
        val expiration = expirations[key] ?: return null
        if (expiration == Long.MAX_VALUE) return Duration.INFINITE
        val remaining = expiration - System.currentTimeMillis()
        return if (remaining > 0) remaining.milliseconds else null
    }

    /**
     * Clears a specific cooldown.
     *
     * @param key The key to clear
     */
    fun clear(key: K) {
        expirations.remove(key)
    }

    /**
     * Clears all cooldowns matching a predicate.
     *
     * @param predicate Function that returns true for keys to remove
     */
    fun clearMatching(predicate: (K) -> Boolean) {
        expirations.keys.removeIf(predicate)
    }

    /**
     * Clears all expired entries.
     * Called automatically on access, but can be invoked manually for batch cleanup.
     */
    fun cleanupExpired() {
        val now = System.currentTimeMillis()
        expirations.entries.removeIf { (_, expiration) ->
            expiration != Long.MAX_VALUE && now >= expiration
        }
    }

    /**
     * Clears all cooldowns.
     */
    fun clearAll() {
        expirations.clear()
    }

    /**
     * Returns the number of tracked entries (including expired ones not yet cleaned).
     */
    val size: Int get() = expirations.size
}
