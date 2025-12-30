package me.cyljacky02.loafylib.event

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import me.cyljacky02.loafylib.scheduler.PaperDispatchers
import org.bukkit.event.*
import org.bukkit.plugin.EventExecutor
import org.bukkit.plugin.Plugin
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method

/**
 * Service for registering event listeners with suspend function support.
 *
 * This allows event handlers to use Kotlin coroutines for async operations
 * without blocking the server thread.
 *
 * ## Usage
 *
 * ```kotlin
 * class MyListener : Listener {
 *     @EventHandler
 *     suspend fun onPlayerJoin(event: PlayerJoinEvent) {
 *         val data = database.fetchPlayerData(event.player.uniqueId) // suspend!
 *         event.player.sendMessage("Welcome back! Stats: $data")
 *     }
 *
 *     @EventHandler
 *     fun onPlayerQuit(event: PlayerQuitEvent) {
 *         // Regular non-suspend handlers work too
 *     }
 * }
 *
 * // Registration
 * SuspendingEventService.register(myListener, plugin, pluginScope)
 * ```
 *
 * ## Thread Safety
 *
 * - Sync events: Handler starts on main thread, resumes on main thread after suspension
 * - Async events: Handler starts on async thread, resumes on async thread after suspension
 * - Uses `CoroutineStart.UNDISPATCHED` for zero-overhead when no suspension occurs
 *
 * ## Design Notes
 *
 * Inspired by MCCoroutine but simplified:
 * - No session management or configuration
 * - No custom event firing (use Paper's standard `callEvent`)
 * - Direct Paper scheduler integration via PluginManager.registerEvent()
 * - Minimal reflection, cached after first call
 */
object SuspendingEventService {

    /**
     * Registers a listener with suspend function support.
     *
     * Scans the listener for `@EventHandler` annotated methods and wraps
     * suspend functions in coroutines. Non-suspend handlers work normally.
     *
     * Uses Paper's recommended `PluginManager.registerEvent()` API.
     *
     * @param listener The listener instance containing event handlers
     * @param plugin The plugin owning this listener
     * @param scope The coroutine scope for launching suspend handlers
     */
    fun register(listener: Listener, plugin: Plugin, scope: CoroutineScope) {
        val handlers = findEventHandlers(listener, plugin, scope)
        val pluginManager = plugin.server.pluginManager

        for (handler in handlers) {
            pluginManager.registerEvent(
                handler.eventClass,
                listener,
                handler.priority,
                handler.executor,
                plugin,
                handler.ignoreCancelled
            )
        }
    }

    /**
     * Finds all @EventHandler methods and creates executors for them.
     */
    private fun findEventHandlers(
        listener: Listener,
        plugin: Plugin,
        scope: CoroutineScope
    ): List<EventHandlerInfo> {
        val result = mutableListOf<EventHandlerInfo>()

        // Collect all methods (public + declared for private handlers)
        val methods = buildSet {
            addAll(listener.javaClass.methods)
            addAll(listener.javaClass.declaredMethods)
        }

        for (method in methods) {
            val annotation = method.getAnnotation(EventHandler::class.java) ?: continue
            if (method.isBridge || method.isSynthetic) continue

            // Validate parameter count (event + optional continuation for suspend)
            val paramCount = method.parameterTypes.size
            if (paramCount < 1 || paramCount > 2) continue

            val eventClass = try {
                @Suppress("UNCHECKED_CAST")
                method.parameterTypes[0].asSubclass(Event::class.java) as Class<out Event>
            } catch (e: ClassCastException) {
                plugin.logger.warning("Invalid event handler: ${method.name} - first parameter must be an Event")
                continue
            }

            method.isAccessible = true

            result.add(EventHandlerInfo(
                eventClass = eventClass,
                priority = annotation.priority,
                ignoreCancelled = annotation.ignoreCancelled,
                executor = SuspendingEventExecutor(method, plugin, scope)
            ))
        }

        return result
    }

    /**
     * Holds information about a single event handler method.
     */
    private data class EventHandlerInfo(
        val eventClass: Class<out Event>,
        val priority: EventPriority,
        val ignoreCancelled: Boolean,
        val executor: EventExecutor
    )
}


/**
 * Event executor that supports both regular and suspend functions.
 *
 * Uses reflection to detect suspend functions (they have a Continuation parameter)
 * and wraps them in coroutines. Regular functions are called directly.
 */
internal class SuspendingEventExecutor(
    private val method: Method,
    private val plugin: Plugin,
    private val scope: CoroutineScope
) : EventExecutor {

    // Cached after first invocation - null means not yet determined
    @Volatile
    private var isSuspend: Boolean? = null

    override fun execute(listener: Listener, event: Event) {
        try {
            // Determine dispatcher based on event type
            val dispatcher = if (event.isAsynchronous) {
                PaperDispatchers.async(plugin)
            } else {
                PaperDispatchers.main(plugin)
            }

            // UNDISPATCHED = start immediately on current thread (zero overhead if no suspension)
            // After suspension, resume on the appropriate dispatcher
            scope.launch(dispatcher, CoroutineStart.UNDISPATCHED) {
                invokeHandler(listener, event)
            }

        } catch (e: InvocationTargetException) {
            throw EventException(e.cause)
        } catch (e: Throwable) {
            throw EventException(e)
        }
    }

    /**
     * Invokes the handler method, detecting suspend vs regular on first call.
     */
    private suspend fun invokeHandler(listener: Listener, event: Event) {
        when (isSuspend) {
            null -> detectAndInvoke(listener, event)
            true -> method.invokeSuspend(listener, event)
            false -> method.invoke(listener, event)
        }
    }

    /**
     * First-call detection: try suspend, fall back to regular.
     */
    private suspend fun detectAndInvoke(listener: Listener, event: Event) {
        try {
            method.invokeSuspend(listener, event)
            isSuspend = true
        } catch (e: IllegalArgumentException) {
            // Not a suspend function - invoke normally
            method.invoke(listener, event)
            isSuspend = false
        }
    }
}

/**
 * Invokes a method as a suspend function using Kotlin coroutine intrinsics.
 *
 * This works by passing the current continuation as the last parameter,
 * which is how Kotlin compiles suspend functions.
 *
 * @throws IllegalArgumentException if the method is not a suspend function
 */
@Suppress("UNCHECKED_CAST")
internal suspend fun Method.invokeSuspend(obj: Any, vararg args: Any?): Any? =
    kotlin.coroutines.intrinsics.suspendCoroutineUninterceptedOrReturn { cont ->
        invoke(obj, *args, cont)
    }
