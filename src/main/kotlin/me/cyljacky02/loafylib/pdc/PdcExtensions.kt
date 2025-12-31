package me.cyljacky02.loafylib.pdc

import org.bukkit.NamespacedKey
import org.bukkit.persistence.PersistentDataContainer
import org.bukkit.persistence.PersistentDataType

// ============================================================================
// Core PersistentDataContainer Extensions
// ============================================================================
// Shared type-safe getters/setters for any PersistentDataContainer.
// Works with Item PDC, Entity PDC, Chunk PDC, etc.
// ============================================================================

/**
 * Gets a value from the PersistentDataContainer, or null if not present.
 *
 * ```kotlin
 * val value: String? = pdc.getOrNull(key, PersistentDataType.STRING)
 * ```
 */
fun <P : Any, C : Any> PersistentDataContainer.getOrNull(
    key: NamespacedKey,
    type: PersistentDataType<P, C>
): C? = if (has(key, type)) get(key, type) else null

// ============================================================================
// Type-Safe Getters
// ============================================================================

/** Gets a String value, or null if not present. */
fun PersistentDataContainer.getString(key: NamespacedKey): String? =
    getOrNull(key, PersistentDataType.STRING)

/** Gets a Byte value, or null if not present. */
fun PersistentDataContainer.getByte(key: NamespacedKey): Byte? =
    getOrNull(key, PersistentDataType.BYTE)

/** Gets a Short value, or null if not present. */
fun PersistentDataContainer.getShort(key: NamespacedKey): Short? =
    getOrNull(key, PersistentDataType.SHORT)

/** Gets an Int value, or null if not present. */
fun PersistentDataContainer.getInt(key: NamespacedKey): Int? =
    getOrNull(key, PersistentDataType.INTEGER)

/** Gets a Long value, or null if not present. */
fun PersistentDataContainer.getLong(key: NamespacedKey): Long? =
    getOrNull(key, PersistentDataType.LONG)

/** Gets a Float value, or null if not present. */
fun PersistentDataContainer.getFloat(key: NamespacedKey): Float? =
    getOrNull(key, PersistentDataType.FLOAT)

/** Gets a Double value, or null if not present. */
fun PersistentDataContainer.getDouble(key: NamespacedKey): Double? =
    getOrNull(key, PersistentDataType.DOUBLE)

/** Gets a Boolean value, or null if not present. */
fun PersistentDataContainer.getBoolean(key: NamespacedKey): Boolean? =
    getOrNull(key, PersistentDataType.BOOLEAN)

/** Gets a ByteArray value, or null if not present. */
fun PersistentDataContainer.getByteArray(key: NamespacedKey): ByteArray? =
    getOrNull(key, PersistentDataType.BYTE_ARRAY)

/** Gets an IntArray value, or null if not present. */
fun PersistentDataContainer.getIntArray(key: NamespacedKey): IntArray? =
    getOrNull(key, PersistentDataType.INTEGER_ARRAY)

/** Gets a LongArray value, or null if not present. */
fun PersistentDataContainer.getLongArray(key: NamespacedKey): LongArray? =
    getOrNull(key, PersistentDataType.LONG_ARRAY)

// ============================================================================
// Type-Safe Setters
// ============================================================================

/** Sets a String value. */
fun PersistentDataContainer.setString(key: NamespacedKey, value: String) {
    set(key, PersistentDataType.STRING, value)
}

/** Sets a Byte value. */
fun PersistentDataContainer.setByte(key: NamespacedKey, value: Byte) {
    set(key, PersistentDataType.BYTE, value)
}

/** Sets a Short value. */
fun PersistentDataContainer.setShort(key: NamespacedKey, value: Short) {
    set(key, PersistentDataType.SHORT, value)
}

/** Sets an Int value. */
fun PersistentDataContainer.setInt(key: NamespacedKey, value: Int) {
    set(key, PersistentDataType.INTEGER, value)
}

/** Sets a Long value. */
fun PersistentDataContainer.setLong(key: NamespacedKey, value: Long) {
    set(key, PersistentDataType.LONG, value)
}

/** Sets a Float value. */
fun PersistentDataContainer.setFloat(key: NamespacedKey, value: Float) {
    set(key, PersistentDataType.FLOAT, value)
}

/** Sets a Double value. */
fun PersistentDataContainer.setDouble(key: NamespacedKey, value: Double) {
    set(key, PersistentDataType.DOUBLE, value)
}

/** Sets a Boolean value. */
fun PersistentDataContainer.setBoolean(key: NamespacedKey, value: Boolean) {
    set(key, PersistentDataType.BOOLEAN, value)
}

/** Sets a ByteArray value. */
fun PersistentDataContainer.setByteArray(key: NamespacedKey, value: ByteArray) {
    set(key, PersistentDataType.BYTE_ARRAY, value)
}

/** Sets an IntArray value. */
fun PersistentDataContainer.setIntArray(key: NamespacedKey, value: IntArray) {
    set(key, PersistentDataType.INTEGER_ARRAY, value)
}

/** Sets a LongArray value. */
fun PersistentDataContainer.setLongArray(key: NamespacedKey, value: LongArray) {
    set(key, PersistentDataType.LONG_ARRAY, value)
}
