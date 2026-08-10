package me.cyljacky02.loafylib.database

import kotlinx.coroutines.CoroutineDispatcher
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
 * Implementations use HikariCP for connection pooling and handle database-specific
 * error codes for retry decisions.
 *
 * Available implementations:
 * - [MariaDbDatabaseManager]: For MariaDB/MySQL server connections
 * - [SqliteDatabaseManager]: For SQLite file-based databases
 *
 * @see AbstractDatabaseManager
 * @see MariaDbDatabaseManager
 * @see SqliteDatabaseManager
 *
 * ## Usage Example
 * ```kotlin
 * class MyRepository(private val db: DatabaseManager) {
 *
 *     // Preferred: useConnection handles dispatcher + connection lifecycle
 *     suspend fun fetchPlayer(uuid: UUID): PlayerData? {
 *         return db.useConnection { conn ->
 *             conn.prepareStatement("SELECT * FROM players WHERE uuid = ?").use { stmt ->
 *                 stmt.setString(1, uuid.toString())
 *                 stmt.executeQuery().use { rs ->
 *                     if (rs.next()) PlayerData.from(rs) else null
 *                 }
 *             }
 *         }
 *     }
 *
 *     // With retry for transient errors:
 *     suspend fun fetchPlayerWithRetry(uuid: UUID): PlayerData? {
 *         return db.withRetry {
 *             db.getConnection().use { conn ->
 *                 conn.prepareStatement("SELECT * FROM players WHERE uuid = ?").use { stmt ->
 *                     stmt.setString(1, uuid.toString())
 *                     stmt.executeQuery().use { rs ->
 *                         if (rs.next()) PlayerData.from(rs) else null
 *                     }
 *                 }
 *             }
 *         }
 *     }
 * }
 * ```
 */
interface DatabaseManager : PluginComponent {

    /**
     * Dedicated coroutine dispatcher for blocking JDBC operations.
     *
     * This is a [Dispatchers.IO.limitedParallelism][kotlinx.coroutines.Dispatchers.IO] view
     * with parallelism bounded to match the HikariCP connection pool's `maximumPoolSize`.
     * It shares threads elastically with `Dispatchers.IO` while ensuring no more concurrent
     * database operations than the pool can serve.
     *
     * Callers can use this directly with `withContext(db.blockingIoDispatcher)` for
     * custom dispatch patterns, but [useConnection] and [withRetry] are preferred
     * as they handle dispatching automatically.
     */
    val blockingIoDispatcher: CoroutineDispatcher

    /**
     * Initializes the database connection pool and dedicated dispatcher.
     *
     * This should be called during plugin enable to establish the HikariCP pool.
     * The implementation configures the pool with appropriate settings for the
     * target database (MariaDB, SQLite, etc.).
     *
     * @throws Exception if the pool cannot be created
     */
    override suspend fun initialize()

    /**
     * Shuts down the database connection pool and dedicated dispatcher.
     *
     * This should be called during plugin disable to cleanly close all
     * connections and release resources.
     */
    override suspend fun shutdown()

    /**
     * Obtains a connection from the pool.
     *
     * **Warning**: This is a non-suspend function that performs a potentially blocking
     * call (`HikariDataSource.getConnection()`). Callers **must** ensure they are on
     * an appropriate thread — either inside [withRetry], [useConnection], or within
     * `withContext(blockingIoDispatcher)`. Calling this from a tick/main thread will
     * block that thread until a connection is available.
     *
     * The caller is responsible for closing the connection when done.
     * Use Kotlin's `use` extension for automatic resourcment:
     * ```kotlin
     * withContext(db.blockingIoDispatcher) {
     *     db.getConnection().use { conn ->
     *         // Use connection
     *     }
     * }
     * ```
     *
     * For most use cases, prefer [useConnection] which handles both dispatching
     * and connection lifecycle automatically.
     *
     * @return A pooled database connection
     * @throws IllegalStateException if the pool is not initialized
     * @throws java.sql.SQLException if a connection cannot be obtained
     */
    fun getConnection(): Connection

    /**
     * Acquires a connection and executes a block, ensuring proper dispatcher usage
     * and automatic connection cleanup.
     *
     * This is the **recommended** way to perform database operations. It:
     * 1. Switches to the dedicated [blockingIoDispatcher]
     * 2. Acquires a connection from the HikariCP pool
     * 3. Executes the block with the connection
     * 4. Closes the connection automatically (even on exception or cancellation)
     *
     * ```kotlin
     * val result = db.useConnection { conn ->
     *     conn.prepareStatement("SELECT * FROM players WHERE uuid = ?").use { stmt ->
     *         stmt.setString(1, uuid.toString())
     *         stmt.executeQuery().use { rs ->
     *             if (rs.next()) PlayerData.from(rs) else null
     *         }
     *     }
     * }
     * ```
     *
     * @param block The database operation to execute with the connection
     * @return The result of the block
     * @throws IllegalStateException if the pool is not initialized
     * @throws java.sql.SQLException if a connection cannot be obtained or the operation fails
     */
    suspend fun <T> useConnection(block: (Connection) -> T): T

    /**
     * Executes a database operation with exponential backoff retry logic.
     *
     * This method handles transient database errors (connection timeouts, deadlocks)
     * by retrying with exponential backoff. The backoff sequence is:
     * 1s → 2s → 4s → 8s → 16s (max)
     *
     * **Thread Safety**: Operations are dispatched to the dedicated [blockingIoDispatcher],
     * ensuring JDBC blocking calls never run on tick/main threads regardless of caller context.
     *
     * **Important**: Constraint violations (duplicate key error 1062, foreign key
     * error 1452, SQLite error 19) are NOT retried and will propagate immediately.
     *
     * @param maxRetries Maximum number of retry attempts (default: 5)
     * @param operation The database operation to execute
     * @return The result of the operation
     * @throws java.sql.SQLException if all retries are exhausted or a non-retryable error occurs
     */
    suspend fun <T> withRetry(maxRetries: Int = 5, operation: suspend () -> T): T
}
