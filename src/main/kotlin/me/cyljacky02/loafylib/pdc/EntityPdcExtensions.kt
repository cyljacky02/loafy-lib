package me.cyljacky02.loafylib.pdc

import org.bukkit.NamespacedKey
import org.bukkit.entity.Entity
import org.bukkit.persistence.PersistentDataContainer
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.Plugin

// ============================================================================
// Entity PDC Extensions
// ============================================================================
// PDC access for entities with a lightweight marker system for identification.
// Same pattern as ItemStack but for Entity lifecycle.
//
// ## Thread Safety
// All functions in this file access entity PDC which requires:
// - Paper: Main server thread
// - Folia: The region thread that owns the entity
//
// Use Entity.withEntityContext(plugin) or entityDispatcher for safe access.
// ============================================================================

/** Marker value for entity identification - uses minimal storage (1 byte) */
private const val MARKER: Byte = 1

// ============================================================================
// Entity Marker System
// ============================================================================

/**
 * Marks this entity with a unique identifier key using a BYTE marker.
 * Uses the same efficient pattern as ItemStack.markAs().
 *
 * ```kotlin
 * val customMobKey = NamespacedKey(plugin, "custom_mob")
 * entity.markAs(customMobKey)
 *
 * // Later, check if an entity has this marker:
 * if (entity.hasEntityKey(customMobKey)) { ... }
 * ```
 */
fun Entity.markAs(plugin: Plugin, key: String): Entity =
    markAs(NamespacedKey(plugin, key))

/**
 * Marks this entity with a NamespacedKey identifier using a BYTE marker.
 */
fun Entity.markAs(key: NamespacedKey): Entity {
    persistentDataContainer.set(key, PersistentDataType.BYTE, MARKER)
    return this
}

/**
 * Removes an entity marker key from this entity.
 */
fun Entity.unmarkAs(plugin: Plugin, key: String): Entity =
    unmarkAs(NamespacedKey(plugin, key))

/**
 * Removes a NamespacedKey marker from this entity.
 */
fun Entity.unmarkAs(key: NamespacedKey): Entity {
    persistentDataContainer.remove(key)
    return this
}

/**
 * Checks if this entity has the specified marker key.
 *
 * ```kotlin
 * val customMobKey = NamespacedKey(plugin, "custom_mob")
 * if (entity.hasEntityKey(customMobKey)) {
 *     // Handle custom mob
 * }
 * ```
 */
fun Entity.hasEntityKey(key: NamespacedKey): Boolean =
    persistentDataContainer.has(key, PersistentDataType.BYTE)

/**
 * Checks if this entity matches any of the provided keys.
 * Returns the first matching key, or null if none match.
 *
 * ```kotlin
 * val bossKey = NamespacedKey(plugin, "boss")
 * val minionKey = NamespacedKey(plugin, "minion")
 *
 * when (entity.matchesAnyKey(bossKey, minionKey)) {
 *     bossKey -> handleBoss()
 *     minionKey -> handleMinion()
 *     null -> { /* not a custom entity */ }
 * }
 * ```
 */
fun Entity.matchesAnyKey(vararg keys: NamespacedKey): NamespacedKey? =
    keys.firstOrNull { hasEntityKey(it) }

// ============================================================================
// Entity PDC DSL
// ============================================================================

/**
 * Edits the PersistentDataContainer of this Entity using a DSL block.
 * Returns the Entity for chaining.
 *
 * ```kotlin
 * entity.pdc {
 *     setDouble(healthKey, 100.0)
 *     setInt(levelKey, 5)
 * }
 * ```
 */
inline fun Entity.pdc(block: PersistentDataContainer.() -> Unit): Entity {
    persistentDataContainer.block()
    return this
}
