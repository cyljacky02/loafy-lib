package me.cyljacky02.loafylib.protection

import org.bukkit.Location
import org.bukkit.entity.Player

/**
 * Hook for checking build permissions with protection plugins.
 *
 * Integrates with protection plugins (WorldGuard, Lands, Residence, GriefPrevention) when available.
 * Returns true if no protection plugins are installed.
 *
 * ## Thread Safety
 *
 * Must be called from entity/region thread (Folia) or main thread (Paper).
 * WorldGuard requires main thread access for SessionManager.
 *
 * ## Usage
 *
 * ```kotlin
 * class MyPlugin : LoafyPlugin() {
 *     private lateinit var protection: ProtectionHook
 *
 *     override fun onPluginEnable() {
 *         protection = ProtectionHookFactory.create(this)
 *     }
 *
 *     // In event handler (already on tick thread)
 *     @EventHandler
 *     fun onInteract(event: PlayerInteractEvent) {
 *         val location = event.clickedBlock?.location ?: return
 *         if (!protection.canBuild(event.player, location)) {
 *             event.player.sendMessage("You cannot build here!")
 *             event.isCancelled = true
 *         }
 *     }
 * }
 * ```
 *
 * @see ProtectionHookFactory
 */
interface ProtectionHook {

    /**
     * Checks if the player can build (place blocks) at the specified location.
     *
     * Must be called from entity/region thread (Folia) or main thread (Paper).
     *
     * @param player The player attempting to build
     * @param location The location to check
     * @return true if building is allowed, false otherwise
     */
    fun canBuild(player: Player, location: Location): Boolean

    /**
     * Checks if the player can break blocks at the specified location.
     *
     * Must be called from entity/region thread (Folia) or main thread (Paper).
     *
     * @param player The player attempting to break
     * @param location The location to check
     * @return true if breaking is allowed, false otherwise
     */
    fun canBreak(player: Player, location: Location): Boolean

    /**
     * Whether any protection plugin integration is available.
     *
     * Returns false when no protection plugins are installed,
     * in which case [canBuild] and [canBreak] always return true.
     */
    val isAvailable: Boolean
}

/**
 * No-op implementation when no protection plugins are installed.
 *
 * Always allows building/breaking since there's nothing to check against.
 */
internal object NoOpProtectionHook : ProtectionHook {

    override fun canBuild(player: Player, location: Location): Boolean = true

    override fun canBreak(player: Player, location: Location): Boolean = true

    override val isAvailable: Boolean = false
}

/**
 * Combines multiple protection hooks.
 *
 * Building/breaking is allowed only if ALL hooks allow it.
 * This ensures the most restrictive protection wins.
 */
internal class CompositeProtectionHook(
    private val hooks: List<ProtectionHook>
) : ProtectionHook {

    override fun canBuild(player: Player, location: Location): Boolean =
        hooks.all { it.canBuild(player, location) }

    override fun canBreak(player: Player, location: Location): Boolean =
        hooks.all { it.canBreak(player, location) }

    override val isAvailable: Boolean = true
}

