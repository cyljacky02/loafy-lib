package me.cyljacky02.loafylib.database

import com.zaxxer.hikari.HikariConfig
import me.cyljacky02.loafylib.config.DatabaseConfig
import java.util.logging.Logger

/**
 * HikariCP-based implementation of [DatabaseManager] for MariaDB.
 *
 * This implementation follows HikariCP best practices:
 * - Fixed-size pool (minimumIdle = maximumPoolSize) for best performance
 * - No connectionTestQuery (MariaDB JDBC4 supports isValid() API)
 * - keepaliveTime enabled to prevent connection timeout by DB/network
 * - Leak detection enabled (minimum 2000ms threshold)
 * - Exponential backoff retry for transient errors (inherited from [AbstractDatabaseManager])
 * - No retry for constraint violations (error codes 1062, 1452)
 * - All operations dispatched via Dispatchers.IO.limitedParallelism for thread safety
 *
 * @param config Database configuration
 * @param logger Logger instance for status messages
 * @param poolName Optional pool name for logging (default: "LoafyLib-HikariPool")
 * @see <a href="https://github.com/brettwooldridge/HikariCP">HikariCP Documentation</a>
 * @see AbstractDatabaseManager
 */
class MariaDbDatabaseManager(
    private val config: DatabaseConfig,
    logger: Logger,
    poolName: String = "LoafyLib-HikariPool"
) : AbstractDatabaseManager(logger, poolName) {

    override val databaseType: String = "MariaDB"

    override fun blockingIoParallelism(): Int = config.poolSize.coerceAtLeast(1)

    override fun createHikariConfig(): HikariConfig = HikariConfig().apply {
        jdbcUrl = config.toMariaDbJdbcUrl()
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

        this.poolName = this@MariaDbDatabaseManager.poolName

        // Explicitly set driver class name - required when using Paper's library loader
        // because JDBC DriverManager uses system classloader for auto-discovery,
        // but the driver is loaded into the plugin's classloader.
        driverClassName = "org.mariadb.jdbc.Driver"

        // NO connectionTestQuery - MariaDB JDBC4 supports isValid() API
        validationTimeout = 5_000 // 5 seconds

        // Leak detection - logs warning if connection not returned within threshold
        leakDetectionThreshold = when {
            config.leakDetectionThresholdMs <= 0L -> 0L
            else -> config.leakDetectionThresholdMs.coerceAtLeast(MIN_LEAK_DETECTION_THRESHOLD_MS)
        }

        // MariaDB Connector/J driver-level prepared statement configuration
        // HikariCP explicitly states pool-level statement caching is an anti-pattern.
        // These properties are passed to the MariaDB JDBC driver via addDataSourceProperty.
        //
        // useServerPrepStmts: Enables server-side prepared statements (PREPARE/EXECUTE)
        // prepStmtCacheSize: Cache size for server-side prepared statements (default: 250)
        // cachePrepStmts: Defaults to true in MariaDB Connector/J 3.x, no need to set
        // Note: SQL length limit is hardcoded to 8192 in MariaDB Connector/J 3.x
        addDataSourceProperty("useServerPrepStmts", "true")
        addDataSourceProperty("prepStmtCacheSize", "250")
    }
}

/**
 * Type alias for backward compatibility.
 * @deprecated Use [MariaDbDatabaseManager] directly.
 */
@Deprecated(
    "Renamed to MariaDbDatabaseManager for clarity",
    ReplaceWith("MariaDbDatabaseManager"),
    DeprecationLevel.WARNING
)
typealias HikariDatabaseManager = MariaDbDatabaseManager
