package me.cyljacky02.loafylib.redis

import io.lettuce.core.RedisFuture
import io.lettuce.core.api.async.RedisAsyncCommands

/**
 * Interface for Redis pipeline operations.
 *
 * Allows batching multiple Redis commands to minimize network round trips.
 * Commands are queued and executed together when the pipeline block completes.
 *
 * **Note on Pipelining Behavior:**
 * This implementation uses Lettuce's default "async burst" mode where commands
 * are sent immediately but responses are collected together. This is safer for
 * shared connections than true pipelining with `setAutoFlushCommands(false)`,
 * which is not thread-safe on shared connections.
 *
 * For most use cases, the typed methods (set, get, hset, etc.) are recommended.
 * For commands not covered by the interface, use [add] to access any Redis command.
 */
interface RedisPipeline {

    // ==================== String Operations ====================

    /**
     * Sets a key-value pair with optional TTL.
     */
    fun set(key: String, value: ByteArray, ttlSeconds: Long? = null): RedisFuture<String>

    /**
     * Sets a key-value pair with TTL using SETEX.
     */
    fun setex(key: String, ttlSeconds: Long, value: ByteArray): RedisFuture<String>

    /**
     * Sets a key only if it does not exist (SETNX).
     * Returns true if the key was set, false if it already existed.
     */
    fun setnx(key: String, value: ByteArray): RedisFuture<Boolean>

    /**
     * Gets a value by key.
     */
    fun get(key: String): RedisFuture<ByteArray?>

    /**
     * Gets the value of a key and deletes it atomically (GETDEL).
     * Returns null if the key does not exist.
     */
    fun getdel(key: String): RedisFuture<ByteArray?>

    // ==================== Key Operations ====================

    /**
     * Deletes one or more keys.
     */
    fun del(vararg keys: String): RedisFuture<Long>

    /**
     * Sets the TTL for a key.
     */
    fun expire(key: String, ttlSeconds: Long): RedisFuture<Boolean>

    /**
     * Checks if keys exist.
     */
    fun exists(vararg keys: String): RedisFuture<Long>

    // ==================== Hash Operations ====================

    /**
     * Sets a field in a hash.
     */
    fun hset(key: String, field: String, value: ByteArray): RedisFuture<Boolean>

    /**
     * Sets multiple fields in a hash.
     */
    fun hmset(key: String, map: Map<String, ByteArray>): RedisFuture<String>

    /**
     * Gets a field from a hash.
     */
    fun hget(key: String, field: String): RedisFuture<ByteArray?>

    /**
     * Gets all fields and values from a hash.
     */
    fun hgetall(key: String): RedisFuture<Map<String, ByteArray>>

    /**
     * Deletes fields from a hash.
     */
    fun hdel(key: String, vararg fields: String): RedisFuture<Long>

    // ==================== List Operations ====================

    /**
     * Pushes values to the left (head) of a list.
     */
    fun lpush(key: String, vararg values: ByteArray): RedisFuture<Long>

    /**
     * Pushes values to the right (tail) of a list.
     */
    fun rpush(key: String, vararg values: ByteArray): RedisFuture<Long>

    /**
     * Gets a range of elements from a list.
     */
    fun lrange(key: String, start: Long, stop: Long): RedisFuture<List<ByteArray>>

    // ==================== Set Operations ====================

    /**
     * Adds members to a set.
     */
    fun sadd(key: String, vararg members: ByteArray): RedisFuture<Long>

    /**
     * Gets all members of a set.
     */
    fun smembers(key: String): RedisFuture<Set<ByteArray>>

    /**
     * Removes members from a set.
     */
    fun srem(key: String, vararg members: ByteArray): RedisFuture<Long>

    // ==================== Sorted Set Operations ====================

    /**
     * Adds members to a sorted set with scores.
     */
    fun zadd(key: String, score: Double, member: ByteArray): RedisFuture<Long>

    /**
     * Gets the rank of a member in a sorted set (0-based, lowest score first).
     * Returns null if the member does not exist.
     */
    fun zrank(key: String, member: ByteArray): RedisFuture<Long?>

    /**
     * Gets the reverse rank of a member in a sorted set (0-based, highest score first).
     * Returns null if the member does not exist.
     */
    fun zrevrank(key: String, member: ByteArray): RedisFuture<Long?>

    /**
     * Gets the score of a member in a sorted set.
     * Returns null if the member does not exist.
     */
    fun zscore(key: String, member: ByteArray): RedisFuture<Double?>

    /**
     * Removes members from a sorted set.
     */
    fun zrem(key: String, vararg members: ByteArray): RedisFuture<Long>

    /**
     * Gets members from a sorted set by score range.
     */
    fun zrangebyscore(key: String, min: Double, max: Double): RedisFuture<List<ByteArray>>

    /**
     * Removes members from a sorted set by score range.
     */
    fun zremrangebyscore(key: String, min: Double, max: Double): RedisFuture<Long>

    /**
     * Gets the number of members in a sorted set.
     */
    fun zcard(key: String): RedisFuture<Long>

    // ==================== Generic Command Access ====================

    /**
     * Executes any Redis command and tracks its future for pipeline completion.
     *
     * Use this for commands not covered by the typed interface methods.
     * Example:
     * ```kotlin
     * redisManager.pipeline {
     *     set("key1", value1)
     *     add { zincrby("leaderboard", 1.0, "player1".toByteArray()) }
     * }
     * ```
     */
    fun <T> add(command: RedisAsyncCommands<String, ByteArray>.() -> RedisFuture<T>): RedisFuture<T>
}
