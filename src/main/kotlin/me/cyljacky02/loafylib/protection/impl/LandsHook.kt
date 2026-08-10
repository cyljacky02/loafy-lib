package me.cyljacky02.loafylib.protection.impl

import me.angeschossen.lands.api.LandsIntegration
import me.angeschossen.lands.api.flags.type.Flags
import me.cyljacky02.loafylib.protection.ProtectionHook
import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin

/**
 * Lands integration for protection checking.
 *
 * Uses LandsAPI to check the BLOCK_PLACE and BLOCK_BREAK role flags.
 * This determines if a player can place or break blocks at a specific location
 * based on their role in the land/area.
 *
 * ## Thread Safety
 *
 * Must be called from entity/region thread (Folia) or main thread (Paper).
 * The `getArea(Location)` method is designed for loaded chunks and should
 * be called from the tick thread for consistency with WorldGuard integration.
 *
 * ## Wilderness Handling
 *
 * If the location is not within any claimed area (wilderness),
 * building is allowed by default. This matches Lands' default behavior.
 *
 * ## API Usage
 *
 * Uses the modern LandsAPI:
 * - `LandsIntegration.of(plugin)` to get integration instance
 * - `LandsIntegration.getArea(location)` to get area at location
 * - `Area.hasRoleFlag(uuid, flag)` to check permission (UUID-based for programmatic checks)
 *
 * @param plugin The plugin instance for LandsIntegration
 * @see ProtectionHook
 * @see me.cyljacky02.loafylib.protection.ProtectionHookFactory
 */
internal class LandsHook(plugin: Plugin) : ProtectionHook {

    private val landsIntegration: LandsIntegration = LandsIntegration.of(plugin)

    override fun canBuild(player: Player, location: Location): Boolean {
        // Get the area at this location (null if wilderness)
        val area = landsIntegration.getArea(location)
            ?: return true // Wilderness - allow building

        // Check if player has BLOCK_PLACE permission in this area
        // Using UUID-based method for programmatic checks (no message sent)
        return area.hasRoleFlag(player.uniqueId, Flags.BLOCK_PLACE)
    }

    override fun canBreak(player: Player, location: Location): Boolean {
        // Get the area at this location (null if wilderness)
        val area = landsIntegration.getArea(location)
            ?: return true // Wilderness - allow breaking

        // Check if player has BLOCK_BREAK permission in this area
        // Using UUID-based method for programmatic checks (no message sent)
        return area.hasRoleFlag(player.uniqueId, Flags.BLOCK_BREAK)
    }

    override val isAvailable: Boolean = true
}

