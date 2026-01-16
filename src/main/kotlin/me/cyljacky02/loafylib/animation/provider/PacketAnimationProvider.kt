package me.cyljacky02.loafylib.animation.provider

import com.github.retrooper.packetevents.PacketEvents
import com.github.retrooper.packetevents.protocol.potion.PotionTypes
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityEffect
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityVelocity
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerRemoveEntityEffect
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetTitleSubtitle
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetTitleText
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetTitleTimes
import me.cyljacky02.loafylib.animation.core.AnimationProvider
import net.kyori.adventure.text.Component
import org.bukkit.Location
import org.bukkit.Particle
import org.bukkit.entity.Player
import org.bukkit.util.Vector
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * PacketEvents implementation of AnimationProvider.
 *
 * Uses a hybrid approach:
 * - Server-side Bukkit API for velocity (proper synchronization, no "moved too quickly" warnings)
 * - PacketEvents for visual effects (freeze, titles, camera shake)
 *
 * This follows Paper best practices:
 * - player.setVelocity() properly sets entity.hurtMarked and syncs with clients
 * - Packet-based effects are used only for visual/client-side only operations
 */
class PacketAnimationProvider : AnimationProvider {

    private val packetManager by lazy { PacketEvents.getAPI().playerManager }

    override fun isAvailable(): Boolean {
        return try {
            PacketEvents.getAPI() != null
        } catch (e: Exception) {
            false
        }
    }

    override fun setVelocity(player: Player, velocity: Vector) {
        // Use Bukkit API for velocity - this properly synchronizes with the server
        // and prevents "moved too quickly" warnings. The server will:
        // 1. Set entity.deltaMovement
        // 2. Set entity.hurtMarked = true (signals velocity sync needed)
        // 3. Fire PlayerVelocityEvent
        // 4. Send ClientboundSetEntityMotionPacket to clients
        player.velocity = velocity
    }

    override fun freezePlayer(player: Player, frozen: Boolean) {
        val user = packetManager.getUser(player)
        
        if (frozen) {
            // Send slowness effect packet (client-side only)
            val slowness = WrapperPlayServerEntityEffect(
                player.entityId,
                PotionTypes.SLOWNESS,
                255, // Max amplifier
                Int.MAX_VALUE, // Duration
                0x06 // Flags: ambient + hide particles
            )
            val jumpBoost = WrapperPlayServerEntityEffect(
                player.entityId,
                PotionTypes.JUMP_BOOST,
                128, // Negative effect
                Int.MAX_VALUE,
                0x06
            )
            // Batch send packets
            user.writePacket(slowness)
            user.writePacket(jumpBoost)
            user.flushPackets()
        } else {
            // Remove effects
            val removeSlowness = WrapperPlayServerRemoveEntityEffect(
                player.entityId,
                PotionTypes.SLOWNESS
            )
            val removeJump = WrapperPlayServerRemoveEntityEffect(
                player.entityId,
                PotionTypes.JUMP_BOOST
            )
            user.writePacket(removeSlowness)
            user.writePacket(removeJump)
            user.flushPackets()
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
        // Use Bukkit API for particles - it's efficient enough
        // and handles particle data types correctly
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
        val user = packetManager.getUser(player)
        
        // Batch all title packets together
        val timesPacket = WrapperPlayServerSetTitleTimes(fadeInTicks, stayTicks, fadeOutTicks)
        val titlePacket = WrapperPlayServerSetTitleText(title)
        val subtitlePacket = WrapperPlayServerSetTitleSubtitle(subtitle)
        
        user.writePacket(timesPacket)
        user.writePacket(titlePacket)
        user.writePacket(subtitlePacket)
        user.flushPackets()
    }

    override fun shakeCamera(player: Player, intensity: Float) {
        // Camera shake by sending small position offsets
        // This creates a visual shake effect without actually moving the player
        val user = packetManager.getUser(player)

        // Generate random shake offset
        val angle = Random.nextDouble() * 2 * Math.PI
        val offsetX = cos(angle) * intensity * 0.1
        val offsetZ = sin(angle) * intensity * 0.1

        // Send a relative move packet with small offset
        // The client will interpolate back, creating shake effect
        val velocity = WrapperPlayServerEntityVelocity(
            player.entityId,
            com.github.retrooper.packetevents.util.Vector3d(
                offsetX,
                0.0,
                offsetZ
            )
        )
        user.sendPacket(velocity)
    }

    override fun clearEffects(player: Player) {
        // Delegate to freezePlayer to avoid code duplication
        freezePlayer(player, false)
    }
}

