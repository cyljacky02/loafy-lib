package me.cyljacky02.loafylib.config

import org.spongepowered.configurate.objectmapping.ConfigSerializable
import org.spongepowered.configurate.objectmapping.meta.Comment

/**
 * Configuration data class for Redis connection settings.
 *
 * This class is designed to be serialized/deserialized by Configurate
 * and provides sensible defaults for local development.
 *
 * @property host Redis server hostname
 * @property port Redis server port
 * @property password Redis password (empty string means no authentication)
 * @property database Redis database index (0-15)
 * @property poolSize Thread pool size for Lettuce I/O and computation threads.
 *   Each pool gets this many threads (total = poolSize × 2). Minimum is 2 (Lettuce enforced).
 *   Default of 2 is sufficient for typical plugin workloads (2 connections: main + pubsub).
 *   Increase only for high-throughput command pipelines or many concurrent connections.
 *   Thread names in dumps: `lettuce-nioEventLoop-X` (IO), `lettuce-eventExecutorLoop-X` (computation)
 * @property timeoutSeconds Connection and command timeout in seconds
 * @property ssl Whether to use SSL/TLS for the Redis connection
 */
@ConfigSerializable
data class RedisConfig(
    @Comment("Redis server hostname")
    val host: String = "localhost",

    @Comment("Redis server port")
    val port: Int = 6379,

    @Comment("Redis password (leave empty if no authentication)")
    val password: String = "",

    @Comment("Redis database index (0-15)")
    val database: Int = 0,

    @Comment("Thread pool size for I/O and computation (min: 2, total threads = poolSize x 2)")
    val poolSize: Int = 2,

    @Comment("Connection and command timeout in seconds")
    val timeoutSeconds: Long = 10,

    @Comment("Whether to use SSL/TLS for the Redis connection")
    val ssl: Boolean = false
)
