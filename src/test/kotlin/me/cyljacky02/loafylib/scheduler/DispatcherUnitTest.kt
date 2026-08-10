package me.cyljacky02.loafylib.scheduler

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.bukkit.World
import org.bukkit.entity.Entity
import org.bukkit.plugin.Plugin
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Unit tests for dispatcher graceful degradation when plugin is disabled.
 *
 * Verifies the kotlinx.coroutines upstream dispatcher contract: when the scheduler
 * rejects a task (plugin disabled), the Job is cancelled and the block is dispatched
 * to [kotlinx.coroutines.Dispatchers.IO] as a fallback — never dropped.
 * See `ExecutorCoroutineDispatcherImpl` and kotlinx.coroutines commit #2012.
 *
 * Note: This is a unit test (not property test) because there are only 4 finite
 * dispatcher types to test, and the behavior is deterministic.
 */
class DispatcherUnitTest : FunSpec({

    context("Dispatcher graceful degradation on plugin disable") {
        
        test("AsyncDispatcher cancels job and dispatches to IO fallback when plugin is disabled") {
            val plugin = mockk<Plugin> {
                every { isEnabled } returns false
                every { name } returns "TestPlugin"
            }
            
            val dispatcher = AsyncDispatcher(plugin)
            val executed = CountDownLatch(1)
            
            val runnable = Runnable {
                executed.countDown()
            }
            
            // dispatch() should cancel the job and dispatch to Dispatchers.IO
            shouldThrow<java.util.concurrent.CancellationException> {
                runBlocking {
                    dispatcher.dispatch(coroutineContext, runnable)
                }
            }
            
            // Block MUST be executed (on IO fallback) — dropping violates dispatcher contract
            executed.await(1, TimeUnit.SECONDS) shouldBe true
        }

        test("MainDispatcher cancels job and dispatches to IO fallback when plugin is disabled") {
            val plugin = mockk<Plugin> {
                every { isEnabled } returns false
                every { name } returns "TestPlugin"
            }
            
            val dispatcher = MainDispatcher(plugin)
            val executed = CountDownLatch(1)
            
            val runnable = Runnable {
                executed.countDown()
            }
            
            shouldThrow<java.util.concurrent.CancellationException> {
                runBlocking {
                    dispatcher.dispatch(coroutineContext, runnable)
                }
            }
            
            // Block MUST be executed (on IO fallback) — dropping violates dispatcher contract
            executed.await(1, TimeUnit.SECONDS) shouldBe true
        }

        test("EntityDispatcher cancels job and dispatches to IO fallback when plugin is disabled") {
            val plugin = mockk<Plugin> {
                every { isEnabled } returns false
                every { name } returns "TestPlugin"
            }
            val entity = mockk<Entity>()
            
            val dispatcher = EntityDispatcher(plugin, entity)
            val executed = CountDownLatch(1)
            
            val runnable = Runnable {
                executed.countDown()
            }
            
            shouldThrow<java.util.concurrent.CancellationException> {
                runBlocking {
                    dispatcher.dispatch(coroutineContext, runnable)
                }
            }
            
            // Block MUST be executed (on IO fallback) — dropping violates dispatcher contract
            executed.await(1, TimeUnit.SECONDS) shouldBe true
            
            // Entity scheduler should NOT be called when plugin is disabled
            verify(exactly = 0) { entity.scheduler }
        }

        test("RegionDispatcher cancels job and dispatches to IO fallback when plugin is disabled") {
            val plugin = mockk<Plugin> {
                every { isEnabled } returns false
                every { name } returns "TestPlugin"
            }
            val world = mockk<World>()
            
            val dispatcher = RegionDispatcher(plugin, world, 0, 0)
            val executed = CountDownLatch(1)
            
            val runnable = Runnable {
                executed.countDown()
            }
            
            shouldThrow<java.util.concurrent.CancellationException> {
                runBlocking {
                    dispatcher.dispatch(coroutineContext, runnable)
                }
            }
            
            // Block MUST be executed (on IO fallback) — dropping violates dispatcher contract
            executed.await(1, TimeUnit.SECONDS) shouldBe true
        }

        test("All dispatchers cancel job and dispatch to IO fallback when plugin is disabled") {
            val plugin = mockk<Plugin> {
                every { isEnabled } returns false
                every { name } returns "TestPlugin"
            }
            val entity = mockk<Entity>()
            val world = mockk<World>()
            
            val dispatchers = listOf(
                AsyncDispatcher(plugin),
                MainDispatcher(plugin),
                EntityDispatcher(plugin, entity),
                RegionDispatcher(plugin, world, 10, 20)
            )
            
            val executionLatch = CountDownLatch(dispatchers.size)
            
            dispatchers.forEach { dispatcher ->
                shouldThrow<java.util.concurrent.CancellationException> {
                    runBlocking {
                        dispatcher.dispatch(coroutineContext, Runnable {
                            executionLatch.countDown()
                        })
                    }
                }
            }
            
            // ALL blocks must be executed (on IO fallback) — dropping violates dispatcher contract
            executionLatch.await(2, TimeUnit.SECONDS) shouldBe true
        }
    }

    context("Dispatcher toString") {
        
        test("AsyncDispatcher has descriptive toString") {
            val plugin = mockk<Plugin>()
            val dispatcher = AsyncDispatcher(plugin)
            dispatcher.toString() shouldBe "PaperAsyncDispatcher"
        }

        test("MainDispatcher has descriptive toString") {
            val plugin = mockk<Plugin>()
            val dispatcher = MainDispatcher(plugin)
            dispatcher.toString() shouldBe "PaperMainDispatcher"
        }

        test("EntityDispatcher includes entity UUID in toString") {
            val plugin = mockk<Plugin>()
            val uuid = java.util.UUID.randomUUID()
            val entity = mockk<Entity> {
                every { uniqueId } returns uuid
            }
            val dispatcher = EntityDispatcher(plugin, entity)
            dispatcher.toString() shouldBe "PaperEntityDispatcher($uuid)"
        }

        test("RegionDispatcher includes world and chunk coords in toString") {
            val plugin = mockk<Plugin>()
            val world = mockk<World> {
                every { name } returns "world"
            }
            val dispatcher = RegionDispatcher(plugin, world, 5, 10)
            dispatcher.toString() shouldBe "PaperRegionDispatcher(world, 5, 10)"
        }
    }
})
