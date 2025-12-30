package me.cyljacky02.loafylib.redis

/**
 * Exception thrown when a Redis connection operation fails.
 *
 * @param message Description of the connection failure
 * @param cause The underlying cause of the failure
 */
class RedisConnectionException(
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)
