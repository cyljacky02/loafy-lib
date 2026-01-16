package me.cyljacky02.loafylib.animation.core

import net.kyori.adventure.util.TriState
import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.util.Vector
import java.util.UUID

/**
 * Captures comprehensive player state before animation for safe restoration afterward.
 *
 * Following Typewriter's best practices, we capture and restore:
 * - Flight state (allowFlight, isFlying)
 * - Fall damage settings
 * - Location (for camera animations)
 * - Player visibility (multiplayer safety)
 * - Invisibility potion effect
 * - Velocity (optional)
 *
 * This ensures players return to their original state after animations,
 * even if the animation is cancelled or errors occur.
 *
 * @property allowFlight Whether the player was allowed to fly
 * @property isFlying Whether the player was flying
 * @property flyingFallDamage The player's flying fall damage state
 * @property location The player's location (null if not captured)
 * @property velocity The player's velocity (null if not captured)
 * @property hadInvisibility Whether the player had invisibility effect
 * @property invisibilityEffect The original invisibility effect (null if none)
 * @property hiddenFromPlayers UUIDs of players this player was hidden from
 * @property hidingPlayers UUIDs of players hidden from this player
 */
data class PlayerStateSnapshot(
    // Flight state
    val allowFlight: Boolean,
    val isFlying: Boolean,
    val flyingFallDamage: TriState,

    // Location state (for camera animations)
    val location: Location? = null,

    // Velocity state (optional)
    val velocity: Vector? = null,

    // Invisibility state
    val hadInvisibility: Boolean = false,
    val invisibilityEffect: PotionEffect? = null,

    // Player visibility state (for multiplayer safety)
    val hiddenFromPlayers: Set<UUID>? = null,
    val hidingPlayers: Set<UUID>? = null
) {
    companion object {
        /**
         * Capture minimal player state (flight only).
         * Use for simple animations that don't need full state capture.
         *
         * @param player The player to capture state from
         * @return A snapshot with flight state only
         */
        fun capture(player: Player): PlayerStateSnapshot {
            return PlayerStateSnapshot(
                allowFlight = player.allowFlight,
                isFlying = player.isFlying,
                flyingFallDamage = player.hasFlyingFallDamage()
            )
        }

        /**
         * Capture comprehensive player state for camera animations.
         * Includes location, visibility, and invisibility state.
         *
         * Based on Typewriter's approach for cinematic camera control.
         *
         * @param player The player to capture state from
         * @param captureLocation Whether to capture location
         * @param captureVelocity Whether to capture velocity
         * @param captureVisibility Whether to capture player visibility state
         * @return A comprehensive snapshot of the player's state
         */
        fun captureForCamera(
            player: Player,
            captureLocation: Boolean = true,
            captureVelocity: Boolean = false,
            captureVisibility: Boolean = true
        ): PlayerStateSnapshot {
            val invisibilityEffect = player.getPotionEffect(PotionEffectType.INVISIBILITY)

            return PlayerStateSnapshot(
                allowFlight = player.allowFlight,
                isFlying = player.isFlying,
                flyingFallDamage = player.hasFlyingFallDamage(),
                location = if (captureLocation) player.location.clone() else null,
                velocity = if (captureVelocity) player.velocity.clone() else null,
                hadInvisibility = invisibilityEffect != null,
                invisibilityEffect = invisibilityEffect,
                hiddenFromPlayers = if (captureVisibility) {
                    player.server.onlinePlayers
                        .filter { it != player && !it.canSee(player) }
                        .map { it.uniqueId }
                        .toSet()
                } else null,
                hidingPlayers = if (captureVisibility) {
                    player.server.onlinePlayers
                        .filter { it != player && !player.canSee(it) }
                        .map { it.uniqueId }
                        .toSet()
                } else null
            )
        }
    }

    /**
     * Restore the captured state to the player.
     *
     * @param player The player to restore state to
     * @param plugin The plugin instance (needed for visibility restoration)
     * @param restoreLocation Whether to restore location
     * @param restoreVelocity Whether to restore velocity
     * @param restoreVisibility Whether to restore player visibility
     */
    fun restore(
        player: Player,
        plugin: Plugin? = null,
        restoreLocation: Boolean = false,
        restoreVelocity: Boolean = false,
        restoreVisibility: Boolean = true
    ) {
        if (!player.isOnline) return

        // Restore flight state in correct order
        player.allowFlight = allowFlight
        player.isFlying = isFlying
        player.setFlyingFallDamage(flyingFallDamage)

        // Restore location if captured and requested
        if (restoreLocation && location != null) {
            player.teleport(location)
        }

        // Restore velocity if captured and requested
        if (restoreVelocity && velocity != null) {
            player.velocity = velocity
        }

        // Restore invisibility state
        restoreInvisibility(player)

        // Restore player visibility if plugin provided
        if (restoreVisibility && plugin != null) {
            restorePlayerVisibility(player, plugin)
        }
    }

    /**
     * Restore invisibility potion effect to original state.
     */
    private fun restoreInvisibility(player: Player) {
        if (!player.isOnline) return

        // Remove current invisibility if we added it
        if (!hadInvisibility) {
            player.removePotionEffect(PotionEffectType.INVISIBILITY)
        } else if (invisibilityEffect != null) {
            // Restore original effect
            player.removePotionEffect(PotionEffectType.INVISIBILITY)
            player.addPotionEffect(invisibilityEffect)
        }
    }

    /**
     * Restore player visibility to original state.
     */
    private fun restorePlayerVisibility(player: Player, plugin: Plugin) {
        if (!player.isOnline) return

        // Restore visibility of other players to this player
        hidingPlayers?.let { hiding ->
            player.server.onlinePlayers
                .filter { it != player && it.uniqueId !in hiding }
                .forEach { player.showPlayer(plugin, it) }
        }

        // Restore this player's visibility to other players
        hiddenFromPlayers?.let { hidden ->
            player.server.onlinePlayers
                .filter { it != player && it.uniqueId !in hidden }
                .forEach { it.showPlayer(plugin, player) }
        }
    }

    /**
     * Apply safe animation state to player.
     *
     * Enables flight and disables fall damage to prevent issues during animation.
     * Based on Typewriter's approach and Paper's best practices.
     *
     * @param player The player to apply safe state to
     */
    fun applySafeAnimationState(player: Player) {
        if (!player.isOnline) return

        // Enable flight to prevent fall damage checks
        player.allowFlight = true
        // Explicitly disable fall damage during animation
        player.setFlyingFallDamage(TriState.FALSE)
    }

    /**
     * Apply camera animation state to player.
     *
     * This is a more comprehensive setup for camera-based animations:
     * - Enables flight
     * - Disables fall damage
     * - Makes player invisible
     * - Hides player from all other players
     * - Hides all other players from this player
     *
     * Based on Typewriter's CameraCinematicAction approach.
     *
     * @param player The player to apply camera state to
     * @param plugin The plugin instance (needed for visibility)
     */
    fun applyCameraAnimationState(player: Player, plugin: Plugin) {
        if (!player.isOnline) return

        // Enable flight
        player.allowFlight = true
        player.isFlying = true
        player.setFlyingFallDamage(TriState.FALSE)

        // Make player invisible (if not already)
        if (!hadInvisibility) {
            player.addPotionEffect(
                PotionEffect(
                    PotionEffectType.INVISIBILITY,
                    Int.MAX_VALUE,
                    0,
                    false,  // ambient
                    false,  // particles
                    false   // icon
                )
            )
        }

        // Hide player from all other players and vice versa
        player.server.onlinePlayers
            .filter { it != player }
            .forEach {
                it.hidePlayer(plugin, player)
                player.hidePlayer(plugin, it)
            }
    }
}

