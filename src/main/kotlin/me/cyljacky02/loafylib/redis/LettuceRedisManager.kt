package me.cyljacky02.loafylib.redis

import io.lettuce.core.ClientOptions
import io.lettuce.core.RedisClient
import io.lettuce.core.RedisConnectionStateListener
import io.lettuce.core.RedisURI
import io.lettuce.core.ScanArgs
import io.lettuce.core.ScanCursor
import io.lettuce.core.ScriptOutputType
import io.lettuce.core.SetArgs
import io.lettuce.core.SocketOptions
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.api.async.RedisAsyncCommands
import io.lettuce.core.codec.ByteArrayCodec
import io.lettuce.core.codec.RedisCodec
import io.lettuce.core.codec.StringCodec
import io.lettuce.core.RedisChannelHandler
import io.lettuce.core.RedisFuture
import io.lettuce.core.pubsub.RedisPubSubAdapter
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection
import io.lettuce.core.resource.ClientResources
import io.lettuce.core.resource.DefaultClientResources
import io.netty.resolver.DefaultAddressResolverGroup
import java.net.SocketAddress
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.future.await
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import me.cyljacky02.loafylib.config.RedisConfig
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.logging.Logger

/**
 * Lettuce-based Redis connection manager implementation.
 *
 * Features:
 * - Async connection using StatefulRedisConnection
 * - ByteArray codec for binary serialization (String keys, ByteArray values)
 * - Pipelining support for batch operations
 * - Pub/sub with Lettuce's automatic reconnection and resubscription
 * - Connection state monitoring via RedisConnectionStateListener
 * - Clean shutdown with awaited ClientResources termination (prevents classloader leaks)
 *
 * This implementation trusts Lettuce's built-in ConnectionWatchdog for:
 * - Automatic reconnection with exponential backoff
 * - Automatic pub/sub resubscription after reconnection
 * - Command buffering during temporary disconnections
 *
 * @param config Redis connection configuration
 * @param logger Logger for connection events
 * @see <a href="https://lettuce.io/core/release/reference/#connection-events">Lettuce Connection Events</a>
 */
class LettuceRedisManager(
    private val config: RedisConfig,
    private val logger: Logger
) : RedisManager {

    companion object {
        /** Initial retry delay in milliseconds */
        private const val INITIAL_RETRY_DELAY_MS = 500L

        /** Maximum retry delay in milliseconds (8 seconds) */
        private const val MAX_RETRY_DELAY_MS = 8000L
    }

    // CoroutineScope is recreated on each connect() to support disconnect/reconnect cycles.
    private var coroutineScope: CoroutineScope? = null

    // Custom codec: String keys, ByteArray values
    private val codec: RedisCodec<String, ByteArray> = RedisCodec.of(StringCodec.UTF8, ByteArrayCodec.INSTANCE)

    // ClientResources is expensive to create (thread pools, event loops).
    // We create it once and reuse across reconnection cycles.
    private var clientResources: ClientResources? = null
    private var clientResourcesOwned: Boolean = false
    private var redisClient: RedisClient? = null

    // Connections - marked @Volatile for visibility across threads
    @Volatile private var connection: StatefulRedisConnection<String, ByteArray>? = null
    @Volatile private var pubSubConnection: StatefulRedisPubSubConnection<String, ByteArray>? = null

    // Pub/Sub handlers - Lettuce auto-resubscribes, we just need to route messages
    private val subscriptionHandlers = ConcurrentHashMap<String, (ByteArray) -> Unit>()

    // Application-level callbacks invoked when Redis reconnects
    private val reconnectCallbacks = CopyOnWriteArrayList<suspend () -> Unit>()

    // Connection mutex: prevents double-initialization race condition
    private val connectMutex = Mutex()

    override val isConnected: Boolean
        get() = connection?.isOpen == true


    override suspend fun connect() {
        if (isConnected) {
            logger.info("Redis already connected")
            return
        }

        // Mutex prevents double-initialization if connect() is called concurrently
        connectMutex.withLock {
            // Double-check after acquiring lock
            if (isConnected) {
                logger.info("Redis already connected")
                return
            }

            logger.info("Initializing Redis connection...")

            // Create a fresh CoroutineScope for this connection lifecycle.
            // Uses Dispatchers.IO directly (not asyncDispatcher from scheduler) because:
            // 1. This scope has its own lifecycle independent of plugin scope
            // 2. Must survive plugin scope cancellation for graceful shutdown
            // 3. asyncDispatcher is also backed by Dispatchers.IO, so functionally equivalent
            if (coroutineScope?.isActive != true) {
                coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
            }

            withContext(Dispatchers.IO) {
                // 1. Create client resources if not already created.
                // We use DefaultAddressResolverGroup.INSTANCE instead of Netty's DnsAddressResolverGroup
                // to avoid Netty DNS resolver initialization overhead.
                //
                // Thread pool sizing:
                // - IO threads handle all Netty network I/O (send/receive, pub/sub decode)
                // - Computation threads handle reconnect scheduling, event bus, metrics
                // - Both pools share across all connections (main + pubsub)
                // - Minimum is 2 for both (Lettuce MIN_IO_THREADS / MIN_COMPUTATION_THREADS)
                // - Default poolSize=2 is sufficient for 2 connections with low throughput
                // - NIO-only (no epoll): one EventLoopGroup, threads assigned round-robin
                if (clientResources == null) {
                    // On macOS, disable Lettuce's kqueue transport detection.
                    // Lettuce's ConnectionBuilder blocks Java NIO ExtendedSocketOptions when kqueue
                    // is detected, but kqueue doesn't support extended TCP keepalive (idle/interval/count).
                    // Disabling kqueue forces NIO transport, allowing Java 11+ jdk.net.ExtendedSocketOptions
                    // to apply extended keepalive settings on macOS 10.15+.
                    // Must be set BEFORE DefaultClientResources construction (KqueueProvider caches in static init).
                    if (System.getProperty("os.name")?.lowercase()?.contains("mac") == true) {
                        System.setProperty("io.lettuce.core.kqueue", "false")
                    }

                    clientResources = DefaultClientResources.builder()
                        .ioThreadPoolSize(config.poolSize.coerceAtLeast(2))
                        .computationThreadPoolSize(config.poolSize.coerceAtLeast(2))
                        .addressResolverGroup(DefaultAddressResolverGroup.INSTANCE)
                        .build()
                    clientResourcesOwned = true
                }

                // 2. Build Redis URI (autoReconnect is true by default)
                val uriBuilder = RedisURI.builder()
                    .withHost(config.host)
                    .withPort(config.port)
                    .withDatabase(config.database)
                    .withTimeout(Duration.ofSeconds(config.timeoutSeconds))
                    .withSsl(config.ssl)

                if (config.password.isNotEmpty()) {
                    uriBuilder.withPassword(config.password.toCharArray())
                }

                val redisUri = uriBuilder.build()

                // 3. Create client with TCP keepalive for dead connection detection.
                // Keepalive formula: detection time = idle + interval * count = 15 + 5*3 = 30s
                // Uses Java 11+ ExtendedSocketOptions on NIO (kqueue disabled on macOS, see above).
                // TcpUserTimeoutOptions omitted: requires native epoll transport (excluded from build).
                redisClient = RedisClient.create(clientResources, redisUri).apply {
                    setOptions(ClientOptions.builder()
                        .socketOptions(SocketOptions.builder()
                            .keepAlive(SocketOptions.KeepAliveOptions.builder()
                                .enable()
                                .idle(Duration.ofSeconds(15))
                                .interval(Duration.ofSeconds(5))
                                .count(3)
                                .build())
                            .build())
                        .build())
                }

                // 4. Create main connection with custom codec
                try {
                    connection = redisClient!!.connect(codec)
                } catch (e: Exception) {
                    cleanup()
                    throw RedisConnectionException("Failed to establish Redis connection: ${e.message}", e)
                }

                // 5. Create pub/sub connection
                try {
                    pubSubConnection = redisClient!!.connectPubSub(codec)
                    setupPubSubListener()
                } catch (e: Exception) {
                    cleanup()
                    throw RedisConnectionException("Failed to establish Redis pub/sub connection: ${e.message}", e)
                }

                // 6. Verify connection with PING
                try {
                    val pong = connection!!.async().ping().await()
                    if (pong != "PONG") {
                        cleanup()
                        throw RedisConnectionException("Redis PING failed: expected PONG, got $pong")
                    }
                } catch (e: RedisConnectionException) {
                    throw e
                } catch (e: Exception) {
                    cleanup()
                    throw RedisConnectionException("Redis PING failed: ${e.message}", e)
                }

                // 7. Set up connection state listener AFTER connections are established
                setupConnectionStateListener()

                logger.info("Successfully connected to Redis at ${config.host}:${config.port}")
            }
        }
    }

    /**
     * Sets up connection state monitoring using Lettuce's listener API.
     */
    private fun setupConnectionStateListener() {
        val mainConnection = connection

        redisClient?.addListener(object : RedisConnectionStateListener {
            override fun onRedisConnected(conn: RedisChannelHandler<*, *>, socketAddress: SocketAddress) {
                // Only trigger callbacks when the MAIN connection reconnects
                if (conn === mainConnection) {
                    logger.info("Main Redis connection restored to $socketAddress")
                    val scope = coroutineScope
                    if (scope != null) {
                        scope.launch {
                            invokeReconnectCallbacks()
                        }
                    } else {
                        logger.warning("Cannot invoke reconnect callbacks: coroutineScope is null")
                    }
                } else {
                    logger.fine("PubSub connection restored to $socketAddress")
                }
            }

            override fun onRedisDisconnected(conn: RedisChannelHandler<*, *>) {
                if (conn === mainConnection) {
                    logger.warning("Main Redis connection lost - Lettuce will auto-reconnect")
                }
            }

            override fun onRedisExceptionCaught(conn: RedisChannelHandler<*, *>, cause: Throwable) {
                logger.warning("Redis exception: ${cause.message}")
            }
        })
    }

    /**
     * Sets up the pub/sub message listener.
     *
     * Handlers are dispatched off the Netty EventLoop via the coroutine scope
     * to prevent blocking Redis I/O if a handler does heavy work.
     */
    private fun setupPubSubListener() {
        pubSubConnection?.addListener(object : RedisPubSubAdapter<String, ByteArray>() {
            override fun message(channel: String, message: ByteArray) {
                val handler = subscriptionHandlers[channel] ?: return
                // Dispatch off Netty EventLoop to avoid blocking Redis I/O
                val scope = coroutineScope
                if (scope != null) {
                    scope.launch {
                        try {
                            handler(message)
                        } catch (e: Exception) {
                            logger.warning("Pub/sub handler for channel '$channel' failed: ${e.message}")
                        }
                    }
                } else {
                    // Fallback: invoke directly if scope is unavailable (shouldn't happen in normal flow)
                    try {
                        handler(message)
                    } catch (e: Exception) {
                        logger.warning("Pub/sub handler for channel '$channel' failed: ${e.message}")
                    }
                }
            }
        })
    }


    override suspend fun disconnect() {
        connectMutex.withLock {
            if (connection == null && pubSubConnection == null) {
                logger.info("Redis already disconnected")
                return
            }

            logger.info("Disconnecting from Redis...")

            // NonCancellable ensures cleanup completes even if the calling coroutine
            // is cancelled — prevents Netty thread leaks on cancellation during disconnect.
            withContext(NonCancellable + Dispatchers.IO) {
                cleanup()
            }

            try {
                coroutineScope?.cancel()
                coroutineScope = null
            } catch (e: Exception) {
                logger.warning("Error cancelling coroutine scope: ${e.message}")
            }

            logger.info("Disconnected from Redis")
        }
    }

    /**
     * Cleans up connection resources. Called on disconnect or connection failure.
     * Note: ClientResources are NOT cleaned up here to allow reuse across reconnection cycles.
     *
     * Wrapped in [NonCancellable] to guarantee resource release even if the calling
     * coroutine is cancelled mid-cleanup (e.g. during connect() error paths or plugin shutdown).
     * Without this, cancellation could interrupt shutdownAsync().await(), leaving Netty
     * event loop threads alive and causing classloader leaks on plugin reload.
     *
     * Must be called from a Dispatchers.IO context (uses suspending shutdownAsync).
     */
    private suspend fun cleanup() = withContext(NonCancellable) {
        try {
            pubSubConnection?.close()
        } catch (e: Exception) {
            logger.warning("Error closing pub/sub connection: ${e.message}")
        }
        pubSubConnection = null

        try {
            connection?.close()
        } catch (e: Exception) {
            logger.warning("Error closing connection: ${e.message}")
        }
        connection = null

        try {
            // Use shutdownAsync().await() instead of blocking shutdown()
            // RedisClient.shutdown() internally calls shutdownAsync().get() which blocks the thread.
            // Since we're in a coroutine context, we can suspend instead.
            redisClient?.shutdownAsync()?.await()
        } catch (e: Exception) {
            logger.warning("Error shutting down Redis client: ${e.message}")
        }
        redisClient = null

        subscriptionHandlers.clear()
    }

    override suspend fun shutdown() {
        connectMutex.withLock {
            logger.info("Shutting down Redis manager...")

            // NonCancellable ensures the entire shutdown sequence completes even if
            // the calling coroutine is cancelled — both client and ClientResources must
            // be fully shut down to prevent Netty event loop thread leaks on plugin reload.
            withContext(NonCancellable + Dispatchers.IO) {
                cleanup()

                // Now shutdown ClientResources (thread pools, event loops)
                // Must await completion to prevent lingering Netty threads on plugin reload
                if (clientResourcesOwned && clientResources != null) {
                    try {
                        // Use shutdown(quietPeriod, timeout, unit) + await() instead of
                        // blocking get(). The timeout is handled by Lettuce itself,
                        // and await() properly suspends rather than blocking the thread.
                        // We're inside NonCancellable so await() won't be interrupted.
                        clientResources?.shutdown(0, 5, java.util.concurrent.TimeUnit.SECONDS)?.await()
                    } catch (e: Exception) {
                        logger.warning("Error shutting down client resources: ${e.message}")
                    }
                    clientResources = null
                    clientResourcesOwned = false
                }

                reconnectCallbacks.clear()
            }

            try {
                coroutineScope?.cancel()
                coroutineScope = null
            } catch (e: Exception) {
                logger.warning("Error cancelling coroutine scope: ${e.message}")
            }

            logger.info("Redis manager shut down completely")
        }
    }

    override suspend fun <T> execute(command: suspend RedisAsyncCommands<String, ByteArray>.() -> T): T {
        val conn = connection ?: throw RedisConnectionException("Redis client not initialized")

        return try {
            command(conn.async())
        } catch (e: CancellationException) {
            throw e // Don't wrap cancellation — required for structured concurrency
        } catch (e: Exception) {
            throw RedisConnectionException("Redis command failed: ${e.message}", e)
        }
    }

    override suspend fun <T> evalScript(
        script: String,
        keys: List<String>,
        args: List<ByteArray>,
        outputType: ScriptOutputType,
        resultMapper: (Any?) -> T
    ): T {
        val conn = connection ?: throw RedisConnectionException("Redis client not initialized")

        return try {
            val keysArray = keys.toTypedArray()
            val result = conn.async().eval<Any>(
                script,
                outputType,
                keysArray,
                *args.toTypedArray()
            ).await()

            resultMapper(result)
        } catch (e: CancellationException) {
            throw e // Don't wrap cancellation — required for structured concurrency
        } catch (e: io.lettuce.core.RedisCommandExecutionException) {
            throw RedisScriptException("Lua script execution failed: ${e.message}", script, e)
        } catch (e: RedisScriptException) {
            throw e
        } catch (e: Exception) {
            throw RedisScriptException("Lua script execution failed: ${e.message}", script, e)
        }
    }

    override suspend fun pipeline(commands: suspend RedisPipeline.() -> Unit) {
        val conn = connection ?: throw RedisConnectionException("Redis client not initialized")

        try {
            val pipeline = RedisPipelineImpl(conn.async())
            commands(pipeline)
            pipeline.awaitAll()
        } catch (e: CancellationException) {
            throw e // Don't wrap cancellation — required for structured concurrency
        } catch (e: RedisPipelineException) {
            throw e
        } catch (e: Exception) {
            throw RedisConnectionException("Redis pipeline failed: ${e.message}", e)
        }
    }


    override suspend fun subscribe(channel: String, handler: (ByteArray) -> Unit) {
        val pubSub = pubSubConnection ?: throw RedisConnectionException("Redis pub/sub not initialized")

        subscriptionHandlers[channel] = handler

        try {
            pubSub.async().subscribe(channel).await()
            logger.info("Subscribed to Redis channel: $channel")
        } catch (e: CancellationException) {
            subscriptionHandlers.remove(channel)
            throw e // Don't wrap cancellation — required for structured concurrency
        } catch (e: Exception) {
            subscriptionHandlers.remove(channel)
            throw RedisConnectionException("Failed to subscribe to channel $channel: ${e.message}", e)
        }
    }

    override suspend fun unsubscribe(channel: String) {
        val removed = subscriptionHandlers.remove(channel)
        if (removed != null) {
            try {
                pubSubConnection?.async()?.unsubscribe(channel)?.await()
                logger.info("Unsubscribed from Redis channel: $channel")
            } catch (e: CancellationException) {
                throw e // Don't wrap cancellation — required for structured concurrency
            } catch (e: Exception) {
                logger.warning("Failed to unsubscribe from channel $channel: ${e.message}")
            }
        }
    }

    override suspend fun publish(channel: String, message: ByteArray) {
        val conn = connection ?: throw RedisConnectionException("Redis client not initialized")

        try {
            conn.async().publish(channel, message).await()
        } catch (e: CancellationException) {
            throw e // Don't wrap cancellation — required for structured concurrency
        } catch (e: Exception) {
            throw RedisConnectionException("Failed to publish to channel $channel: ${e.message}", e)
        }
    }

    /**
     * Scans keys matching a pattern using cursor-based iteration.
     */
    private suspend fun scan(cursor: String, pattern: String, count: Int = 100): Pair<String, List<String>> {
        val conn = connection ?: throw RedisConnectionException("Redis client not initialized")

        val scanArgs = ScanArgs.Builder
            .matches(pattern)
            .limit(count.toLong())

        val result = conn.async()
            .scan(ScanCursor.of(cursor), scanArgs)
            .await()

        return result.cursor to result.keys
    }

    override fun scanFlow(pattern: String, countHint: Int): Flow<String> = flow {
        var cursor = "0"
        do {
            val (nextCursor, keys) = scan(cursor, pattern, countHint)
            keys.forEach { emit(it) }
            cursor = nextCursor
        } while (cursor != "0")
    }

    override fun onReconnect(callback: suspend () -> Unit): AutoCloseable {
        reconnectCallbacks.add(callback)
        return AutoCloseable { reconnectCallbacks.remove(callback) }
    }

    override suspend fun <T> withRetry(maxRetries: Int, operation: suspend () -> T): T {
        require(maxRetries > 0) { "maxRetries must be positive" }

        var lastException: Exception? = null
        var delayMs = INITIAL_RETRY_DELAY_MS

        repeat(maxRetries) { attempt ->
            try {
                return operation()
            } catch (e: CancellationException) {
                throw e // Don't wrap cancellation — required for structured concurrency
            } catch (e: RedisScriptException) {
                throw e // Script errors (syntax, WRONGTYPE) are not transient — don't retry
            } catch (e: Exception) {
                lastException = e
                logger.warning("Redis operation failed (attempt ${attempt + 1}/$maxRetries): ${e.message}")
                if (attempt < maxRetries - 1) {
                    delay(delayMs)
                    delayMs = (delayMs * 2).coerceAtMost(MAX_RETRY_DELAY_MS)
                }
            }
        }

        throw RedisConnectionException(
            "Redis operation failed after $maxRetries retries: ${lastException?.message}",
            lastException
        )
    }

    /**
     * Invokes all registered reconnect callbacks.
     */
    private suspend fun invokeReconnectCallbacks() {
        if (reconnectCallbacks.isEmpty()) return

        logger.info("Invoking ${reconnectCallbacks.size} reconnect callbacks...")

        for (callback in reconnectCallbacks) {
            try {
                callback()
            } catch (e: CancellationException) {
                // If OUR scope is being cancelled, propagate immediately.
                // If the callback itself threw CancellationException internally,
                // ensureActive() won't throw and we continue with remaining callbacks.
                coroutineContext.ensureActive()
                logger.warning("Reconnect callback cancelled: ${e.message}")
            } catch (e: Exception) {
                logger.warning("Reconnect callback failed: ${e.message}")
            }
        }
    }
}

/**
 * Lettuce-based implementation of RedisPipeline.
 */
internal class RedisPipelineImpl(
    private val async: RedisAsyncCommands<String, ByteArray>
) : RedisPipeline {

    private val futures = ArrayList<RedisFuture<*>>()

    @Suppress("UNCHECKED_CAST")
    private fun <T> track(future: RedisFuture<out T>): RedisFuture<T> {
        futures.add(future)
        return future as RedisFuture<T>
    }

    // ==================== String Operations ====================

    override fun set(key: String, value: ByteArray, ttlSeconds: Long?): RedisFuture<String> {
        val future = if (ttlSeconds != null) {
            async.set(key, value, SetArgs.Builder.ex(ttlSeconds))
        } else {
            async.set(key, value)
        }
        return track(future)
    }

    override fun setex(key: String, ttlSeconds: Long, value: ByteArray): RedisFuture<String> {
        return track(async.setex(key, ttlSeconds, value))
    }

    override fun setnx(key: String, value: ByteArray): RedisFuture<Boolean> {
        return track(async.setnx(key, value))
    }

    override fun get(key: String): RedisFuture<ByteArray?> {
        return track(async.get(key))
    }

    override fun getdel(key: String): RedisFuture<ByteArray?> {
        return track(async.getdel(key))
    }

    // ==================== Key Operations ====================

    override fun del(vararg keys: String): RedisFuture<Long> {
        return track(async.del(*keys))
    }

    override fun expire(key: String, ttlSeconds: Long): RedisFuture<Boolean> {
        return track(async.expire(key, ttlSeconds))
    }

    override fun exists(vararg keys: String): RedisFuture<Long> {
        return track(async.exists(*keys))
    }

    // ==================== Hash Operations ====================

    override fun hset(key: String, field: String, value: ByteArray): RedisFuture<Boolean> {
        return track(async.hset(key, field, value))
    }

    override fun hmset(key: String, map: Map<String, ByteArray>): RedisFuture<String> {
        return track(async.hmset(key, map))
    }

    override fun hget(key: String, field: String): RedisFuture<ByteArray?> {
        return track(async.hget(key, field))
    }

    override fun hgetall(key: String): RedisFuture<Map<String, ByteArray>> {
        return track(async.hgetall(key))
    }

    override fun hdel(key: String, vararg fields: String): RedisFuture<Long> {
        return track(async.hdel(key, *fields))
    }

    // ==================== List Operations ====================

    override fun lpush(key: String, vararg values: ByteArray): RedisFuture<Long> {
        return track(async.lpush(key, *values))
    }

    override fun rpush(key: String, vararg values: ByteArray): RedisFuture<Long> {
        return track(async.rpush(key, *values))
    }

    override fun lrange(key: String, start: Long, stop: Long): RedisFuture<List<ByteArray>> {
        return track(async.lrange(key, start, stop))
    }

    // ==================== Set Operations ====================

    override fun sadd(key: String, vararg members: ByteArray): RedisFuture<Long> {
        return track(async.sadd(key, *members))
    }

    override fun smembers(key: String): RedisFuture<Set<ByteArray>> {
        return track(async.smembers(key))
    }

    override fun srem(key: String, vararg members: ByteArray): RedisFuture<Long> {
        return track(async.srem(key, *members))
    }

    // ==================== Sorted Set Operations ====================

    override fun zadd(key: String, score: Double, member: ByteArray): RedisFuture<Long> {
        return track(async.zadd(key, score, member))
    }

    override fun zrank(key: String, member: ByteArray): RedisFuture<Long?> {
        return track(async.zrank(key, member))
    }

    override fun zrevrank(key: String, member: ByteArray): RedisFuture<Long?> {
        return track(async.zrevrank(key, member))
    }

    override fun zscore(key: String, member: ByteArray): RedisFuture<Double?> {
        return track(async.zscore(key, member))
    }

    override fun zrem(key: String, vararg members: ByteArray): RedisFuture<Long> {
        return track(async.zrem(key, *members))
    }

    override fun zrangebyscore(key: String, min: Double, max: Double): RedisFuture<List<ByteArray>> {
        return track(async.zrangebyscore(key, io.lettuce.core.Range.create(min, max)))
    }

    override fun zremrangebyscore(key: String, min: Double, max: Double): RedisFuture<Long> {
        return track(async.zremrangebyscore(key, io.lettuce.core.Range.create(min, max)))
    }

    override fun zcard(key: String): RedisFuture<Long> {
        return track(async.zcard(key))
    }

    // ==================== Generic Command Access ====================

    override fun <T> add(command: RedisAsyncCommands<String, ByteArray>.() -> RedisFuture<T>): RedisFuture<T> {
        val future: RedisFuture<T> = command(async)
        futures.add(future)
        return future
    }

    /**
     * Awaits completion of all queued futures using a single suspension point.
     *
     * Uses a `finally` block to guarantee [futures] is cleared even on cancellation,
     * preventing stale futures from leaking into subsequent pipeline uses.
     */
    suspend fun awaitAll() {
        if (futures.isEmpty()) return

        // Convert once to CompletableFuture array for both allOf and error collection
        val completableFutures = Array(futures.size) { futures[it].toCompletableFuture() }

        try {
            try {
                CompletableFuture.allOf(*completableFutures).await()
            } catch (e: CancellationException) {
                throw e // Don't swallow cancellation — required for structured concurrency
            } catch (_: Exception) {
                // allOf completes exceptionally if ANY future fails.
                // We iterate individual futures below to collect ALL failures.
            }

            // Collect failures from completed futures (non-blocking since all are done)
            val failures = completableFutures.mapNotNull { cf ->
                try {
                    cf.getNow(null)
                    null
                } catch (e: Exception) {
                    e.cause ?: e
                }
            }

            if (failures.isNotEmpty()) {
                throw RedisPipelineException(
                    "Redis pipeline failed with ${failures.size} error(s). First error: ${failures.first().message}",
                    failures
                )
            }
        } finally {
            futures.clear()
        }
    }
}
