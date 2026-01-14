package me.cyljacky02.loafylib.database

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.delay
import me.cyljacky02.loafylib.config.DatabaseConfig
import java.sql.Connection
import java.sql.SQLException
import java.util.logging.Logger

/**
 * HikariCP-based implementation of [DatabaseManager] for MariaDB.
 *
 * This implementation follows HikariCP best practices:
 * - Fixed-size pool (minimumIdle = maximumPoolSize) for best performance
 * - No connectionTestQuery (MariaDB JDBC4 supports isValid() API)
 * - keepaliveTime enabled to prevent connection timeout by DB/network
 * - Leak detection enabled (minimum 2000ms threshold)
 * - Exponential backoff retry for transient errors
 * - No retry for constraint violations (error codes 1062, 1452)
 *
 * @param config Database configuration
 * @param logger Logger instance for status messages
 * @param poolName Optional pool name for logging (default: "LoafyLib-HikariPool")
 * @see <a href="https://github.com/brettwooldridge/HikariCP">HikariCP Documentation</a>
 */
class HikariDatabaseManager(
    private val config: DatabaseConfig,
    private val logger: Logger,
    private val poolName: String = "LoafyLib-HikariPool"
) : DatabaseManager {

    private var dataSource: HikariDataSource? = null

    companion object {
        /** Initial retry delay in milliseconds */
        private const val INITIAL_RETRY_DELAY_MS = 1000L
        
        /** Maximum retry delay in milliseconds (16 seconds) */
        private const val MAX_RETRY_DELAY_MS = 16000L
        
        /** Minimum leak detection threshold (HikariCP requirement) */
        private const val MIN_LEAK_DETECTION_THRESHOLD_MS = 2000L
        
        /** Default leak detection threshold (30 seconds) */
        private const val DEFAULT_LEAK_DETECTION_THRESHOLD_MS = 30000L
        
        /** MariaDB error code for duplicate entry */
        private const val ERROR_DUPLICATE_ENTRY = 1062
        
        /** MariaDB error code for foreign key constraint violation */
        private const val ERROR_FOREIGN_KEY_CONSTRAINT = 1452
    }

    override suspend fun initialize() {
        val hikariConfig = HikariConfig().apply {
            jdbcUrl = config.toJdbcUrl()
            username = config.username
            password = config.password
            maximumPoolSize = config.poolSize
            // HikariCP recommends NOT setting minimumIdle for fixed-size pool (best performance)
            // Default: minimumIdle = maximumPoolSize

            connectionTimeout = 10_000 // 10 seconds
            maxLifetime = 1_800_000 // 30 minutes (HikariCP default)

            // keepaliveTime: Prevents connections from being timed out by database/network
            // HikariCP default is 2 minutes. Must be less than maxLifetime.
            // The "ping" is performed on idle connections with minimal performance impact.
            keepaliveTime = 120_000 // 2 minutes (HikariCP default)

            this.poolName = this@HikariDatabaseManager.poolName

            // Explicitly set driver class name - required when using Paper's library loader
            // because JDBC DriverManager uses system classloader for auto-discovery,
            // but the driver is loaded into the plugin's classloader.
            driverClassName = "org.mariadb.jdbc.Driver"

            // NO connectionTestQuery - MariaDB JDBC4 supports isValid() API
            validationTimeout = 5_000 // 5 seconds

            // Leak detection - logs warning if connection not returned within threshold
            leakDetectionThreshold = DEFAULT_LEAK_DETECTION_THRESHOLD_MS
                .coerceAtLeast(MIN_LEAK_DETECTION_THRESHOLD_MS)

            // MariaDB Connector/J driver-level prepared statement configuration
            // HikariCP explicitly states pool-level statement caching is an anti-pattern.
            // These properties are passed to the MariaDB JDBC driver via addDataSourceProperty.
            //
            // useServerPrepStmts: Enables server-side prepared statements (PREPARE/EXECUTE)
            // prepStmtCacheSize: Client-side cache size for prepared statements
            // prepStmtCacheSqlLimit: Max SQL query length to cache
            // Note: cachePrepStmts is MySQL-specific and NOT used by MariaDB Connector/J
            addDataSourceProperty("useServerPrepStmts", "true")
            addDataSourceProperty("prepStmtCacheSize", "250")
            addDataSourceProperty("prepStmtCacheSqlLimit", "2048")
        }

        dataSource = HikariDataSource(hikariConfig)
        logger.info("HikariCP pool '$poolName' initialized (size: ${config.poolSize})")
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

    override suspend fun <T> withRetry(maxRetries: Int, operation: suspend () -> T): T {
        require(maxRetries > 0) { "maxRetries must be positive" }
        
        var lastException: SQLException? = null
        var delayMs = INITIAL_RETRY_DELAY_MS

        repeat(maxRetries) { attempt ->
            try {
                return operation()
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

    /**
     * Checks if the SQLException is a constraint violation (not transient, should not retry).
     *
     * MariaDB error codes:
     * - 1062: Duplicate entry (unique constraint violation)
     * - 1452: Foreign key constraint violation
     */
    private fun isConstraintViolation(e: SQLException): Boolean {
        return e.errorCode == ERROR_DUPLICATE_ENTRY ||
                e.errorCode == ERROR_FOREIGN_KEY_CONSTRAINT ||
                e.message?.contains("Duplicate entry", ignoreCase = true) == true ||
                e.message?.contains("foreign key constraint", ignoreCase = true) == true
    }
}
