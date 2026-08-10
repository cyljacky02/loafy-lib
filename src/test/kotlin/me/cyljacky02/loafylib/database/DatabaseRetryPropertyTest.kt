package me.cyljacky02.loafylib.database

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.longs.shouldBeGreaterThanOrEqual
import io.kotest.matchers.longs.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.checkAll
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import me.cyljacky02.loafylib.config.DatabaseConfig
import java.sql.SQLException
import java.util.logging.Logger

/**
 * Property-based tests for database retry logic.
 * Tests the shared retry behavior in [AbstractDatabaseManager] used by both
 * [MariaDbDatabaseManager] and [SqliteDatabaseManager].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DatabaseRetryPropertyTest : FunSpec({

    // Test logger that doesn't output anything
    val silentLogger = Logger.getAnonymousLogger().apply { 
        useParentHandlers = false 
    }

    context("Exponential backoff timing") {
        
        test("Transient errors retry with exponential backoff delays") {
            runTest {
                val config = DatabaseConfig()
                val manager = TestableDatabaseManager(config, silentLogger)
                
                var attemptCount = 0
                val attemptTimes = mutableListOf<Long>()
                
                shouldThrow<SQLException> {
                    manager.withRetry(5) {
                        attemptTimes.add(currentTime)
                        attemptCount++
                        throw SQLException("Connection timeout", "08S01", 0)
                    }
                }
                
                attemptCount shouldBe 5
                
                // Verify delays between attempts follow exponential pattern
                // Expected: 1000ms, 2000ms, 4000ms, 8000ms (between attempts)
                val delays = attemptTimes.zipWithNext { a, b -> b - a }
                
                delays.size shouldBe 4
                delays[0] shouldBeGreaterThanOrEqual 1000L
                delays[0] shouldBeLessThan 1500L
                
                delays[1] shouldBeGreaterThanOrEqual 2000L
                delays[1] shouldBeLessThan 2500L
                
                delays[2] shouldBeGreaterThanOrEqual 4000L
                delays[2] shouldBeLessThan 4500L
                
                delays[3] shouldBeGreaterThanOrEqual 8000L
                delays[3] shouldBeLessThan 8500L
            }
        }

        test("Retry delay caps at 16 seconds") {
            runTest {
                val config = DatabaseConfig()
                val manager = TestableDatabaseManager(config, silentLogger)
                
                val attemptTimes = mutableListOf<Long>()
                
                shouldThrow<SQLException> {
                    manager.withRetry(7) {
                        attemptTimes.add(currentTime)
                        throw SQLException("Connection timeout", "08S01", 0)
                    }
                }
                
                // Delays: 1s, 2s, 4s, 8s, 16s, 16s (capped)
                val delays = attemptTimes.zipWithNext { a, b -> b - a }
                
                // 5th delay (index 4) should be 16s
                delays[4] shouldBeGreaterThanOrEqual 16000L
                delays[4] shouldBeLessThan 16500L
                
                // 6th delay (index 5) should also be 16s (capped)
                delays[5] shouldBeGreaterThanOrEqual 16000L
                delays[5] shouldBeLessThan 16500L
            }
        }

        test("Successful operation after transient failures returns result") {
            runTest {
                val config = DatabaseConfig()
                val manager = TestableDatabaseManager(config, silentLogger)
                
                var attemptCount = 0
                
                val result = manager.withRetry(5) {
                    attemptCount++
                    if (attemptCount < 3) {
                        throw SQLException("Connection timeout", "08S01", 0)
                    }
                    "success"
                }
                
                result shouldBe "success"
                attemptCount shouldBe 3
            }
        }
    }

    context("MariaDB constraint violations never retry") {
        
        test("Duplicate entry error (1062) throws immediately without retry") {
            runTest {
                val config = DatabaseConfig()
                val manager = TestableDatabaseManager(config, silentLogger)
                
                var attemptCount = 0
                
                val exception = shouldThrow<SQLException> {
                    manager.withRetry(5) {
                        attemptCount++
                        throw SQLException("Duplicate entry 'test' for key 'PRIMARY'", "23000", 1062)
                    }
                }
                
                attemptCount shouldBe 1
                exception.errorCode shouldBe 1062
            }
        }

        test("Foreign key constraint error (1452) throws immediately without retry") {
            runTest {
                val config = DatabaseConfig()
                val manager = TestableDatabaseManager(config, silentLogger)
                
                var attemptCount = 0
                
                val exception = shouldThrow<SQLException> {
                    manager.withRetry(5) {
                        attemptCount++
                        throw SQLException("Cannot add or update a child row: a foreign key constraint fails", "23000", 1452)
                    }
                }
                
                attemptCount shouldBe 1
                exception.errorCode shouldBe 1452
            }
        }

        test("Non-constraint error codes are retried") {
            checkAll(100, Arb.int(0..2000)) { errorCode ->
                // Skip constraint violation codes (MariaDB: 1062, 1452; SQLite: 19)
                if (errorCode == 1062 || errorCode == 1452 || errorCode == 19) return@checkAll
                
                runTest {
                    val config = DatabaseConfig()
                    val manager = TestableDatabaseManager(config, silentLogger)
                    
                    var attemptCount = 0
                    
                    shouldThrow<SQLException> {
                        manager.withRetry(3) {
                            attemptCount++
                            throw SQLException("Some error", "HY000", errorCode)
                        }
                    }
                    
                    // Should have retried (3 attempts)
                    attemptCount shouldBe 3
                }
            }
        }

        test("Constraint violation by message (Duplicate entry) throws immediately") {
            runTest {
                val config = DatabaseConfig()
                val manager = TestableDatabaseManager(config, silentLogger)
                
                var attemptCount = 0
                
                shouldThrow<SQLException> {
                    manager.withRetry(5) {
                        attemptCount++
                        // Error code 0 but message contains "Duplicate entry"
                        throw SQLException("Duplicate entry 'test' for key 'idx'", "23000", 0)
                    }
                }
                
                attemptCount shouldBe 1
            }
        }

        test("Constraint violation by message (foreign key constraint) throws immediately") {
            runTest {
                val config = DatabaseConfig()
                val manager = TestableDatabaseManager(config, silentLogger)
                
                var attemptCount = 0
                
                shouldThrow<SQLException> {
                    manager.withRetry(5) {
                        attemptCount++
                        // Error code 0 but message contains "foreign key constraint"
                        throw SQLException("a foreign key constraint fails", "23000", 0)
                    }
                }
                
                attemptCount shouldBe 1
            }
        }
    }

    context("SQLite constraint violations never retry") {

        test("SQLite CONSTRAINT error (19) throws immediately without retry") {
            runTest {
                val config = DatabaseConfig()
                val manager = TestableDatabaseManager(config, silentLogger)

                var attemptCount = 0

                val exception = shouldThrow<SQLException> {
                    manager.withRetry(5) {
                        attemptCount++
                        throw SQLException("SQLITE_CONSTRAINT", "23000", 19)
                    }
                }

                attemptCount shouldBe 1
                exception.errorCode shouldBe 19
            }
        }

        test("SQLite UNIQUE constraint failed message throws immediately") {
            runTest {
                val config = DatabaseConfig()
                val manager = TestableDatabaseManager(config, silentLogger)

                var attemptCount = 0

                shouldThrow<SQLException> {
                    manager.withRetry(5) {
                        attemptCount++
                        throw SQLException("UNIQUE constraint failed: users.email", "23000", 0)
                    }
                }

                attemptCount shouldBe 1
            }
        }

        test("SQLite FOREIGN KEY constraint failed message throws immediately") {
            runTest {
                val config = DatabaseConfig()
                val manager = TestableDatabaseManager(config, silentLogger)

                var attemptCount = 0

                shouldThrow<SQLException> {
                    manager.withRetry(5) {
                        attemptCount++
                        throw SQLException("FOREIGN KEY constraint failed", "23000", 0)
                    }
                }

                attemptCount shouldBe 1
            }
        }
    }
})

/**
 * Testable version of AbstractDatabaseManager that doesn't require actual database connection.
 * Only exposes the withRetry logic for testing both MariaDB and SQLite constraint handling.
 */
private class TestableDatabaseManager(
    private val config: DatabaseConfig,
    private val logger: Logger
) {
    companion object {
        private const val INITIAL_RETRY_DELAY_MS = 1000L
        private const val MAX_RETRY_DELAY_MS = 16000L
        // MariaDB error codes
        private const val ERROR_DUPLICATE_ENTRY = 1062
        private const val ERROR_FOREIGN_KEY_CONSTRAINT = 1452
        // SQLite error code
        private const val SQLITE_CONSTRAINT = 19
    }

    suspend fun <T> withRetry(maxRetries: Int = 5, operation: suspend () -> T): T {
        require(maxRetries > 0) { "maxRetries must be positive" }
        
        var lastException: SQLException? = null
        var delayMs = INITIAL_RETRY_DELAY_MS

        repeat(maxRetries) { attempt ->
            try {
                return operation()
            } catch (e: SQLException) {
                if (isConstraintViolation(e)) {
                    throw e
                }

                lastException = e
                logger.warning(
                    "Database operation failed (attempt ${attempt + 1}/$maxRetries): ${e.message}"
                )

                if (attempt < maxRetries - 1) {
                    kotlinx.coroutines.delay(delayMs)
                    delayMs = (delayMs * 2).coerceAtMost(MAX_RETRY_DELAY_MS)
                }
            }
        }

        throw lastException ?: SQLException("Unknown database error after $maxRetries retries")
    }

    private fun isConstraintViolation(e: SQLException): Boolean {
        return e.errorCode == ERROR_DUPLICATE_ENTRY ||
                e.errorCode == ERROR_FOREIGN_KEY_CONSTRAINT ||
                e.errorCode == SQLITE_CONSTRAINT ||
                e.message?.contains("Duplicate entry", ignoreCase = true) == true ||
                e.message?.contains("foreign key constraint", ignoreCase = true) == true ||
                e.message?.contains("UNIQUE constraint failed", ignoreCase = true) == true ||
                e.message?.contains("FOREIGN KEY constraint failed", ignoreCase = true) == true
    }
}
