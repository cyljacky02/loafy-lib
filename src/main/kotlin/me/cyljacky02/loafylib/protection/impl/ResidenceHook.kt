package me.cyljacky02.loafylib.protection.impl

import com.bekvon.bukkit.residence.listeners.ResidenceBlockListener
import me.cyljacky02.loafylib.protection.ProtectionHook
import org.bukkit.Location
import org.bukkit.entity.Player

/**
 * Residence integration for protection checking.
 *
 * Uses Residence's `ResidenceBlockListener` static methods to check
 * if a player can place or break blocks at a specific location.
 *
 * ## Thread Safety
 *
 * Must be called from entity/region thread (Folia) or main thread (Paper).
 * Residence interacts with Bukkit API and internal managers that are
 * not thread-safe.
 *
 * ## Wilderness Handling
 *
 * Residence handles wilderness (unclaimed areas) internally via world flags.
 * The `canBreakBlock` and `canPlaceBlock` methods return the appropriate
 * result based on world configuration.
 *
 * ## API Usage
 *
 * Uses Residence's static methods:
 * - `ResidenceBlockListener.canBreakBlock(player, location, checkFlags)`
 * - `ResidenceBlockListener.canPlaceBlock(player, block, checkFlags)`
 *
 * @see ProtectionHook
 * @see me.cyljacky02.loafylib.protection.ProtectionHookFactory
 */
internal class ResidenceHook : ProtectionHook {

    override fun canBuild(player: Player, location: Location): Boolean {
        // Use location.block since canPlaceBlock expects a Block
        // The third parameter 'true' enables flag checking
        return ResidenceBlockListener.canPlaceBlock(player, location.block, true)
    }

    override fun canBreak(player: Player, location: Location): Boolean {
        // The third parameter 'true' enables flag checking
        return ResidenceBlockListener.canBreakBlock(player, location, true)
    }

    override val isAvailable: Boolean = true
}
