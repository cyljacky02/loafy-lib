package me.cyljacky02.loafylib.redis

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

/**
 * Unit tests for Redis reconnect callback registration and invocation.
 *
 * Note: This is a unit test (not property test) because callback mechanics
 * are deterministic and don't benefit from randomization.
 * Tests directly on CopyOnWriteArrayList behavior without requiring Redis.
 */
class RedisCallbackUnitTest : FunSpec({

    context("Reconnect callback registration and invocation") {
        
        test("Registered callback is invoked on reconnect event") {
            val callbackManager = TestCallbackManager()
            var callbackInvoked = false
            
            callbackManager.onReconnect {
                callbackInvoked = true
            }
            
            // Simulate reconnect event
            runBlocking {
                callbackManager.invokeReconnectCallbacks()
            }
            
            callbackInvoked shouldBe true
        }

        test("Multiple registered callbacks are all invoked") {
            val callbackManager = TestCallbackManager()
            val invokedCallbacks = mutableListOf<Int>()
            
            callbackManager.onReconnect { invokedCallbacks.add(1) }
            callbackManager.onReconnect { invokedCallbacks.add(2) }
            callbackManager.onReconnect { invokedCallbacks.add(3) }
            
            runBlocking {
                callbackManager.invokeReconnectCallbacks()
            }
            
            invokedCallbacks shouldBe listOf(1, 2, 3)
        }

        test("Callback removed via AutoCloseable is NOT invoked") {
            val callbackManager = TestCallbackManager()
            val invokedCallbacks = mutableListOf<String>()
            
            callbackManager.onReconnect { invokedCallbacks.add("callback1") }
            val handle = callbackManager.onReconnect { invokedCallbacks.add("callback2") }
            callbackManager.onReconnect { invokedCallbacks.add("callback3") }
            
            // Remove callback2
            handle.close()
            
            runBlocking {
                callbackManager.invokeReconnectCallbacks()
            }
            
            // callback2 should NOT be in the list
            invokedCallbacks shouldBe listOf("callback1", "callback3")
        }

        test("Removing callback twice is safe (idempotent)") {
            val callbackManager = TestCallbackManager()
            val invocationCount = AtomicInteger(0)
            
            val handle = callbackManager.onReconnect { invocationCount.incrementAndGet() }
            
            // Remove twice
            handle.close()
            handle.close()
            
            runBlocking {
                callbackManager.invokeReconnectCallbacks()
            }
            
            invocationCount.get() shouldBe 0
        }

        test("Callbacks are invoked exactly once per reconnect event") {
            val callbackManager = TestCallbackManager()
            val invocationCount = AtomicInteger(0)
            
            callbackManager.onReconnect { invocationCount.incrementAndGet() }
            
            // Simulate multiple reconnect events
            runBlocking {
                callbackManager.invokeReconnectCallbacks()
                callbackManager.invokeReconnectCallbacks()
                callbackManager.invokeReconnectCallbacks()
            }
            
            invocationCount.get() shouldBe 3
        }

        test("Callback exception does not prevent other callbacks from running") {
            val callbackManager = TestCallbackManager()
            val invokedCallbacks = mutableListOf<Int>()
            
            callbackManager.onReconnect { invokedCallbacks.add(1) }
            callbackManager.onReconnect { throw RuntimeException("Callback 2 failed") }
            callbackManager.onReconnect { invokedCallbacks.add(3) }
            
            runBlocking {
                callbackManager.invokeReconnectCallbacks()
            }
            
            // Both callback 1 and 3 should have been invoked despite callback 2 failing
            invokedCallbacks shouldBe listOf(1, 3)
        }

        test("No callbacks registered means no invocations") {
            val callbackManager = TestCallbackManager()
            
            // Should not throw
            runBlocking {
                callbackManager.invokeReconnectCallbacks()
            }
            
            // No assertions needed - just verifying no exception
        }

        test("Callback can be registered and removed during iteration") {
            val callbackManager = TestCallbackManager()
            val invokedCallbacks = mutableListOf<Int>()
            var handle2: AutoCloseable? = null
            
            callbackManager.onReconnect { 
                invokedCallbacks.add(1)
                // Register a new callback during iteration
                callbackManager.onReconnect { invokedCallbacks.add(4) }
            }
            handle2 = callbackManager.onReconnect { 
                invokedCallbacks.add(2)
                // Remove self during iteration (CopyOnWriteArrayList handles this safely)
                handle2?.close()
            }
            callbackManager.onReconnect { invokedCallbacks.add(3) }
            
            runBlocking {
                callbackManager.invokeReconnectCallbacks()
            }
            
            // All original callbacks should run (CopyOnWriteArrayList iterates over snapshot)
            invokedCallbacks shouldBe listOf(1, 2, 3)
            
            // Second invocation should include callback 4 but not callback 2
            invokedCallbacks.clear()
            runBlocking {
                callbackManager.invokeReconnectCallbacks()
            }
            
            invokedCallbacks shouldBe listOf(1, 3, 4)
        }
    }
})

/**
 * Test implementation that mimics LettuceRedisManager's callback mechanism.
 * Uses CopyOnWriteArrayList for thread-safe iteration during modification.
 */
private class TestCallbackManager {
    private val reconnectCallbacks = CopyOnWriteArrayList<suspend () -> Unit>()

    fun onReconnect(callback: suspend () -> Unit): AutoCloseable {
        reconnectCallbacks.add(callback)
        return AutoCloseable { reconnectCallbacks.remove(callback) }
    }

    suspend fun invokeReconnectCallbacks() {
        for (callback in reconnectCallbacks) {
            try {
                callback()
            } catch (e: Exception) {
                // Log but continue (mimics real implementation)
            }
        }
    }
}
