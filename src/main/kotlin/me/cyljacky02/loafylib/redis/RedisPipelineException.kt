package me.cyljacky02.loafylib.redis

/**
 * Exception thrown when one or more commands in a Redis pipeline fail.
 *
 * Contains all failures that occurred during pipeline execution, allowing
 * callers to inspect and handle partial failures appropriately.
 *
 * @param message Summary message describing the failure
 * @param failures List of all exceptions that occurred
 */
class RedisPipelineException(
    message: String,
    val failures: List<Throwable>
) : RuntimeException(message, failures.firstOrNull()) {
    init {
        // Add remaining failures as suppressed exceptions for full visibility
        failures.drop(1).forEach { addSuppressed(it) }
    }
}
