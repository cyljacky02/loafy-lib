package me.cyljacky02.loafylib.scheduler

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.entity.Entity
import org.bukkit.plugin.Plugin
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Unit tests for dispatcher graceful degradation when plugin is disabled.
 *
 * Note: This is a unit test (not property test) because there are only 4 finite
 * dispatcher types to test, and the behavior is deterministic.
 */
class DispatcherUnitTest : FunSpec({

    context("Property 1: Dispatcher graceful degradation on plugin disable") {
        
        test("AsyncDispatcher runs task directly when plugin is disabled") {
            val plugin = mockk<Plugin> {
                every { isEnabled } returns false
            }
            
            val dispatcher = AsyncDispatcher(plugin)
            val executed = AtomicBoolean(false)
            val executionThread = AtomicInteger(-1)
            val testThread = Thread.currentThread().id
            
            val runnable = Runnable {
                executed.set(true)
                executionThread.set(Thread.currentThread().id.toInt())
            }
            
            // dispatch() should run directly on current thread
            runBlocking {
                dispatcher.dispatch(coroutineContext, runnable)
            }
            
            executed.get() shouldBe true
            executionThread.get() shouldBe testThread.toInt()
        }

        test("MainDispatcher runs task directly when plugin is disabled") {
            val plugin = mockk<Plugin> {
                every { isEnabled } returns false
            }
            
            val dispatcher = MainDispatcher(plugin)
            val executed = AtomicBoolean(false)
            val executionThread = AtomicInteger(-1)
            val testThread = Thread.currentThread().id
            
            val runnable = Runnable {
                executed.set(true)
                executionThread.set(Thread.currentThread().id.toInt())
            }
            
            runBlocking {
                dispatcher.dispatch(coroutineContext, runnable)
            }
            
            executed.get() shouldBe true
            executionThread.get() shouldBe testThread.toInt()
        }

        test("EntityDispatcher runs task directly when plugin is disabled") {
            val plugin = mockk<Plugin> {
                every { isEnabled } returns false
            }
            val entity = mockk<Entity>()
            
            val dispatcher = EntityDispatcher(plugin, entity)
            val executed = AtomicBoolean(false)
            val executionThread = AtomicInteger(-1)
            val testThread = Thread.currentThread().id
            
            val runnable = Runnable {
                executed.set(true)
                executionThread.set(Thread.currentThread().id.toInt())
            }
            
            runBlocking {
                dispatcher.dispatch(coroutineContext, runnable)
            }
            
            executed.get() shouldBe true
            executionThread.get() shouldBe testThread.toInt()
            
            // Entity scheduler should NOT be called
            verify(exactly = 0) { entity.scheduler }
        }

        test("RegionDispatcher runs task directly when plugin is disabled") {
            val plugin = mockk<Plugin> {
                every { isEnabled } returns false
            }
            val world = mockk<World>()
            
            val dispatcher = RegionDispatcher(plugin, world, 0, 0)
            val executed = AtomicBoolean(false)
            val executionThread = AtomicInteger(-1)
            val testThread = Thread.currentThread().id
            
            val runnable = Runnable {
                executed.set(true)
                executionThread.set(Thread.currentThread().id.toInt())
            }
            
            runBlocking {
                dispatcher.dispatch(coroutineContext, runnable)
            }
            
            executed.get() shouldBe true
            executionThread.get() shouldBe testThread.toInt()
        }

        test("All dispatchers do not throw when plugin is disabled") {
            val plugin = mockk<Plugin> {
                every { isEnabled } returns false
            }
            val entity = mockk<Entity>()
            val world = mockk<World>()
            
            val dispatchers = listOf(
                AsyncDispatcher(plugin),
                MainDispatcher(plugin),
                EntityDispatcher(plugin, entity),
                RegionDispatcher(plugin, world, 10, 20)
            )
            
            var executionCount = 0
            
            dispatchers.forEach { dispatcher ->
                runBlocking {
                    dispatcher.dispatch(coroutineContext, Runnable {
                        executionCount++
                    })
                }
            }
            
            // All 4 dispatchers should have executed their tasks
            executionCount shouldBe 4
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
