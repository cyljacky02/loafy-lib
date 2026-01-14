package me.cyljacky02.loafylib.config

import org.spongepowered.configurate.objectmapping.ConfigSerializable
import org.spongepowered.configurate.objectmapping.meta.Comment

/**
 * Configuration data class for MariaDB database connection settings.
 *
 * This class is designed to be serialized/deserialized by Configurate
 * and provides sensible defaults for local development.
 *
 * Uses MariaDB Connector/J (not MySQL Connector/J) for optimal performance
 * with MariaDB servers.
 *
 * @property host Database server hostname
 * @property port Database server port (default: 3306)
 * @property database Database name
 * @property username Database username
 * @property password Database password
 * @property poolSize HikariCP connection pool size (default: 10)
 */
@ConfigSerializable
data class DatabaseConfig(
    @Comment("Database server hostname")
    val host: String = "localhost",
    
    @Comment("Database server port")
    val port: Int = 3306,
    
    @Comment("Database name")
    val database: String = "minecraft",
    
    @Comment("Database username")
    val username: String = "root",
    
    @Comment("Database password")
    val password: String = "",
    
    @Comment("HikariCP connection pool size")
    val poolSize: Int = 10
) {
    companion object {
        /**
         * Creates a DatabaseConfig with default values.
         */
        fun defaults(): DatabaseConfig = DatabaseConfig()
    }
    
    /**
     * Builds a JDBC URL for MariaDB connection.
     *
     * Uses the MariaDB Connector/J JDBC URL format.
     * Driver-level properties (useServerPrepStmts, prepStmtCacheSize, etc.)
     * are configured via HikariCP's addDataSourceProperty() method.
     *
     * @return JDBC URL in format: jdbc:mariadb://host:port/database
     */
    fun toJdbcUrl(): String = "jdbc:mariadb://$host:$port/$database"
}
