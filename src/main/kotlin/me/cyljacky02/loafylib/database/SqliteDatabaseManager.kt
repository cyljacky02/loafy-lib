package me.cyljacky02.loafylib.database

import com.zaxxer.hikari.HikariConfig
import me.cyljacky02.loafylib.config.DatabaseConfig
import java.io.File
import java.util.logging.Logger

/**
 * HikariCP-based implementation of [DatabaseManager] for SQLite.
 *
 * This implementation is optimized for SQLite with single-file local databases:
 * - WAL (Write-Ahead Logging) journal mode for better concurrent read/write performance
 * - synchronous=NORMAL for balanced durability and performance
 * - Foreign keys enabled by default
 * - Single connection pool (SQLite doesn't benefit from multiple connections for writes)
 *
 * ## SQLite Optimization Flags
 *
 * The following PRAGMA settings are applied via JDBC URL:
 * - `journal_mode=WAL`: Enables Write-Ahead Logging for better concurrency
 * - `synchronous=NORMAL`: Balances durability with performance (safe with WAL)
 * - `foreign_keys=ON`: Enforces foreign key constraints
 *
 * ## Connection Pooling Considerations
 *
 * SQLite uses file-level locking, so multiple concurrent write connections don't
 * provide benefit. However, HikariCP is still used for:
 * - Connection lifecycle management
 * - Consistent API with other database implementations
 * - Read connection pooling (WAL allows concurrent reads)
 *
 * @param config Database configuration (uses [DatabaseConfig.filePath] for database location)
 * @param logger Logger instance for status messages
 * @param poolName Optional pool name for logging (default: "LoafyLib-SQLite-Pool")
 * @see <a href="https://www.sqlite.org/wal.html">SQLite WAL Documentation</a>
 * @see AbstractDatabaseManager
 */
class SqliteDatabaseManager(
    private val config: DatabaseConfig,
    logger: Logger,
    poolName: String = "LoafyLib-SQLite-Pool"
) : AbstractDatabaseManager(logger, poolName) {

    override val databaseType: String = "SQLite"

    override fun blockingIoParallelism(): Int = 1

    override fun createHikariConfig(): HikariConfig = HikariConfig().apply {
        // Ensure parent directories exist
        val dbFile = File(config.filePath)
        dbFile.parentFile?.mkdirs()

        // SQLite JDBC URL with optimization flags
        // - journal_mode=WAL: Write-Ahead Logging for better concurrency
        // - synchronous=NORMAL: Safe with WAL, better performance than FULL
        // - foreign_keys=ON: Enable foreign key constraint enforcement
        // - busy_timeout: Milliseconds to wait when database is locked (avoids immediate SQLITE_BUSY)
        //   Set via URL parameter so it's applied during connection creation by the driver,
        //   before any queries execute. This is more reliable than addDataSourceProperty
        //   which depends on HikariCP's DataSource property mapping.
        val busyTimeout = config.sqliteBusyTimeoutMs.coerceAtLeast(0)
        jdbcUrl = "jdbc:sqlite:${config.filePath}?journal_mode=WAL&synchronous=NORMAL&foreign_keys=ON&busy_timeout=$busyTimeout"

        // SQLite doesn't use username/password for file-based databases
        // but HikariCP requires non-null values
        username = ""
        password = ""

        // SQLite connection pool size
        // - For writes: SQLite uses file-level locking, so only 1 write at a time
        // - For reads: WAL allows concurrent reads
        // - Default to small pool size; larger pools don't help SQLite writes
        maximumPoolSize = 1

        connectionTimeout = 10_000 // 10 seconds

        // SQLite connections are lightweight, but we keep them alive for efficiency
        maxLifetime = 1_800_000 // 30 minutes

        this.poolName = this@SqliteDatabaseManager.poolName

        // Explicitly set driver class name - required when using Paper's library loader
        driverClassName = "org.sqlite.JDBC"

        validationTimeout = 5_000 // 5 seconds

        // Leak detection
        leakDetectionThreshold = when {
            config.leakDetectionThresholdMs <= 0L -> 0L
            else -> config.leakDetectionThresholdMs.coerceAtLeast(MIN_LEAK_DETECTION_THRESHOLD_MS)
        }

        // Auto-commit is enabled by default. Individual operations should use explicit
        // transactions when atomic multi-statement behavior is required.
        isAutoCommit = true
    }

    override suspend fun initialize() {
        super.initialize()
        logger.info("SQLite database file: ${config.filePath}")
    }
}
