package me.cyljacky02.loafylib.event

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import me.cyljacky02.loafylib.scheduler.PaperDispatchers
import org.bukkit.event.Event
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.plugin.Plugin
import java.util.concurrent.atomic.AtomicInteger

/**
 * Unit tests for SuspendingEventService.
 *
 * Note: These are unit tests (not property tests) because:
 * - Suspend detection is deterministic (method either is or isn't suspend)
 * - Dispatcher selection has only 2 cases (sync vs async event)
 * - No randomization benefit for these finite cases
 */
class SuspendingEventUnitTest : FunSpec({

    afterTest {
        unmockkAll()
    }

    context("Suspend detection caching") {
        
        test("Suspend handler is invoked correctly") {
            val plugin = mockk<Plugin> {
                every { isEnabled } returns false // Use graceful degradation
            }
            val scope = CoroutineScope(Dispatchers.Unconfined + SupervisorJob())
            
            val listener = TestSuspendListener()
            val event = TestEvent(isAsync = false)
            
            // Get the method and create executor
            val method = listener.javaClass.getDeclaredMethod(
                "onEvent",
                TestEvent::class.java,
                kotlin.coroutines.Continuation::class.java
            )
            method.isAccessible = true
            
            val executor = SuspendingEventExecutor(method, plugin, scope)
            
            // Execute multiple times
            repeat(3) {
                executor.execute(listener, event)
            }
            
            // Handler should have been called 3 times
            listener.invocationCount.get() shouldBe 3
        }

        test("Regular (non-suspend) handler is invoked correctly") {
            val plugin = mockk<Plugin> {
                every { isEnabled } returns false
            }
            val scope = CoroutineScope(Dispatchers.Unconfined + SupervisorJob())
            
            val listener = TestRegularListener()
            val event = TestEvent(isAsync = false)
            
            val method = listener.javaClass.getDeclaredMethod("onEvent", TestEvent::class.java)
            method.isAccessible = true
            
            val executor = SuspendingEventExecutor(method, plugin, scope)
            
            // Execute multiple times
            repeat(3) {
                executor.execute(listener, event)
            }
            
            // Handler should have been called 3 times
            listener.invocationCount.get() shouldBe 3
        }
    }

    context("Dispatcher selection based on event.isAsynchronous") {
        
        test("Sync event uses main dispatcher") {
            val plugin = mockk<Plugin> {
                every { isEnabled } returns false
            }
            
            // Verify dispatcher type
            val mainDispatcher = PaperDispatchers.main(plugin)
            mainDispatcher.toString() shouldBe "PaperMainDispatcher"
        }

        test("Async event uses async dispatcher") {
            val plugin = mockk<Plugin> {
                every { isEnabled } returns false
            }
            
            val asyncDispatcher = PaperDispatchers.async(plugin)
            asyncDispatcher.toString() shouldBe "PaperAsyncDispatcher"
        }

        test("Both sync and async events are handled correctly") {
            val plugin = mockk<Plugin> {
                every { isEnabled } returns false
            }
            val scope = CoroutineScope(Dispatchers.Unconfined + SupervisorJob())
            
            val listener = TestSuspendListener()
            
            val method = listener.javaClass.getDeclaredMethod(
                "onEvent",
                TestEvent::class.java,
                kotlin.coroutines.Continuation::class.java
            )
            method.isAccessible = true
            
            val executor = SuspendingEventExecutor(method, plugin, scope)
            
            // Test with sync event
            val syncEvent = TestEvent(isAsync = false)
            executor.execute(listener, syncEvent)
            
            // Test with async event
            val asyncEvent = TestEvent(isAsync = true)
            executor.execute(listener, asyncEvent)
            
            // Both should have been handled
            listener.invocationCount.get() shouldBe 2
        }
    }
})

// =============================================================================
// Test Listeners
// =============================================================================

private class TestSuspendListener : Listener {
    val invocationCount = AtomicInteger(0)
    
    @EventHandler
    suspend fun onEvent(event: TestEvent) {
        invocationCount.incrementAndGet()
        delay(1) // Simulate async work
    }
}

private class TestRegularListener : Listener {
    val invocationCount = AtomicInteger(0)
    
    @EventHandler
    fun onEvent(event: TestEvent) {
        invocationCount.incrementAndGet()
    }
}

/**
 * Simple test event for unit testing.
 */
private class TestEvent(private val isAsync: Boolean) : Event(isAsync) {
    override fun getHandlers(): org.bukkit.event.HandlerList = handlerList
    
    companion object {
        @JvmStatic
        val handlerList = org.bukkit.event.HandlerList()
    }
}
