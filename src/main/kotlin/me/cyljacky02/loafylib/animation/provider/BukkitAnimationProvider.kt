package me.cyljacky02.loafylib.animation.provider

import me.cyljacky02.loafylib.animation.core.AnimationProvider
import net.kyori.adventure.text.Component
import net.kyori.adventure.title.Title
import org.bukkit.Location
import org.bukkit.Particle
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.util.Vector
import java.time.Duration

/**
 * Bukkit API implementation of AnimationProvider.
 *
 * Uses standard Bukkit/Paper API for all effects.
 * Works on all Paper/Folia servers without additional dependencies.
 *
 * Limitations:
 * - Camera shake is not supported (no-op)
 * - Freeze uses slowness potion effect (visible to player)
 */
@Suppress("unused") // Plugin parameter kept for API consistency with PacketAnimationProvider
class BukkitAnimationProvider(
    @Suppress("UNUSED_PARAMETER") // Reserved for future use (e.g., scheduled tasks)
    private val plugin: Plugin
) : AnimationProvider {

    override fun isAvailable(): Boolean = true

    override fun setVelocity(player: Player, velocity: Vector) {
        player.velocity = velocity
    }

    override fun freezePlayer(player: Player, frozen: Boolean) {
        if (frozen) {
            // Apply slowness and jump boost negative to prevent movement
            player.addPotionEffect(
                PotionEffect(
                    PotionEffectType.SLOWNESS,
                    Int.MAX_VALUE,
                    255,
                    false,
                    false,
                    false
                )
            )
            player.addPotionEffect(
                PotionEffect(
                    PotionEffectType.JUMP_BOOST,
                    Int.MAX_VALUE,
                    128, // Negative effect
                    false,
                    false,
                    false
                )
            )
        } else {
            player.removePotionEffect(PotionEffectType.SLOWNESS)
            player.removePotionEffect(PotionEffectType.JUMP_BOOST)
        }
    }

    override fun spawnParticles(
        player: Player,
        particle: Particle,
        location: Location,
        count: Int,
        spread: Double,
        speed: Double
    ) {
        player.spawnParticle(
            particle,
            location,
            count,
            spread,
            spread,
            spread,
            speed
        )
    }

    override fun showTitle(
        player: Player,
        title: Component,
        subtitle: Component,
        fadeInTicks: Int,
        stayTicks: Int,
        fadeOutTicks: Int
    ) {
        val times = Title.Times.times(
            Duration.ofMillis(fadeInTicks * 50L),
            Duration.ofMillis(stayTicks * 50L),
            Duration.ofMillis(fadeOutTicks * 50L)
        )
        player.showTitle(Title.title(title, subtitle, times))
    }

    override fun shakeCamera(player: Player, intensity: Float) {
        // Camera shake not supported in Bukkit API
        // PacketEvents provider implements this
    }

    override fun clearEffects(player: Player) {
        // Remove all animation effects
        player.removePotionEffect(PotionEffectType.SLOWNESS)
        player.removePotionEffect(PotionEffectType.JUMP_BOOST)
    }
}

