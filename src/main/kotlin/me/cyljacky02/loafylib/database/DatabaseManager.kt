package me.cyljacky02.loafylib.database

import me.cyljacky02.loafylib.plugin.PluginComponent
import java.sql.Connection

/**
 * Interface for database connection management with HikariCP pooling.
 *
 * This interface provides the core database operations needed by dependent plugins:
 * - Connection lifecycle management (initialize/shutdown)
 * - Connection acquisition from the pool
 * - Retry logic for transient database errors
 *
 * Implementations should use HikariCP for connection pooling and handle
 * MariaDB-specific error codes for retry decisions.
 *
 * ## Usage Example
 * ```kotlin
 * class MyDatabaseManager(
 *     private val databaseManager: DatabaseManager
 * ) {
 *     suspend fun doSomething() {
 *         databaseManager.withRetry {
 *             databaseManager.getConnection().use { conn ->
 *                 // Execute queries
 *             }
 *         }
 *     }
 * }
 * ```
 */
interface DatabaseManager : PluginComponent {

    /**
     * Initializes the database connection pool.
     *
     * This should be called during plugin enable to establish the HikariCP pool.
     * The implementation should configure the pool with appropriate settings
     * for MariaDB connections.
     *
     * @throws DatabaseInitializationException if the pool cannot be created
     */
    override suspend fun initialize()

    /**
     * Shuts down the database connection pool.
     *
     * This should be called during plugin disable to cleanly close all
     * connections and release resources.
     */
    override suspend fun shutdown()

    /**
     * Obtains a connection from the pool.
     *
     * The caller is responsible for closing the connection when done.
     * Use Kotlin's `use` extension for automatic resource management:
     * ```kotlin
     * databaseManager.getConnection().use { conn ->
     *     // Use connection
     * }
     * ```
     *
     * @return A pooled database connection
     * @throws IllegalStateException if the pool is not initialized
     * @throws java.sql.SQLException if a connection cannot be obtained
     */
    fun getConnection(): Connection

    /**
     * Executes a database operation with exponential backoff retry logic.
     *
     * This method handles transient database errors (connection timeouts, deadlocks)
     * by retrying with exponential backoff. The backoff sequence is:
     * 1s → 2s → 4s → 8s → 16s (max)
     *
     * **Important**: Constraint violations (duplicate key error 1062, foreign key
     * error 1452) are NOT retried and will propagate immediately.
     *
     * @param maxRetries Maximum number of retry attempts (default: 5)
     * @param operation The database operation to execute
     * @return The result of the operation
     * @throws java.sql.SQLException if all retries are exhausted or a non-retryable error occurs
     */
    suspend fun <T> withRetry(maxRetries: Int = 5, operation: suspend () -> T): T
}
