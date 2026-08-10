package me.cyljacky02.loafylib.event

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import me.cyljacky02.loafylib.scheduler.PaperDispatchers
import org.bukkit.event.Event
import org.bukkit.event.EventException
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockEvent
import org.bukkit.event.entity.EntityEvent
import org.bukkit.event.inventory.InventoryEvent
import org.bukkit.event.player.PlayerEvent
import org.bukkit.event.world.ChunkEvent
import org.bukkit.plugin.EventExecutor
import org.bukkit.plugin.Plugin
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import kotlin.coroutines.Continuation

fun interface SuspendingEventDispatcherProvider {
    fun dispatcherFor(event: Event): CoroutineDispatcher

    companion object {
        fun default(plugin: Plugin): SuspendingEventDispatcherProvider =
            SuspendingEventDispatcherProvider { event ->
                if (event.isAsynchronous) {
                    return@SuspendingEventDispatcherProvider PaperDispatchers.async(plugin)
                }

                when (event) {
                    is PlayerEvent -> PaperDispatchers.entity(plugin, event.player)
                    is EntityEvent -> PaperDispatchers.entity(plugin, event.entity)
                    is BlockEvent -> PaperDispatchers.region(plugin, event.block.location)
                    is ChunkEvent -> PaperDispatchers.region(plugin, event.chunk.world, event.chunk.x, event.chunk.z)
                    is InventoryEvent -> {
                        val holder = event.view.player
                        PaperDispatchers.entity(plugin, holder)
                    }

                    else -> PaperDispatchers.main(plugin)
                }
            }
    }
}

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
 * - Sync events: Handler starts on the calling tick thread (global region / region / entity), and resumes after suspension on the dispatcher provided by [SuspendingEventDispatcherProvider]
 * - Async events: Handler starts on the calling async thread, and resumes after suspension on the dispatcher provided by [SuspendingEventDispatcherProvider]
 * - Uses `CoroutineStart.UNDISPATCHED` to run immediately in the current call frame until first suspension, then resume using the coroutine dispatcher
 *
 * ## Design Notes
 *
 * Inspired by MCCoroutine but simplified:
 * - No session management or configuration
 * - No custom event firing (use Paper's standard `callEvent`)
 * - Direct Paper scheduler integration via PluginManager.registerEvent()
 * - Minimal reflection, suspend vs non-suspend is determined during registration
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
    fun register(
        listener: Listener,
        plugin: Plugin,
        scope: CoroutineScope,
        dispatcherProvider: SuspendingEventDispatcherProvider = SuspendingEventDispatcherProvider.default(plugin)
    ) {
        val handlers = findEventHandlers(listener, plugin, scope, dispatcherProvider)
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
        scope: CoroutineScope,
        dispatcherProvider: SuspendingEventDispatcherProvider
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

            val isSuspend = when (paramCount) {
                1 -> false
                2 -> {
                    val continuationParam = method.parameterTypes[1]
                    if (continuationParam != Continuation::class.java) {
                        plugin.logger.warning(
                            "Invalid event handler: ${method.name} - second parameter must be Continuation for suspend handlers"
                        )
                        continue
                    }
                    true
                }
                else -> continue
            }

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
                executor = SuspendingEventExecutor(method, plugin, scope, dispatcherProvider, isSuspend, eventClass)
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
 * Suspend vs non-suspend is determined during registration (suspend handlers have a Continuation parameter).
 * Suspend handlers are wrapped in coroutines; regular functions are called directly.
 */
internal class SuspendingEventExecutor(
    private val method: Method,
    private val plugin: Plugin,
    private val scope: CoroutineScope,
    private val dispatcherProvider: SuspendingEventDispatcherProvider,
    private val isSuspend: Boolean,
    private val expectedEventClass: Class<out Event>
) : EventExecutor {

    override fun execute(listener: Listener, event: Event) {
        if (!plugin.isEnabled) return
        if (scope.coroutineContext[Job]?.isActive == false) return

        // Type safety check - Paper may dispatch parent event types to child handlers
        if (!expectedEventClass.isInstance(event)) return

        if (!isSuspend) {
            try {
                method.invoke(listener, event)
            } catch (e: InvocationTargetException) {
                throw EventException(e.cause)
            } catch (e: Throwable) {
                throw EventException(e)
            }
            return
        }

        val dispatcher = dispatcherProvider.dispatcherFor(event)

        // UNDISPATCHED = start immediately on current thread (zero overhead if no suspension)
        // After suspension, resume on the appropriate dispatcher
        scope.launch(dispatcher, CoroutineStart.UNDISPATCHED) {
            try {
                method.invokeSuspend(listener, event)
            } catch (e: InvocationTargetException) {
                throw (e.cause ?: e)
            }
        }
    }
}

/**
 * Invokes a method as a suspend function using Kotlin coroutine intrinsics.
 *
 * This works by passing the current continuation as the last parameter,
 * which is how Kotlin compiles suspend functions.
 */
@Suppress("UNCHECKED_CAST")
internal suspend fun Method.invokeSuspend(obj: Any, vararg args: Any?): Any? =
    kotlin.coroutines.intrinsics.suspendCoroutineUninterceptedOrReturn { cont ->
        invoke(obj, *args, cont)
    }
