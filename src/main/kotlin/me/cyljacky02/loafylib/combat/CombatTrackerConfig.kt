package me.cyljacky02.loafylib.combat

/**
 * Configuration for a [CombatTracker] instance.
 *
 * Each consumer plugin can create its own [CombatTracker] with different
 * settings. For example, a flight plugin might use PvP-only with 10s duration,
 * while a teleport plugin might use ALL scope with 15s duration.
 *
 * @property tagDurationMs How long a combat tag lasts in milliseconds.
 * @property scope Which types of combat trigger tagging.
 * @property tagBothParties If true, both victim AND attacker are tagged.
 *   Set to false if only the damaged player should be restricted.
 */
data class CombatTrackerConfig(
    val tagDurationMs: Long = 10_000L,
    val scope: CombatScope = CombatScope.PVP_ONLY,
    val tagBothParties: Boolean = true
)

/**
 * Defines which types of combat trigger tagging.
 */
enum class CombatScope {
    /** Only player-vs-player combat (melee, projectiles, pets, TNT between players). */
    PVP_ONLY,
    /** Only player-vs-mob combat (excludes player attackers). */
    PVE_ONLY,
    /** Any entity damage involving a player as victim. */
    ALL
}

/**
 * Represents an active combat tag on a player.
 *
 * @property expiresAt Timestamp (ms since epoch) when the tag expires.
 * @property opponentUuid UUID of the opponent that caused the tag, or null for PvE/environmental.
 * @property source The type of combat that triggered the tag.
 */
data class CombatTag(
    val expiresAt: Long,
    val opponentUuid: java.util.UUID?,
    val source: CombatSource
)

/**
 * The type of damage that caused a combat tag.
 *
 * Resolved from [org.bukkit.damage.DamageSource.getDirectEntity]:
 * - [MELEE] — direct player hit
 * - [PROJECTILE] — arrow, trident, firework, etc.
 * - [PET] — tamed wolf, cat, etc. (owner is the causing entity)
 * - [EXPLOSION] — TNT, end crystal, creeper
 * - [POTION] — splash/lingering potion area effect
 * - [OTHER] — anything else (e.g., custom damage)
 */
enum class CombatSource {
    MELEE,
    PROJECTILE,
    PET,
    EXPLOSION,
    POTION,
    OTHER
}
