package me.cyljacky02.loafylib.redis

/**
 * Exception thrown when a Redis Lua script execution fails.
 *
 * @param message Description of the script failure
 * @param script The Lua script that failed (for debugging)
 * @param cause The underlying cause of the failure
 */
class RedisScriptException(
    message: String,
    val script: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)
