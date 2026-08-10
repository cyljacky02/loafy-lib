package me.cyljacky02.loafylib.database

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.sql.Connection
import java.sql.SQLException
import java.util.logging.Logger

/**
 * Abstract base class for HikariCP-based database managers.
 *
 * This class provides the common HikariCP lifecycle management and retry logic
 * shared by all database implementations (MariaDB, SQLite, etc.).
 *
 * Features:
 * - HikariDataSource lifecycle (init, close, connection retrieval)
 * - Exponential backoff retry for transient errors
 * - [Dispatchers.IO.limitedParallelism][kotlinx.coroutines.Dispatchers.IO]-backed dispatcher for blocking JDBC calls
 * - [useConnection] helper for safe dispatcher + connection lifecycle management
 *
 * Subclasses must implement [createHikariConfig] to provide database-specific
 * configuration (JDBC URL, driver, optimization flags, etc.).
 *
 * @param logger Logger instance for status messages
 * @param poolName Pool name for logging and JMX (default: "LoafyLib-HikariPool")
 * @see MariaDbDatabaseManager
 * @see SqliteDatabaseManager
 */
abstract class AbstractDatabaseManager(
    protected val logger: Logger,
    protected val poolName: String = "LoafyLib-HikariPool"
) : DatabaseManager {

    private var dataSource: HikariDataSource? = null

    /**
     * Dispatcher backed by [Dispatchers.IO.limitedParallelism], created lazily.
     *
     * Uses `limitedParallelism` instead of a dedicated thread pool because:
     * - Officially recommended by kotlinx.coroutines as a replacement for `newFixedThreadPoolContext`
     * - Shares threads elastically with [Dispatchers.IO] — no idle dedicated threads
     * - No manual lifecycle management (no close/shutdown needed)
     * - Parallelism is still bounded to match the HikariCP pool size
     */
    override val blockingIoDispatcher: CoroutineDispatcher by lazy {
        Dispatchers.IO.limitedParallelism(blockingIoParallelism(), "$poolName-DB")
    }

    companion object {
        /** Initial retry delay in milliseconds */
        private const val INITIAL_RETRY_DELAY_MS = 1000L

        /** Maximum retry delay in milliseconds (16 seconds) */
        private const val MAX_RETRY_DELAY_MS = 16000L

        /** Minimum leak detection threshold (HikariCP requirement) */
        protected const val MIN_LEAK_DETECTION_THRESHOLD_MS = 2000L

        /** Default leak detection threshold (30 seconds) */
        protected const val DEFAULT_LEAK_DETECTION_THRESHOLD_MS = 30000L

        /** MariaDB error code for duplicate entry */
        private const val ERROR_DUPLICATE_ENTRY = 1062

        /** MariaDB error code for foreign key constraint violation */
        private const val ERROR_FOREIGN_KEY_CONSTRAINT = 1452

        /** SQLite error code for constraint violation (SQLITE_CONSTRAINT) */
        private const val SQLITE_CONSTRAINT = 19
    }

    /**
     * Returns the maximum parallelism for the blocking I/O dispatcher.
     *
     * This should match the HikariCP `maximumPoolSize` to prevent more concurrent
     * connection requests than the pool can serve. Subclasses override to match
     * their pool configuration.
     *
     * @return maximum number of concurrent blocking database operations
     */
    protected open fun blockingIoParallelism(): Int = 10

    /**
     * Creates the database-specific HikariConfig.
     *
     * Subclasses must implement this to configure:
     * - JDBC URL
     * - Driver class name
     * - Username/password (if applicable)
     * - Pool size
     * - Database-specific optimizations
     *
     * @return configured HikariConfig ready to create a DataSource
     */
    protected abstract fun createHikariConfig(): HikariConfig

    /**
     * Returns a description of the database type for logging.
     * E.g., "MariaDB" or "SQLite"
     */
    protected abstract val databaseType: String

    override suspend fun initialize() {
        val hikariConfig = createHikariConfig()
        dataSource = HikariDataSource(hikariConfig)
        logger.info("HikariCP pool '$poolName' initialized for $databaseType")
    }

    override suspend fun shutdown() {
        dataSource?.close()
        dataSource = null
        logger.info("HikariCP pool '$poolName' closed")
    }

    override fun getConnection(): Connection {
        return dataSource?.connection
            ?: throw IllegalStateException("Database not initialized. Call initialize() first.")
    }

    override suspend fun <T> useConnection(block: (Connection) -> T): T {
        return withContext(blockingIoDispatcher) {
            getConnection().use { conn ->
                block(conn)
            }
        }
    }

    override suspend fun <T> withRetry(maxRetries: Int, operation: suspend () -> T): T {
        require(maxRetries > 0) { "maxRetries must be positive" }

        return withContext(blockingIoDispatcher) {
            var lastException: SQLException? = null
            var delayMs = INITIAL_RETRY_DELAY_MS

            repeat(maxRetries) { attempt ->
                try {
                    return@withContext operation()
                } catch (e: SQLException) {
                    // Don't retry constraint violations - they're not transient
                    if (isConstraintViolation(e)) {
                        throw e
                    }

                    lastException = e
                    logger.warning(
                        "Database operation failed (attempt ${attempt + 1}/$maxRetries): ${e.message}"
                    )

                    if (attempt < maxRetries - 1) {
                        delay(delayMs)
                        delayMs = (delayMs * 2).coerceAtMost(MAX_RETRY_DELAY_MS)
                    }
                }
            }

            throw lastException ?: SQLException("Unknown database error after $maxRetries retries")
        }
    }

    /**
     * Checks if the SQLException is a constraint violation (not transient, should not retry).
     *
     * Handles both MariaDB and SQLite constraint violations:
     * - MariaDB 1062: Duplicate entry (unique constraint violation)
     * - MariaDB 1452: Foreign key constraint violation
     * - SQLite 19: SQLITE_CONSTRAINT
     * - Message-based detection as fallback
     */
    protected open fun isConstraintViolation(e: SQLException): Boolean {
        return e.errorCode == ERROR_DUPLICATE_ENTRY ||
                e.errorCode == ERROR_FOREIGN_KEY_CONSTRAINT ||
                e.errorCode == SQLITE_CONSTRAINT ||
                e.message?.contains("Duplicate entry", ignoreCase = true) == true ||
                e.message?.contains("foreign key constraint", ignoreCase = true) == true ||
                e.message?.contains("UNIQUE constraint failed", ignoreCase = true) == true ||
                e.message?.contains("FOREIGN KEY constraint failed", ignoreCase = true) == true
    }
}
