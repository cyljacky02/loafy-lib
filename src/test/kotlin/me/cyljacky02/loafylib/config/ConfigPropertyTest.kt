package me.cyljacky02.loafylib.config

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.bind
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import org.spongepowered.configurate.kotlin.extensions.get
import org.spongepowered.configurate.yaml.NodeStyle
import org.spongepowered.configurate.yaml.YamlConfigurationLoader
import java.io.StringReader
import java.io.StringWriter

/**
 * Helper to serialize and deserialize a config object via YAML.
 */
inline fun <reified T : Any> roundTrip(config: T): T {
    val writer = StringWriter()
    
    // Serialize to YAML
    val saveLoader = YamlConfigurationLoader.builder()
        .sink { java.io.BufferedWriter(writer) }
        .nodeStyle(NodeStyle.BLOCK)
        .build()
    
    val saveNode = saveLoader.createNode()
    saveNode.set(config)
    saveLoader.save(saveNode)
    
    val yaml = writer.toString()
    
    // Deserialize from YAML
    val loadLoader = YamlConfigurationLoader.builder()
        .source { StringReader(yaml).buffered() }
        .build()
    
    val loadNode = loadLoader.load()
    return loadNode.get<T>() ?: throw AssertionError("Failed to deserialize config from YAML")
}

/**
 * Property-based tests for configuration round-trip serialization.
 */
class ConfigPropertyTest : FunSpec({

    // Generator for valid RedisConfig
    val redisConfigArb = Arb.bind(
        Arb.string(1..50),           // host (non-empty)
        Arb.int(1..65535),           // port (valid port range)
        Arb.string(0..50),           // password (can be empty)
        Arb.int(0..15),              // database (Redis db index 0-15)
        Arb.int(1..100)              // poolSize (reasonable range)
    ) { host, port, password, database, poolSize ->
        RedisConfig(
            host = host,
            port = port,
            password = password,
            database = database,
            poolSize = poolSize
        )
    }

    // Generator for valid DatabaseConfig
    val databaseConfigArb = Arb.bind(
        Arb.string(1..50),           // host (non-empty)
        Arb.int(1..65535),           // port (valid port range)
        Arb.string(1..50),           // database name (non-empty)
        Arb.string(1..50),           // username (non-empty)
        Arb.string(0..50),           // password (can be empty)
        Arb.int(1..100)              // poolSize (reasonable range)
    ) { host, port, database, username, password, poolSize ->
        DatabaseConfig(
            host = host,
            port = port,
            database = database,
            username = username,
            password = password,
            poolSize = poolSize
        )
    }



    context("Config round-trip serialization") {
        
        test("RedisConfig round-trip: serialize to YAML then deserialize produces equivalent object") {
            checkAll(100, redisConfigArb) { original ->
                val restored = roundTrip(original)
                restored shouldBe original
            }
        }

        test("DatabaseConfig round-trip: serialize to YAML then deserialize produces equivalent object") {
            checkAll(100, databaseConfigArb) { original ->
                val restored = roundTrip(original)
                restored shouldBe original
            }
        }
    }

    context("Edge cases") {
        
        test("RedisConfig with empty password serializes correctly") {
            val config = RedisConfig(password = "")
            val restored = roundTrip(config)
            restored shouldBe config
        }

        test("DatabaseConfig with empty password serializes correctly") {
            val config = DatabaseConfig(password = "")
            val restored = roundTrip(config)
            restored shouldBe config
        }

        test("RedisConfig with special characters in password") {
            val config = RedisConfig(password = "p@ss:word/with\\special\"chars")
            val restored = roundTrip(config)
            restored shouldBe config
        }
    }
})
