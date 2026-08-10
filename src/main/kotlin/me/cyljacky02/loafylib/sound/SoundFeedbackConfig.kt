package me.cyljacky02.loafylib.sound

import org.spongepowered.configurate.objectmapping.ConfigSerializable
import org.spongepowered.configurate.objectmapping.meta.Comment

/**
 * Configuration for a sound feedback played to a player.
 *
 * Uses Adventure sound keys so it can play vanilla sounds and resource-pack sounds.
 * This class is shared across Loafy plugins for consistent sound configuration.
 *
 * ## Usage Example
 * ```kotlin
 * @ConfigSerializable
 * data class MyFeatureConfig(
 *     val denySound: SoundFeedbackConfig = SoundFeedbackConfig()
 * )
 *
 * // In your feature:
 * SoundFeedback.play(player, config.denySound)
 * ```
 *
 * @property enabled Whether to play this sound
 * @property key Sound key (Adventure Key). Examples: `entity.villager.no`, `minecraft:entity.villager.no`, `my_pack:deny`
 * @property source Sound source/category for volume control
 * @property volume Volume (1.0 = normal). Note: higher volume increases hearing distance
 * @property pitch Pitch (0.0-2.0, 1.0 = normal)
 */
@ConfigSerializable
data class SoundFeedbackConfig(
    @Comment("Whether to play this sound")
    val enabled: Boolean = true,

    @Comment("Sound key (Adventure Key). Examples: entity.villager.no, minecraft:entity.villager.no, my_pack:deny")
    val key: String = "entity.villager.no",

    @Comment("Sound source/category. Valid values: MASTER, MUSIC, RECORD, WEATHER, BLOCK, HOSTILE, NEUTRAL, PLAYER, AMBIENT, VOICE")
    val source: String = "PLAYER",

    @Comment("Volume (1.0 = normal). Note: higher volume increases hearing distance")
    val volume: Float = 1.0f,

    @Comment("Pitch (0.0-2.0, 1.0 = normal)")
    val pitch: Float = 1.0f
)
