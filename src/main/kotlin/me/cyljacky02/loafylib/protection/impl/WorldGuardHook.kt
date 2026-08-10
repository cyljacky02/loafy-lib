package me.cyljacky02.loafylib.protection.impl

import com.sk89q.worldedit.bukkit.BukkitAdapter
import com.sk89q.worldguard.WorldGuard
import com.sk89q.worldguard.bukkit.WorldGuardPlugin
import com.sk89q.worldguard.protection.flags.Flags
import me.cyljacky02.loafylib.protection.ProtectionHook
import org.bukkit.Location
import org.bukkit.entity.Player

/**
 * WorldGuard integration for protection checking.
 *
 * Uses WorldGuard's RegionQuery API to check BLOCK_PLACE and BLOCK_BREAK flags.
 * This determines if a player can place or break blocks at a specific location
 * based on WorldGuard region permissions.
 *
 * ## Thread Safety
 *
 * **MUST be called from main/region thread.**
 *
 * WorldGuard's SessionManager requires main thread access for player
 * session lookups. Calling from async threads may cause undefined behavior
 * or exceptions.
 *
 * ## API Usage
 *
 * Uses the modern WorldGuard 7.x API:
 * - `WorldGuard.getInstance().getPlatform().getRegionContainer().createQuery()`
 * - `RegionQuery.testBuild(location, player, flags...)`
 *
 * @see ProtectionHook
 * @see me.cyljacky02.loafylib.protection.ProtectionHookFactory
 */
internal class WorldGuardHook : ProtectionHook {

    override fun canBuild(player: Player, location: Location): Boolean {
        val localPlayer = WorldGuardPlugin.inst().wrapPlayer(player)
        val query = WorldGuard.getInstance()
            .platform
            .regionContainer
            .createQuery()

        return query.testBuild(
            BukkitAdapter.adapt(location),
            localPlayer,
            Flags.BLOCK_PLACE
        )
    }

    override fun canBreak(player: Player, location: Location): Boolean {
        val localPlayer = WorldGuardPlugin.inst().wrapPlayer(player)
        val query = WorldGuard.getInstance()
            .platform
            .regionContainer
            .createQuery()

        return query.testBuild(
            BukkitAdapter.adapt(location),
            localPlayer,
            Flags.BLOCK_BREAK
        )
    }

    override val isAvailable: Boolean = true
}

