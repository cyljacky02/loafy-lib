package me.cyljacky02.loafylib.animation.provider

import me.cyljacky02.loafylib.animation.core.AnimationProvider
import org.bukkit.plugin.Plugin
import java.util.logging.Logger

/**
 * Factory for creating AnimationProvider instances.
 *
 * Automatically selects the best available provider:
 * 1. PacketAnimationProvider if PacketEvents is available
 * 2. BukkitAnimationProvider as fallback
 */
object AnimationProviderFactory {

    /**
     * Create the best available animation provider.
     *
     * @param plugin The plugin instance
     * @param logger Optional logger for status messages
     * @return The best available AnimationProvider
     */
    fun create(plugin: Plugin, logger: Logger? = null): AnimationProvider {
        // Try PacketEvents first
        if (isPacketEventsAvailable()) {
            val provider = PacketAnimationProvider()
            if (provider.isAvailable()) {
                logger?.info("Animation provider: PacketEvents (enhanced features)")
                return provider
            }
        }

        // Fall back to Bukkit
        logger?.info("Animation provider: Bukkit API (basic features)")
        return BukkitAnimationProvider(plugin)
    }

    /**
     * Check if PacketEvents is available on the classpath.
     */
    private fun isPacketEventsAvailable(): Boolean {
        return try {
            Class.forName("com.github.retrooper.packetevents.PacketEvents")
            true
        } catch (e: ClassNotFoundException) {
            false
        }
    }
}

