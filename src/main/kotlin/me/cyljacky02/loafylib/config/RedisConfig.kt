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
 * @property poolSize Connection pool size for Lettuce
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
    
    @Comment("Connection pool size")
    val poolSize: Int = 4
) {
    companion object {
        /**
         * Creates a RedisConfig with default values.
         */
        fun defaults(): RedisConfig = RedisConfig()
    }
    
    /**
     * Returns the password as nullable string for Lettuce configuration.
     * Returns null if password is empty or blank.
     */
    fun getPasswordOrNull(): String? = password.ifBlank { null }
    
    /**
     * Builds a Redis URI string for Lettuce connection.
     */
    fun toRedisUri(): String = buildString {
        append("redis://")
        if (password.isNotBlank()) {
            append(password)
            append("@")
        }
        append(host)
        append(":")
        append(port)
        append("/")
        append(database)
    }
}
