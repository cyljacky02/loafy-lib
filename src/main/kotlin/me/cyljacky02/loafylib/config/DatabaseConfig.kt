package me.cyljacky02.loafylib.config

import org.spongepowered.configurate.objectmapping.ConfigSerializable
import org.spongepowered.configurate.objectmapping.meta.Comment

/**
 * Configuration data class for MariaDB/MySQL database connection settings.
 *
 * This class is designed to be serialized/deserialized by Configurate
 * and provides sensible defaults for local development.
 *
 * @property host Database server hostname
 * @property port Database server port
 * @property database Database name
 * @property username Database username
 * @property password Database password
 * @property poolSize HikariCP connection pool size
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
     * Uses the MariaDB JDBC driver format with recommended connection parameters:
     * - useSSL=false: Disable SSL for local development (override in production)
     * - allowPublicKeyRetrieval=true: Required for some authentication methods
     */
    fun toJdbcUrl(): String = "jdbc:mariadb://$host:$port/$database"
}
