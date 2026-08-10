package me.cyljacky02.loafylib.sound

import net.kyori.adventure.key.Key
import net.kyori.adventure.sound.Sound
import org.bukkit.entity.Player
import java.util.Locale

/**
 * Utility for playing sound feedback to players from [SoundFeedbackConfig].
 *
 * Handles parsing of Adventure [Key] and [Sound.Source] with safe fallbacks
 * for invalid configuration values.
 *
 * ## Usage
 * ```kotlin
 * // Play a sound from config
 * SoundFeedback.play(player, config.denySound)
 *
 * // Or use the extension function
 * player.playFeedback(config.denySound)
 * ```
 */
object SoundFeedback {

    /**
     * Plays a sound to a player based on the given configuration.
     *
     * Does nothing if:
     * - [config] is null
     * - [SoundFeedbackConfig.enabled] is false
     * - The sound key is invalid (logs nothing, fails silently)
     *
     * @param player The player to play the sound to
     * @param config The sound configuration, or null to skip
     */
    fun play(player: Player, config: SoundFeedbackConfig?) {
        if (config == null || !config.enabled) return

        val key = parseKey(config.key) ?: return
        val source = parseSource(config.source)

        player.playSound(Sound.sound(key, source, config.volume, config.pitch))
    }

    /**
     * Parses an Adventure [Key] from a string.
     *
     * Accepts formats:
     * - `entity.villager.no` (defaults to `minecraft:` namespace)
     * - `minecraft:entity.villager.no`
     * - `my_pack:custom_sound`
     *
     * @param raw The key string to parse
     * @return The parsed [Key], or null if invalid
     */
    fun parseKey(raw: String): Key? {
        return try {
            Key.key(raw)
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    /**
     * Parses a [Sound.Source] from a string.
     *
     * Accepts case-insensitive values with optional hyphens:
     * - `PLAYER`, `player`, `Player`
     * - `HOSTILE`, `hostile`
     * - `VOICE`, `voice`
     *
     * @param raw The source string to parse
     * @return The parsed [Sound.Source], or [Sound.Source.PLAYER] as fallback
     */
    fun parseSource(raw: String): Sound.Source {
        val normalized = raw.trim().uppercase(Locale.ROOT).replace('-', '_')
        return runCatching { Sound.Source.valueOf(normalized) }.getOrElse { Sound.Source.PLAYER }
    }
}

/**
 * Extension function to play sound feedback to this player.
 *
 * @param config The sound configuration, or null to skip
 * @see SoundFeedback.play
 */
fun Player.playFeedback(config: SoundFeedbackConfig?) {
    SoundFeedback.play(this, config)
}
