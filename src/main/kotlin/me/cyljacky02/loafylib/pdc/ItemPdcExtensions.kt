package me.cyljacky02.loafylib.pdc

import org.bukkit.NamespacedKey
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.persistence.PersistentDataContainer
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.Plugin

// ============================================================================
// ItemStack PDC Extensions
// ============================================================================
// Efficient PDC access for items using Paper's optimized editPersistentDataContainer.
// Includes a lightweight marker system for item identification.
// ============================================================================

/** Marker value for item identification - uses minimal storage (1 byte) */
private const val MARKER: Byte = 1

// ============================================================================
// Item Marker System (BYTE marker approach)
// ============================================================================

/**
 * Marks this item with a unique identifier key using a BYTE marker.
 * Uses PersistentDataContainer for reliable identification across server restarts.
 *
 * This approach uses the key directly in PDC (1 byte value) instead of storing
 * the key as a string value (~40 bytes), providing ~97% storage reduction.
 *
 * ```kotlin
 * val wand = ItemStack(Material.STICK).edit {
 *     name("<gold>Magic Wand</gold>".mini())
 * }.markAs(plugin, "magic_wand")
 *
 * // Items can have multiple markers:
 * item.markAs(plugin, "custom_item").markAs(plugin, "tradeable")
 * ```
 */
fun ItemStack.markAs(plugin: Plugin, key: String): ItemStack =
    markAs(NamespacedKey(plugin, key))

/**
 * Marks this item with a NamespacedKey identifier using a BYTE marker.
 */
fun ItemStack.markAs(key: NamespacedKey): ItemStack {
    editPersistentDataContainer { pdc -> pdc.set(key, PersistentDataType.BYTE, MARKER) }
    return this
}

/**
 * Removes an item marker key from this item.
 *
 * ```kotlin
 * item.unmarkAs(plugin, "tradeable")
 * ```
 */
fun ItemStack.unmarkAs(plugin: Plugin, key: String): ItemStack =
    unmarkAs(NamespacedKey(plugin, key))

/**
 * Removes a NamespacedKey marker from this item.
 */
fun ItemStack.unmarkAs(key: NamespacedKey): ItemStack {
    editPersistentDataContainer { pdc -> pdc.remove(key) }
    return this
}

/**
 * Checks if this item has the specified marker key.
 *
 * ```kotlin
 * val wandKey = NamespacedKey(plugin, "magic_wand")
 * if (event.item?.hasItemKey(wandKey) == true) {
 *     // Handle wand interaction
 * }
 * ```
 */
fun ItemStack.hasItemKey(key: NamespacedKey): Boolean =
    persistentDataContainer.has(key, PersistentDataType.BYTE)

/**
 * Checks if this item matches any of the provided keys.
 * Returns the first matching key, or null if none match.
 *
 * ```kotlin
 * val wandKey = NamespacedKey(plugin, "wand")
 * val swordKey = NamespacedKey(plugin, "sword")
 *
 * when (item.matchesAnyKey(wandKey, swordKey)) {
 *     wandKey -> handleWand()
 *     swordKey -> handleSword()
 *     null -> { /* not a custom item */ }
 * }
 * ```
 */
fun ItemStack.matchesAnyKey(vararg keys: NamespacedKey): NamespacedKey? =
    keys.firstOrNull { hasItemKey(it) }

// ============================================================================
// ItemStack PDC DSL
// ============================================================================

/**
 * Edits the PersistentDataContainer of this ItemStack using Paper's optimized API.
 * Returns the ItemStack for chaining.
 *
 * This uses Paper's `editPersistentDataContainer` which is more efficient than
 * going through ItemMeta when only modifying PDC data.
 *
 * ```kotlin
 * val item = ItemStack(Material.DIAMOND).pdc {
 *     setString(myKey, "custom_value")
 *     setInt(countKey, 42)
 * }
 * ```
 */
inline fun ItemStack.pdc(crossinline block: PersistentDataContainer.() -> Unit): ItemStack {
    editPersistentDataContainer { it.block() }
    return this
}

/**
 * Edits the PersistentDataContainer of this ItemMeta using a DSL block.
 *
 * ```kotlin
 * itemMeta.pdc {
 *     setInt(key, 42)
 * }
 * ```
 */
inline fun ItemMeta.pdc(block: PersistentDataContainer.() -> Unit) {
    persistentDataContainer.block()
}
