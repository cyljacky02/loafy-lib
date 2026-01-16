package me.cyljacky02.loafylib.pdc

import org.bukkit.Chunk
import org.bukkit.NamespacedKey
import org.bukkit.persistence.PersistentDataContainer
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.Plugin

// ============================================================================
// Chunk PDC Extensions
// ============================================================================
// PDC access for chunks with a lightweight marker system.
// Same pattern as Item/Entity for API consistency.
//
// ## Thread Safety
// All functions in this file access chunk PDC which requires:
// - Paper: Main server thread
// - Folia: The region thread that owns the chunk
//
// For async chunk loading, use World.getChunkAtAsync() then access PDC
// in the callback (which runs on the correct thread automatically).
// ============================================================================

/** Marker value for chunk identification - uses minimal storage (1 byte) */
private const val MARKER: Byte = 1

// ============================================================================
// Chunk Marker System
// ============================================================================

/**
 * Marks this chunk with a unique identifier key using a BYTE marker.
 *
 * ```kotlin
 * val claimedKey = NamespacedKey(plugin, "claimed")
 * chunk.markAs(claimedKey)
 *
 * // Later, check if a chunk has this marker:
 * if (chunk.hasChunkKey(claimedKey)) { ... }
 * ```
 */
fun Chunk.markAs(plugin: Plugin, key: String): Chunk =
    markAs(NamespacedKey(plugin, key))

/**
 * Marks this chunk with a NamespacedKey identifier using a BYTE marker.
 */
fun Chunk.markAs(key: NamespacedKey): Chunk {
    persistentDataContainer.set(key, PersistentDataType.BYTE, MARKER)
    return this
}

/**
 * Removes a chunk marker key from this chunk.
 */
fun Chunk.unmarkAs(plugin: Plugin, key: String): Chunk =
    unmarkAs(NamespacedKey(plugin, key))

/**
 * Removes a NamespacedKey marker from this chunk.
 */
fun Chunk.unmarkAs(key: NamespacedKey): Chunk {
    persistentDataContainer.remove(key)
    return this
}

/**
 * Checks if this chunk has the specified marker key.
 *
 * ```kotlin
 * val claimedKey = NamespacedKey(plugin, "claimed")
 * if (chunk.hasChunkKey(claimedKey)) {
 *     // Handle claimed chunk
 * }
 * ```
 */
fun Chunk.hasChunkKey(key: NamespacedKey): Boolean =
    persistentDataContainer.has(key, PersistentDataType.BYTE)

/**
 * Checks if this chunk matches any of the provided keys.
 * Returns the first matching key, or null if none match.
 *
 * ```kotlin
 * val claimedKey = NamespacedKey(plugin, "claimed")
 * val protectedKey = NamespacedKey(plugin, "protected")
 *
 * when (chunk.matchesAnyKey(claimedKey, protectedKey)) {
 *     claimedKey -> handleClaimed()
 *     protectedKey -> handleProtected()
 *     null -> { /* normal chunk */ }
 * }
 * ```
 */
fun Chunk.matchesAnyKey(vararg keys: NamespacedKey): NamespacedKey? =
    keys.firstOrNull { hasChunkKey(it) }

// ============================================================================
// Chunk PDC DSL
// ============================================================================

/**
 * Edits the PersistentDataContainer of this Chunk using a DSL block.
 * Returns the Chunk for chaining.
 *
 * ```kotlin
 * chunk.pdc {
 *     setString(ownerKey, playerUuid.toString())
 *     setLong(claimedAtKey, System.currentTimeMillis())
 * }
 * ```
 */
inline fun Chunk.pdc(block: PersistentDataContainer.() -> Unit): Chunk {
    persistentDataContainer.block()
    return this
}
