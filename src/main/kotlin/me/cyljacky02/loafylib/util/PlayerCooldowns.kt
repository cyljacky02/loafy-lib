package me.cyljacky02.loafylib.util

import me.cyljacky02.loafylib.plugin.PluginComponent
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerQuitEvent
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Per-player cooldown management with automatic cleanup.
 *
 * Tracks cooldowns by player UUID and NamespacedKey, automatically cleaning up
 * when players disconnect. Thread-safe for use from async contexts.
 *
 * ## Usage
 *
 * ```kotlin
 * class MyPlugin : LoafyPlugin() {
 *     override fun components() = listOf(
 *         PlayerCooldowns(),
 *         MyService(registry.get())
 *     )
 * }
 *
 * class MyService(private val cooldowns: PlayerCooldowns) : PluginComponent, Listener {
 *     private val wandKey = NamespacedKey("myplugin", "wand")
 *
 *     @EventHandler
 *     suspend fun onInteract(event: PlayerInteractEvent) {
 *         val player = event.player
 *         if (cooldowns.isOnCooldown(player, wandKey)) {
 *             val remaining = cooldowns.getRemainingCooldown(player, wandKey)
 *             player.sendMessage("Cooldown: ${remaining?.inWholeSeconds}s")
 *             return
 *         }
 *         // Do action...
 *         cooldowns.setCooldown(player, wandKey, 5.seconds)
 *     }
 * }
 * ```
 *
 * @see PluginComponent
 */
class PlayerCooldowns : PluginComponent, Listener {

    // Map<PlayerUUID, Map<CooldownKey, ExpirationTimeMillis>>
    private val cooldowns = ConcurrentHashMap<UUID, ConcurrentHashMap<NamespacedKey, Long>>()

    /**
     * Checks if a player is currently on cooldown for the given key.
     *
     * @param player The player to check
     * @param key The cooldown identifier
     * @return true if on cooldown, false otherwise
     */
    fun isOnCooldown(player: Player, key: NamespacedKey): Boolean {
        val playerCooldowns = cooldowns[player.uniqueId] ?: return false
        val expiration = playerCooldowns[key] ?: return false
        
        if (System.currentTimeMillis() >= expiration) {
            playerCooldowns.remove(key)
            return false
        }
        return true
    }

    /**
     * Sets a cooldown for a player.
     *
     * @param player The player to set cooldown for
     * @param key The cooldown identifier
     * @param duration How long the cooldown should last
     */
    fun setCooldown(player: Player, key: NamespacedKey, duration: Duration) {
        val expiration = System.currentTimeMillis() + duration.inWholeMilliseconds
        cooldowns
            .computeIfAbsent(player.uniqueId) { ConcurrentHashMap() }
            .put(key, expiration)
    }

    /**
     * Gets the remaining cooldown duration, or null if not on cooldown.
     *
     * @param player The player to check
     * @param key The cooldown identifier
     * @return Remaining duration, or null if no active cooldown
     */
    fun getRemainingCooldown(player: Player, key: NamespacedKey): Duration? {
        val playerCooldowns = cooldowns[player.uniqueId] ?: return null
        val expiration = playerCooldowns[key] ?: return null
        
        val remaining = expiration - System.currentTimeMillis()
        if (remaining <= 0) {
            playerCooldowns.remove(key)
            return null
        }
        return remaining.milliseconds
    }

    /**
     * Clears a specific cooldown for a player.
     *
     * @param player The player
     * @param key The cooldown to clear
     */
    fun clearCooldown(player: Player, key: NamespacedKey) {
        cooldowns[player.uniqueId]?.remove(key)
    }

    /**
     * Clears all cooldowns for a player.
     *
     * @param player The player
     */
    fun clearAllCooldowns(player: Player) {
        cooldowns.remove(player.uniqueId)
    }

    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        cooldowns.remove(event.player.uniqueId)
    }

    override suspend fun initialize() {
        // No initialization needed
    }

    override suspend fun shutdown() {
        cooldowns.clear()
    }
}
