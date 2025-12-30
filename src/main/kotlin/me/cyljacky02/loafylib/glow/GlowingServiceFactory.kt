package me.cyljacky02.loafylib.glow

import org.bukkit.plugin.Plugin

/**
 * Factory for creating the appropriate [GlowingService] implementation.
 *
 * This factory checks if PacketEvents is available at runtime and returns:
 * - [PacketEventsGlowingService] when PacketEvents is installed
 * - [NoOpGlowingService] when PacketEvents is not available
 *
 * This enables graceful degradation - LoafyLib loads successfully without PacketEvents,
 * but glowing features are disabled.
 *
 * ## Design Notes
 * 
 * The factory uses `Class.forName()` for the initial check because:
 * 1. It's the safest way to detect a soft dependency before any code references it
 * 2. Calling `PacketEvents.getAPI()` directly would throw `NoClassDefFoundError` if not installed
 * 3. This pattern is standard for Bukkit/Paper soft dependencies
 *
 * Once we know PacketEvents is available, the service can safely use `PacketEvents.getAPI()`.
 *
 * ## Usage Example
 * ```kotlin
 * val glowingService = GlowingServiceFactory.create(plugin)
 * if (glowingService.isAvailable()) {
 *     glowingService.setGlowing(entity, player, GlowColor.RED)
 * }
 * ```
 *
 * @see GlowingService
 * @see PacketEventsGlowingService
 * @see NoOpGlowingService
 */
object GlowingServiceFactory {

    /**
     * Creates the appropriate [GlowingService] implementation based on runtime availability.
     *
     * @param plugin the plugin instance for logging and event registration
     * @return [PacketEventsGlowingService] if PacketEvents is available, [NoOpGlowingService] otherwise
     */
    fun create(plugin: Plugin): GlowingService {
        return if (isPacketEventsAvailable()) {
            PacketEventsGlowingService(plugin)
        } else {
            NoOpGlowingService(plugin.logger)
        }
    }

    /**
     * Checks if PacketEvents is available at runtime.
     *
     * Uses class loading to detect PacketEvents presence. This is the standard
     * pattern for soft dependency detection in Bukkit/Paper plugins because:
     * - It doesn't require PacketEvents as a compile-time dependency
     * - It safely handles the case where PacketEvents classes don't exist
     * - It's evaluated once at factory creation time, not on every method call
     *
     * @return true if PacketEvents API class is loadable, false otherwise
     */
    private fun isPacketEventsAvailable(): Boolean {
        return try {
            Class.forName("com.github.retrooper.packetevents.PacketEvents")
            true
        } catch (e: ClassNotFoundException) {
            false
        } catch (e: NoClassDefFoundError) {
            false
        }
    }
}

