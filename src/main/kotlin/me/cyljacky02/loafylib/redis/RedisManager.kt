package me.cyljacky02.loafylib.redis

import io.lettuce.core.ScriptOutputType
import io.lettuce.core.api.async.RedisAsyncCommands
import kotlinx.coroutines.flow.Flow
import me.cyljacky02.loafylib.plugin.PluginComponent

/**
 * Interface for Redis connection management and command execution.
 *
 * Provides async Redis operations using Lettuce with:
 * - String keys and ByteArray values codec for binary serialization
 * - Pub/sub support with automatic resubscription on reconnect
 * - Pipelining for batch operations
 * - Lua script execution for atomic operations
 * - SCAN iteration via Kotlin Flow
 *
 * Implementations should use Lettuce's built-in ConnectionWatchdog for
 * automatic reconnection with exponential backoff.
 *
 * @see LettuceRedisManager for the default implementation
 */
interface RedisManager : PluginComponent {

    /**
     * Whether the Redis connection is currently established and open.
     */
    val isConnected: Boolean

    /**
     * Establishes connection to Redis server.
     * This is called by [initialize] as part of the PluginComponent lifecycle.
     *
     * @throws RedisConnectionException if initial connection fails
     */
    suspend fun connect()

    /**
     * Gracefully closes all Redis connections.
     *
     * Unlike [shutdown], this preserves ClientResources for potential reconnection.
     */
    suspend fun disconnect()

    /**
     * Initializes the Redis connection.
     * Default implementation calls [connect].
     */
    override suspend fun initialize() {
        connect()
    }

    /**
     * Performs full shutdown including ClientResources.
     *
     * Call this when the application is terminating (e.g., plugin disable).
     * Unlike [disconnect], this also releases thread pools and event loops.
     */
    override suspend fun shutdown()

    /**
     * Executes a single Redis command asynchronously.
     *
     * Lettuce buffers commands during temporary disconnections when
     * autoReconnect is enabled (default). Commands will be sent after reconnection.
     *
     * @param command The command to execute using RedisAsyncCommands
     * @return The result of the command
     * @throws RedisConnectionException if client not initialized or command fails
     */
    suspend fun <T> execute(command: suspend RedisAsyncCommands<String, ByteArray>.() -> T): T

    /**
     * Executes a Lua script atomically on the Redis server.
     *
     * Lua scripts execute atomically - all Redis operations within the script
     * complete as a single unit with no interleaving from other clients.
     *
     * ## Output Types
     *
     * Choose the appropriate [ScriptOutputType] based on your script's return value:
     * - `BOOLEAN` - Script returns true/false (Lua boolean or 0/1)
     * - `INTEGER` - Script returns a number (e.g., `return 1` for CAS operations)
     * - `STATUS` - Script returns a simple string status (e.g., "OK")
     * - `VALUE` - Script returns a single bulk string value
     * - `MULTI` - Script returns an array/table (default, most flexible)
     *
     * Using the correct output type avoids unnecessary type coercion in the resultMapper.
     *
     * @param script The Lua script to execute
     * @param keys List of Redis keys accessed by the script (passed as KEYS[1], KEYS[2], etc.)
     * @param args List of arguments passed to the script (passed as ARGV[1], ARGV[2], etc.)
     * @param outputType The expected script output type (defaults to MULTI for flexibility)
     * @param resultMapper Function to convert the raw script result to the desired type
     * @return The mapped result from the script execution
     * @throws RedisScriptException if script execution fails
     * @throws RedisConnectionException if not connected
     */
    suspend fun <T> evalScript(
        script: String,
        keys: List<String>,
        args: List<ByteArray>,
        outputType: ScriptOutputType = ScriptOutputType.MULTI,
        resultMapper: (Any?) -> T
    ): T

    /**
     * Executes multiple Redis commands in a pipeline for batch operations.
     *
     * Lettuce handles pipelining natively - async commands are batched at the
     * network layer automatically.
     *
     * @param commands The commands to execute in the pipeline
     * @throws RedisConnectionException if not connected
     * @throws RedisPipelineException if any command in the pipeline fails
     */
    suspend fun pipeline(commands: suspend RedisPipeline.() -> Unit)

    /**
     * Subscribes to a Redis pub/sub channel.
     *
     * Lettuce automatically resubscribes to channels after reconnection.
     *
     * @param channel The channel name to subscribe to
     * @param handler Callback invoked when a message is received
     * @throws RedisConnectionException if subscription fails
     */
    suspend fun subscribe(channel: String, handler: (ByteArray) -> Unit)

    /**
     * Unsubscribes from a Redis pub/sub channel.
     *
     * @param channel The channel name to unsubscribe from
     */
    suspend fun unsubscribe(channel: String)

    /**
     * Publishes a message to a Redis pub/sub channel.
     *
     * @param channel The channel name to publish to
     * @param message The message bytes to publish
     * @throws RedisConnectionException if not connected
     */
    suspend fun publish(channel: String, message: ByteArray)

    /**
     * Returns a Flow that transparently handles Redis SCAN cursor iteration.
     *
     * Example usage:
     * ```kotlin
     * redisManager.scanFlow("player:*").collect { key -> processKey(key) }
     * ```
     *
     * @param pattern The pattern to match keys against (e.g., "player:*")
     * @param countHint Hint for number of keys to return per iteration (default 100)
     * @return Flow emitting each matching key
     * @throws RedisConnectionException if not connected
     */
    fun scanFlow(pattern: String, countHint: Int = 100): Flow<String>

    /**
     * Registers a callback to be invoked when Redis reconnects.
     *
     * Use this for application-level recovery tasks like re-registering
     * player sessions. Lettuce handles connection and pub/sub recovery
     * automatically - these callbacks are for your application state.
     *
     * ## Memory Leak Prevention
     *
     * The returned [AutoCloseable] **MUST** be closed when the callback is no longer needed,
     * otherwise the callback remains registered indefinitely, causing a memory leak.
     *
     * ## Usage Example
     *
     * ```kotlin
     * class MyPlugin : LoafyPlugin() {
     *     private var reconnectHandle: AutoCloseable? = null
     *
     *     override fun onPluginEnable() {
     *         val redis = registry.get<RedisManager>()
     *
     *         // Register reconnect callback and store the handle
     *         reconnectHandle = redis.onReconnect {
     *             // Re-register all online players after Redis reconnects
     *             server.onlinePlayers.forEach { player ->
     *                 sessionService.register(player.uniqueId, player.name)
     *             }
     *         }
     *     }
     *
     *     override fun onPluginDisable() {
     *         // CRITICAL: Close the handle to prevent memory leaks
     *         reconnectHandle?.close()
     *         reconnectHandle = null
     *     }
     * }
     * ```
     *
     * @param callback The suspend callback to invoke on reconnection
     * @return An AutoCloseable handle to unregister the callback - MUST be closed in onPluginDisable()
     */
    fun onReconnect(callback: suspend () -> Unit): AutoCloseable
}
