package me.cyljacky02.loafylib.config

import org.spongepowered.configurate.objectmapping.ConfigSerializable
import org.spongepowered.configurate.objectmapping.meta.Comment

/**
 * Database type enumeration for configuration.
 */
enum class DatabaseType {
    /** MariaDB/MySQL database server */
    MARIADB,
    /** SQLite file-based database */
    SQLITE
}

/**
 * Configuration data class for database connection settings.
 *
 * This class is designed to be serialized/deserialized by Configurate
 * and provides sensible defaults for local development.
 *
 * Supports both MariaDB (remote server) and SQLite (local file) databases:
 * - **MARIADB**: Uses host, port, database, username, password
 * - **SQLITE**: Uses filePath only (ignores server connection fields)
 *
 * @property type Database type (MARIADB or SQLITE)
 * @property host Database server hostname (MariaDB only)
 * @property port Database server port (MariaDB only, default: 3306)
 * @property database Database name (MariaDB only)
 * @property username Database username (MariaDB only)
 * @property password Database password (MariaDB only)
 * @property filePath SQLite database file path (SQLite only, relative to plugin data folder)
 * @property poolSize HikariCP connection pool size (default: 10)
 */
@ConfigSerializable
data class DatabaseConfig(
    @Comment("Database type: MARIADB or SQLITE")
    val type: DatabaseType = DatabaseType.MARIADB,

    @Comment("Database server hostname (MariaDB only)")
    val host: String = "localhost",
    
    @Comment("Database server port (MariaDB only)")
    val port: Int = 3306,
    
    @Comment("Database name (MariaDB only)")
    val database: String = "minecraft",
    
    @Comment("Database username (MariaDB only)")
    val username: String = "root",
    
    @Comment("Database password (MariaDB only)")
    val password: String = "",

    @Comment("SQLite database file path (SQLite only, relative to plugin data folder)")
    val filePath: String = "database.db",
    
    @Comment("HikariCP connection pool size")
    val poolSize: Int = 10,

    @Comment("HikariCP leak detection threshold in milliseconds (0 = disabled)")
    val leakDetectionThresholdMs: Long = 0L,

    @Comment("SQLite busy timeout in milliseconds")
    val sqliteBusyTimeoutMs: Int = 30000
) {
    /**
     * Builds a JDBC URL for MariaDB connection.
     *
     * Uses the MariaDB Connector/J JDBC URL format.
     * Driver-level properties (useServerPrepStmts, prepStmtCacheSize, etc.)
     * are configured via HikariCP's addDataSourceProperty() method.
     *
     * @return JDBC URL in format: jdbc:mariadb://host:port/database
     */
    fun toMariaDbJdbcUrl(): String = "jdbc:mariadb://$host:$port/$database"

    /**
     * Builds a JDBC URL for MariaDB connection.
     * @deprecated Use [toMariaDbJdbcUrl] for clarity.
     */
    @Deprecated(
        "Use toMariaDbJdbcUrl() for clarity",
        ReplaceWith("toMariaDbJdbcUrl()"),
        DeprecationLevel.WARNING
    )
    fun toJdbcUrl(): String = toMariaDbJdbcUrl()

    /**
     * Creates the appropriate DatabaseManager based on the configured type.
     *
     * @param logger Logger instance for the database manager
     * @param poolName Optional custom pool name
     * @param dataFolder Plugin data folder for resolving relative SQLite paths
     * @return Configured DatabaseManager instance
     */
    fun createManager(
        logger: java.util.logging.Logger,
        poolName: String = "LoafyLib-HikariPool",
        dataFolder: java.io.File? = null
    ): me.cyljacky02.loafylib.database.DatabaseManager {
        return when (type) {
            DatabaseType.MARIADB -> me.cyljacky02.loafylib.database.MariaDbDatabaseManager(
                config = this,
                logger = logger,
                poolName = poolName
            )
            DatabaseType.SQLITE -> {
                // Resolve relative path against data folder if provided
                val resolvedConfig = if (dataFolder != null && !java.io.File(filePath).isAbsolute) {
                    copy(filePath = java.io.File(dataFolder, filePath).absolutePath)
                } else {
                    this
                }
                me.cyljacky02.loafylib.database.SqliteDatabaseManager(
                    config = resolvedConfig,
                    logger = logger,
                    poolName = poolName
                )
            }
        }
    }
}
