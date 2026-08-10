package me.cyljacky02.loafylib.pdc

import org.bukkit.NamespacedKey
import org.bukkit.block.Block
import org.bukkit.block.TileState
import org.bukkit.persistence.PersistentDataContainer
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.Plugin

// ============================================================================
// TileState PDC Extensions
// ============================================================================
// PDC access for block entities (TileState) with a lightweight marker system.
// Same pattern as Item/Entity/Chunk for API consistency.
//
// TileState covers all block entities: Skull, Chest, Sign, Banner, Barrel, etc.
//
// ## Thread Safety
// All functions in this file access block entity PDC which requires:
// - Paper: Main server thread
// - Folia: The region thread that owns the block
//
// Use Location.withRegionContext(plugin) or regionDispatcher for safe access.
//
// ## Important: update() Requirement
// TileState operates on snapshots. After modifying PDC data, you MUST call
// BlockState.update() to persist changes. The [pdc] DSL and marker functions
// in this file handle update() automatically.
// ============================================================================

/** Marker value for tile state identification - uses minimal storage (1 byte) */
private const val MARKER: Byte = 1

// ============================================================================
// TileState Marker System
// ============================================================================

/**
 * Marks this tile state with a unique identifier key using a BYTE marker.
 * Automatically calls [update()][TileState.update] to persist the change.
 *
 * ```kotlin
 * val customChestKey = NamespacedKey(plugin, "custom_chest")
 * (block.state as? TileState)?.markAs(customChestKey)
 *
 * // Later, check if a tile state has this marker:
 * if (tileState.hasTileKey(customChestKey)) { ... }
 * ```
 */
fun TileState.markAs(plugin: Plugin, key: String): TileState =
    markAs(NamespacedKey(plugin, key))

/**
 * Marks this tile state with a NamespacedKey identifier using a BYTE marker.
 * Automatically calls [update()][TileState.update] to persist the change.
 */
fun TileState.markAs(key: NamespacedKey): TileState {
    persistentDataContainer.set(key, PersistentDataType.BYTE, MARKER)
    update()
    return this
}

/**
 * Removes a tile state marker key.
 * Automatically calls [update()][TileState.update] to persist the change.
 */
fun TileState.unmarkAs(plugin: Plugin, key: String): TileState =
    unmarkAs(NamespacedKey(plugin, key))

/**
 * Removes a NamespacedKey marker from this tile state.
 * Automatically calls [update()][TileState.update] to persist the change.
 */
fun TileState.unmarkAs(key: NamespacedKey): TileState {
    persistentDataContainer.remove(key)
    update()
    return this
}

/**
 * Checks if this tile state has the specified marker key.
 *
 * ```kotlin
 * val customChestKey = NamespacedKey(plugin, "custom_chest")
 * if (tileState.hasTileKey(customChestKey)) {
 *     // Handle custom chest
 * }
 * ```
 */
fun TileState.hasTileKey(key: NamespacedKey): Boolean =
    persistentDataContainer.has(key, PersistentDataType.BYTE)

/**
 * Checks if this tile state matches any of the provided keys.
 * Returns the first matching key, or null if none match.
 *
 * ```kotlin
 * val vaultKey = NamespacedKey(plugin, "vault")
 * val shopKey = NamespacedKey(plugin, "shop")
 *
 * when (tileState.matchesAnyKey(vaultKey, shopKey)) {
 *     vaultKey -> handleVault()
 *     shopKey -> handleShop()
 *     null -> { /* not a custom tile */ }
 * }
 * ```
 */
fun TileState.matchesAnyKey(vararg keys: NamespacedKey): NamespacedKey? =
    keys.firstOrNull { hasTileKey(it) }

// ============================================================================
// TileState PDC DSL
// ============================================================================

/**
 * Edits the PersistentDataContainer of this TileState using a DSL block.
 * Automatically calls [update()][TileState.update] after the block completes
 * to persist changes to the world.
 *
 * Returns the TileState for chaining.
 *
 * ```kotlin
 * val skull = block.state as Skull
 * skull.pdc {
 *     setString(headKey, "loafy:zombie")
 *     setInt(schemaKey, 1)
 * }
 * // update() is called automatically
 * ```
 */
inline fun TileState.pdc(block: PersistentDataContainer.() -> Unit): TileState {
    persistentDataContainer.block()
    update()
    return this
}

// ============================================================================
// Block Convenience Extensions
// ============================================================================

/**
 * Gets the block's TileState as the specified type, edits its PDC, and
 * automatically calls [update()][TileState.update] to persist.
 *
 * Returns the TileState if the block has one of the specified type, or null.
 * This combines the cast, null-check, PDC edit, and update into a single call.
 *
 * ```kotlin
 * // Edit a skull's PDC in one call:
 * block.tileStatePdc<Skull> {
 *     setString(headKey, "loafy:zombie")
 *     setInt(schemaKey, 1)
 * } ?: return // block is not a skull
 *
 * // Works with any TileState subtype:
 * block.tileStatePdc<Chest> {
 *     setString(ownerKey, playerUuid.toString())
 * }
 * ```
 */
inline fun <reified T : TileState> Block.tileStatePdc(
    block: PersistentDataContainer.() -> Unit
): T? {
    val state = this.state as? T ?: return null
    state.persistentDataContainer.block()
    state.update()
    return state
}

/**
 * Gets the block's TileState as the specified type for read-only PDC access.
 * Does NOT call update() since no modifications are expected.
 *
 * Returns null if the block does not have a TileState of the specified type.
 *
 * ```kotlin
 * val headKey: String? = block.tileState<Skull>()
 *     ?.persistentDataContainer
 *     ?.getString(headKeyKey)
 * ```
 */
inline fun <reified T : TileState> Block.tileState(): T? =
    this.state as? T
