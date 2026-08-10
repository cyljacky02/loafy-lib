package me.cyljacky02.loafylib.protection.impl

import me.ryanhamshire.GriefPrevention.ClaimPermission
import com.griefprevention.protection.ProtectionHelper
import me.cyljacky02.loafylib.protection.ProtectionHook
import org.bukkit.Location
import org.bukkit.entity.Player

/**
 * GriefPrevention integration for protection checking.
 *
 * Uses GriefPrevention's `ProtectionHelper.checkPermission()` to check
 * if a player can build or break blocks at a specific location.
 *
 * ## Thread Safety
 *
 * Must be called from entity/region thread (Folia) or main thread (Paper).
 * GriefPrevention interacts with Bukkit's event system and DataStore
 * which are not thread-safe.
 *
 * ## Wilderness Handling
 *
 * GriefPrevention handles wilderness internally based on world's ClaimsMode.
 * The `checkPermission` method returns the appropriate result based on
 * world configuration (e.g., Creative mode denies building outside claims).
 *
 * ## API Usage
 *
 * Uses the modern GriefPrevention API:
 * - `ProtectionHelper.checkPermission(player, location, ClaimPermission.Build, event)`
 * - Returns null if allowed, or a Supplier<String> with denial reason if denied
 *
 * Note: We pass null for the event parameter since we're doing a programmatic
 * check outside of an event context. This is safe as the event parameter is
 * only used for specific event-based logic (like PreventBlockBreakEvent).
 *
 * @see ProtectionHook
 * @see me.cyljacky02.loafylib.protection.ProtectionHookFactory
 */
internal class GriefPreventionHook : ProtectionHook {

    override fun canBuild(player: Player, location: Location): Boolean {
        // checkPermission returns null if allowed, or a Supplier<String> if denied
        // Passing null for event since we're doing a programmatic check
        val denyReason = ProtectionHelper.checkPermission(player, location, ClaimPermission.Build, null)
        return denyReason == null
    }

    override fun canBreak(player: Player, location: Location): Boolean {
        // GriefPrevention uses ClaimPermission.Build for both build and break
        val denyReason = ProtectionHelper.checkPermission(player, location, ClaimPermission.Build, null)
        return denyReason == null
    }

    override val isAvailable: Boolean = true
}
