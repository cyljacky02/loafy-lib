package me.cyljacky02.loafylib.animation.command

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import me.cyljacky02.loafylib.animation.AnimationService
import me.cyljacky02.loafylib.animation.actions.*
import me.cyljacky02.loafylib.animation.core.AnimationResult
import me.cyljacky02.loafylib.animation.core.AnimationSequence
import me.cyljacky02.loafylib.animation.dsl.animation
import me.cyljacky02.loafylib.util.mini
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.potion.PotionEffectType
import revxrsal.commands.annotation.Command
import revxrsal.commands.annotation.Default
import revxrsal.commands.annotation.Description
import revxrsal.commands.annotation.Optional
import revxrsal.commands.annotation.Subcommand
import revxrsal.commands.bukkit.annotation.CommandPermission

/**
 * Debug commands for testing the animation framework.
 *
 * These commands allow server admins to:
 * - Test built-in animations
 * - Cancel running animations
 * - Emergency stop all animations and clear effects
 * - View animation status
 *
 * ## Usage
 * Register these commands in your plugin:
 * ```kotlin
 * lamp.register(AnimationDebugCommands(animationService, pluginScope))
 * ```
 *
 * ## Safety Features
 * - `/anim stop` - Cancel animation for a player
 * - `/anim emergency` - Force stop ALL animations and clear ALL effects
 * - `/anim clear` - Clear lingering effects without stopping animation
 */
@Command("anim", "animation", "animdebug")
@CommandPermission("loafylib.animation.debug")
class AnimationDebugCommands(
    private val animationService: AnimationService,
    private val scope: CoroutineScope
) {

    // ========== Status Commands ==========

    @Subcommand("status")
    @Description("Check if a player has an active animation")
    fun status(sender: Player, @Optional target: Player?) {
        val player = target ?: sender
        val isPlaying = animationService.isPlaying(player)

        if (isPlaying) {
            sender.sendMessage("<yellow>⚡ ${player.name} has an active animation".mini())
        } else {
            sender.sendMessage("<gray>○ ${player.name} has no active animation".mini())
        }
    }

    @Subcommand("list")
    @Description("List all registered animations")
    fun list(sender: Player) {
        val animations = animationService.registry.getAllIds()

        if (animations.isEmpty()) {
            sender.sendMessage("<gray>No animations registered".mini())
            return
        }

        sender.sendMessage("<gold>Registered Animations (${animations.size}):".mini())
        animations.forEach { id ->
            sender.sendMessage("<gray> - <white>$id".mini())
        }
    }

    // ========== Control Commands ==========

    @Subcommand("stop")
    @Description("Stop animation for a player")
    fun stop(sender: Player, @Optional target: Player?) {
        val player = target ?: sender

        if (animationService.cancel(player)) {
            sender.sendMessage("<green>✓ Cancelled animation for ${player.name}".mini())
        } else {
            sender.sendMessage("<gray>No active animation for ${player.name}".mini())
        }
    }

    @Subcommand("clear")
    @Description("Clear lingering animation effects (freeze, etc)")
    fun clear(sender: Player, @Optional target: Player?) {
        val player = target ?: sender
        clearAllEffects(player)
        sender.sendMessage("<green>✓ Cleared effects for ${player.name}".mini())
    }

    @Subcommand("emergency")
    @Description("EMERGENCY: Stop ALL animations and clear ALL effects for ALL players")
    fun emergency(sender: Player) {
        sender.sendMessage("<red>⚠ EMERGENCY STOP - Clearing all animations...".mini())

        // Cancel all animations
        animationService.player.cancelAll()

        // Clear effects for all online players
        sender.server.onlinePlayers.forEach { player ->
            clearAllEffects(player)
        }

        sender.sendMessage("<green>✓ All animations stopped and effects cleared".mini())
    }

    // ========== Test Commands ==========

    @Subcommand("test freeze")
    @Description("Test freeze animation")
    fun testFreeze(sender: Player, @Default("40") ticks: Int) {
        val safeTicks = ticks.coerceIn(1, 200) // Max 10 seconds for safety
        val anim = animation("test-freeze") {
            description = "Test freeze effect"
            freeze(safeTicks)
        }
        playTestAnimation(sender, anim)
    }

    @Subcommand("test particles")
    @Description("Test particle animation")
    fun testParticles(sender: Player) {
        val anim = animation("test-particles") {
            description = "Test particle burst"
            particles(Particle.CLOUD, count = 30, spread = 0.5, duration = 20)
        }
        playTestAnimation(sender, anim)
    }

    @Subcommand("test title")
    @Description("Test title animation")
    fun testTitle(sender: Player) {
        val anim = animation("test-title") {
            description = "Test title display"
            title("<rainbow>Test Title</rainbow>", "<gray>Subtitle here", fadeIn = 5, stay = 40, fadeOut = 10)
        }
        playTestAnimation(sender, anim)
    }

    @Subcommand("test sound")
    @Description("Test sound animation")
    fun testSound(sender: Player) {
        val anim = animation("test-sound") {
            description = "Test sound effect"
            sound(Sound.ENTITY_ENDER_DRAGON_FLAP, volume = 0.5f, pitch = 1.2f)
        }
        playTestAnimation(sender, anim)
    }

    @Subcommand("test shake")
    @Description("Test camera shake (requires PacketEvents)")
    fun testShake(sender: Player, @Default("0.5") intensity: Float) {
        val safeIntensity = intensity.coerceIn(0.1f, 2.0f)
        val anim = animation("test-shake") {
            description = "Test camera shake"
            cameraShake(safeIntensity, duration = 20)
        }
        playTestAnimation(sender, anim)
    }

    @Subcommand("test combo")
    @Description("Test combined animation (effects only)")
    fun testCombo(sender: Player) {
        val anim = animation("test-combo") {
            description = "Combined effects animation"
            freeze(10)
            sound(Sound.ENTITY_ENDER_DRAGON_FLAP, volume = 0.5f)
            particles(Particle.CLOUD, count = 20, spread = 0.3, duration = 15)
            title("<gold>Whoosh!", fadeIn = 0, stay = 20, fadeOut = 10)
        }
        playTestAnimation(sender, anim)
    }

    @Subcommand("play")
    @Description("Play a registered animation by ID")
    fun playRegistered(sender: Player, animationId: String, @Optional target: Player?) {
        val player = target ?: sender

        scope.launch {
            val result = animationService.play(player, animationId)
            handleResult(sender, player, animationId, result)
        }
    }

    // ========== Info Commands ==========

    @Subcommand("provider")
    @Description("Show which animation provider is active")
    fun providerInfo(sender: Player) {
        val provider = animationService.provider
        val providerName = provider::class.simpleName ?: "Unknown"
        val available = provider.isAvailable()

        sender.sendMessage("<gold>Animation Provider Info:".mini())
        sender.sendMessage("<gray>  Type: <white>$providerName".mini())
        sender.sendMessage("<gray>  Available: ${if (available) "<green>Yes" else "<red>No"}".mini())

        if (providerName.contains("Packet")) {
            sender.sendMessage("<gray>  Features: <green>Camera shake, packet batching".mini())
        } else {
            sender.sendMessage("<gray>  Features: <yellow>Basic (no camera shake)".mini())
        }
    }

    // ========== Helper Methods ==========

    private fun playTestAnimation(sender: Player, animation: AnimationSequence) {
        sender.sendMessage("<yellow>▶ Playing test animation: ${animation.id}".mini())

        scope.launch {
            val result = animationService.play(sender, animation)
            handleResult(sender, sender, animation.id, result)
        }
    }

    private fun handleResult(sender: Player, target: Player, animId: String, result: AnimationResult) {
        val targetName = if (sender == target) "you" else target.name

        when (result) {
            is AnimationResult.Completed ->
                sender.sendMessage("<green>✓ Animation '$animId' completed for $targetName".mini())
            is AnimationResult.Cancelled ->
                sender.sendMessage("<yellow>⚠ Animation '$animId' was cancelled for $targetName".mini())
            is AnimationResult.AlreadyPlaying ->
                sender.sendMessage("<red>✗ $targetName already has an animation playing".mini())
            is AnimationResult.PlayerDisconnected ->
                sender.sendMessage("<red>✗ $targetName disconnected during animation".mini())
            is AnimationResult.Error ->
                sender.sendMessage("<red>✗ Animation error: ${result.message}".mini())
        }
    }

    /**
     * Clears ALL possible animation effects from a player.
     * This is the nuclear option for stuck effects.
     */
    private fun clearAllEffects(player: Player) {
        // Clear via provider
        animationService.provider.clearEffects(player)

        // Also directly remove potion effects that might be stuck
        player.removePotionEffect(PotionEffectType.SLOWNESS)
        player.removePotionEffect(PotionEffectType.JUMP_BOOST)

        // Reset movement speeds to defaults
        player.walkSpeed = 0.2f
        player.flySpeed = 0.1f

        // Clear any titles
        player.clearTitle()
    }
}