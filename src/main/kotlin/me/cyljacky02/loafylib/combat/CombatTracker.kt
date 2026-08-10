package me.cyljacky02.loafylib.combat

import me.cyljacky02.loafylib.plugin.PluginComponent
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.plugin.java.JavaPlugin
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Reusable combat detection and tagging service.
 *
 * Listens to [EntityDamageByEntityEvent] and tags players involved in combat
 * based on configurable scope (PvP, PvE, or all). Uses Paper's
 * [DamageSource.getCausingEntity()][org.bukkit.damage.DamageSource.getCausingEntity]
 * to resolve the full damage chain (projectiles → shooter, pets → owner,
 * TNT → igniter, lingering potions → thrower).
 *
 * ## Thread Safety
 *
 * - Tag storage uses [ConcurrentHashMap] — safe from any thread.
 * - The damage listener runs on the victim's entity thread
 *   (Paper dispatches [EntityDamageByEntityEvent] as an [org.bukkit.event.entity.EntityEvent]).
 * - When [CombatTrackerConfig.tagBothParties] is true, the attacker is tagged
 *   via a thread-safe [ConcurrentHashMap.put] (no entity state mutation needed).
 * - Callers reacting to combat (e.g., ending flight) must dispatch entity state
 *   changes to the correct entity thread themselves.
 *
 * ## Usage
 *
 * ```kotlin
 * // In your plugin's components():
 * val combatTracker = CombatTracker(
 *     plugin = this,
 *     config = CombatTrackerConfig(
 *         tagDurationMs = 10_000L,
 *         scope = CombatScope.PVP_ONLY,
 *         tagBothParties = true
 *     )
 * )
 *
 * // Query:
 * if (combatTracker.isTagged(player.uniqueId)) { /* combat-tagged */ }
 * val remaining = combatTracker.getTimeRemainingMs(player.uniqueId)
 * ```
 *
 * @see CombatTrackerConfig
 * @see CombatTag
 */
class CombatTracker(
    private val plugin: JavaPlugin,
    @Volatile var config: CombatTrackerConfig = CombatTrackerConfig()
) : PluginComponent, Listener {

    private val tags = ConcurrentHashMap<UUID, CombatTag>()

    override suspend fun initialize() {}

    override suspend fun shutdown() {
        tags.clear()
    }

    // ── Query API ──────────────────────────────────────────────────────

    /**
     * Returns true if the player is currently combat-tagged.
     * Lazily evicts expired tags on access. O(1).
     */
    fun isTagged(uuid: UUID): Boolean {
        val tag = tags[uuid] ?: return false
        if (System.currentTimeMillis() >= tag.expiresAt) {
            tags.remove(uuid)
            return false
        }
        return true
    }

    /**
     * Returns the full [CombatTag] for a player, or null if not tagged.
     * Lazily evicts expired tags on access.
     */
    fun getTag(uuid: UUID): CombatTag? {
        val tag = tags[uuid] ?: return null
        if (System.currentTimeMillis() >= tag.expiresAt) {
            tags.remove(uuid)
            return null
        }
        return tag
    }

    /**
     * Returns the remaining combat tag duration in milliseconds, or 0 if not tagged.
     */
    fun getTimeRemainingMs(uuid: UUID): Long {
        val tag = tags[uuid] ?: return 0L
        val remaining = tag.expiresAt - System.currentTimeMillis()
        if (remaining <= 0L) {
            tags.remove(uuid)
            return 0L
        }
        return remaining
    }

    // ── Manual Control ─────────────────────────────────────────────────

    /**
     * Manually tags a player as in-combat. Useful for admin commands or
     * custom combat detection beyond EntityDamageByEntityEvent.
     */
    fun tag(uuid: UUID, durationMs: Long = config.tagDurationMs, source: CombatSource = CombatSource.OTHER) {
        tags[uuid] = CombatTag(
            expiresAt = System.currentTimeMillis() + durationMs,
            opponentUuid = null,
            source = source
        )
    }

    /**
     * Clears the combat tag for a player.
     */
    fun clearTag(uuid: UUID) {
        tags.remove(uuid)
    }

    // ── Damage Listener ────────────────────────────────────────────────

    /**
     * Listens for entity damage and tags players based on [config].
     *
     * Uses MONITOR priority + ignoreCancelled to only process damage
     * that actually went through (not cancelled by protection plugins, etc.).
     *
     * Paper's [org.bukkit.damage.DamageSource.getCausingEntity] resolves the
     * full damage chain natively:
     * - Arrow/Trident → shooting Player
     * - Tamed Wolf → owning Player
     * - TNT → igniting Player
     * - Lingering/Splash Potion → throwing Player
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onDamage(event: EntityDamageByEntityEvent) {
        val victim = event.entity as? Player ?: return
        val causingEntity = event.damageSource.causingEntity
        val directEntity = event.damageSource.directEntity

        // Determine combat source type
        val source = resolveCombatSource(directEntity)

        // Apply scope filter
        val isPlayerAttacker = causingEntity is Player && causingEntity != victim
        when (config.scope) {
            CombatScope.PVP_ONLY -> if (!isPlayerAttacker) return
            CombatScope.PVE_ONLY -> if (isPlayerAttacker) return
            CombatScope.ALL -> {} // accept everything
        }

        val now = System.currentTimeMillis()
        val expiresAt = now + config.tagDurationMs

        // Tag victim
        tags[victim.uniqueId] = CombatTag(
            expiresAt = expiresAt,
            opponentUuid = (causingEntity as? Player)?.uniqueId,
            source = source
        )

        // Tag attacker (bidirectional)
        if (config.tagBothParties && isPlayerAttacker) {
            val attacker = causingEntity
            tags[attacker.uniqueId] = CombatTag(
                expiresAt = expiresAt,
                opponentUuid = victim.uniqueId,
                source = source
            )
        }
    }

    private fun resolveCombatSource(directEntity: org.bukkit.entity.Entity?): CombatSource {
        return when (directEntity) {
            is Player -> CombatSource.MELEE
            is org.bukkit.entity.Projectile -> CombatSource.PROJECTILE
            is org.bukkit.entity.Tameable -> CombatSource.PET
            is org.bukkit.entity.TNTPrimed -> CombatSource.EXPLOSION
            is org.bukkit.entity.AreaEffectCloud -> CombatSource.POTION
            else -> CombatSource.OTHER
        }
    }
}
