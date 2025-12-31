package me.cyljacky02.loafylib.blockdata

import org.bukkit.block.Block
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.Plugin

// ============================================================================
// Block Extension Functions for BlockDataService
// ============================================================================

/**
 * Gets or creates a [BlockPDC] for this block.
 *
 * The returned container is scoped to the calling plugin's namespace,
 * ensuring isolation between plugins storing data on the same block.
 *
 * ```kotlin
 * val blockPdc = block.getBlockData(plugin)
 * blockPdc.set(myKey, PersistentDataType.STRING, "value")
 * ```
 *
 * @param plugin the plugin owning the data
 * @return a [BlockPDC] for storing/retrieving data on this block
 */
fun Block.getBlockData(plugin: Plugin): BlockPDC {
    return BlockPDC(this, plugin)
}

/**
 * Checks if this block has any custom data for the specified plugin.
 *
 * This is more efficient than [getBlockData] when you only need to
 * check existence, as it doesn't create a full [BlockPDC] instance.
 *
 * ```kotlin
 * if (block.hasBlockData(plugin)) {
 *     val data = block.getBlockData(plugin)
 *     // Process data...
 * }
 * ```
 *
 * @param plugin the plugin to check data for
 * @return true if the block has data for this plugin, false otherwise
 */
fun Block.hasBlockData(plugin: Plugin): Boolean {
    val chunkPdc = chunk.persistentDataContainer
    val blockKey = BlockPDC.createBlockKey(this, plugin)
    return chunkPdc.has(blockKey, PersistentDataType.TAG_CONTAINER)
}

/**
 * Clears all custom data from this block for the specified plugin.
 *
 * After clearing, [hasBlockData] will return false for this block.
 *
 * ```kotlin
 * block.clearBlockData(plugin)
 * ```
 *
 * @param plugin the plugin owning the data to clear
 */
fun Block.clearBlockData(plugin: Plugin) {
    if (hasBlockData(plugin)) {
        getBlockData(plugin).clear()
    }
}

/**
 * Checks if this block's data is protected from automatic lifecycle changes.
 *
 * Protected data is not automatically removed or moved when blocks are
 * broken, exploded, or pushed by pistons.
 *
 * This method is optimized to check protection status directly from the
 * chunk PDC without creating a full BlockPDC instance when possible.
 *
 * ```kotlin
 * if (block.isBlockDataProtected(plugin)) {
 *     // Data won't be auto-removed on block break
 * }
 * ```
 *
 * @param plugin the plugin owning the data
 * @return true if the data is protected, false if not protected or no data exists
 */
fun Block.isBlockDataProtected(plugin: Plugin): Boolean {
    val chunkPdc = chunk.persistentDataContainer
    val blockKey = BlockPDC.createBlockKey(this, plugin)
    
    val blockPdc = chunkPdc.get(blockKey, PersistentDataType.TAG_CONTAINER)
        ?: return false
    
    return blockPdc.has(BlockDataKeys.PROTECTED, PersistentDataType.BYTE) &&
           blockPdc.get(BlockDataKeys.PROTECTED, PersistentDataType.BYTE) == 1.toByte()
}

// ============================================================================
// BlockPDC DSL Extensions
// ============================================================================

/**
 * Edits this BlockPDC using a DSL block.
 * Returns the same BlockPDC for chaining.
 *
 * ```kotlin
 * val blockPdc = block.getBlockData(plugin).edit {
 *     setString(myKey, "value")
 *     setInt(countKey, 42)
 *     isProtected = true
 * }
 * ```
 *
 * @param block the DSL block for editing
 * @return this BlockPDC for chaining
 */
inline fun BlockPDC.edit(block: BlockPDC.() -> Unit): BlockPDC {
    this.block()
    return this
}

// ============================================================================
// BlockPDC Type-Safe Getters
// ============================================================================
// Note: BlockPDC implements PersistentDataContainer, so it can use the shared
// extensions from me.cyljacky02.loafylib.pdc.PdcExtensions.
// These BlockPDC-specific extensions are kept for backwards compatibility
// and to provide explicit documentation for BlockPDC usage.
// ============================================================================

/** Gets a String value from this BlockPDC, or null if not present. */
fun BlockPDC.getString(key: org.bukkit.NamespacedKey): String? =
    get(key, PersistentDataType.STRING)

/** Gets a Byte value from this BlockPDC, or null if not present. */
fun BlockPDC.getByte(key: org.bukkit.NamespacedKey): Byte? =
    get(key, PersistentDataType.BYTE)

/** Gets a Short value from this BlockPDC, or null if not present. */
fun BlockPDC.getShort(key: org.bukkit.NamespacedKey): Short? =
    get(key, PersistentDataType.SHORT)

/** Gets an Int value from this BlockPDC, or null if not present. */
fun BlockPDC.getInt(key: org.bukkit.NamespacedKey): Int? =
    get(key, PersistentDataType.INTEGER)

/** Gets a Long value from this BlockPDC, or null if not present. */
fun BlockPDC.getLong(key: org.bukkit.NamespacedKey): Long? =
    get(key, PersistentDataType.LONG)

/** Gets a Float value from this BlockPDC, or null if not present. */
fun BlockPDC.getFloat(key: org.bukkit.NamespacedKey): Float? =
    get(key, PersistentDataType.FLOAT)

/** Gets a Double value from this BlockPDC, or null if not present. */
fun BlockPDC.getDouble(key: org.bukkit.NamespacedKey): Double? =
    get(key, PersistentDataType.DOUBLE)

/** Gets a Boolean value from this BlockPDC, or null if not present. */
fun BlockPDC.getBoolean(key: org.bukkit.NamespacedKey): Boolean? =
    get(key, PersistentDataType.BOOLEAN)

/** Gets a ByteArray value from this BlockPDC, or null if not present. */
fun BlockPDC.getByteArray(key: org.bukkit.NamespacedKey): ByteArray? =
    get(key, PersistentDataType.BYTE_ARRAY)

/** Gets an IntArray value from this BlockPDC, or null if not present. */
fun BlockPDC.getIntArray(key: org.bukkit.NamespacedKey): IntArray? =
    get(key, PersistentDataType.INTEGER_ARRAY)

/** Gets a LongArray value from this BlockPDC, or null if not present. */
fun BlockPDC.getLongArray(key: org.bukkit.NamespacedKey): LongArray? =
    get(key, PersistentDataType.LONG_ARRAY)

// ============================================================================
// BlockPDC Type-Safe Setters
// ============================================================================

/** Sets a String value in this BlockPDC. */
fun BlockPDC.setString(key: org.bukkit.NamespacedKey, value: String) {
    set(key, PersistentDataType.STRING, value)
}

/** Sets a Byte value in this BlockPDC. */
fun BlockPDC.setByte(key: org.bukkit.NamespacedKey, value: Byte) {
    set(key, PersistentDataType.BYTE, value)
}

/** Sets a Short value in this BlockPDC. */
fun BlockPDC.setShort(key: org.bukkit.NamespacedKey, value: Short) {
    set(key, PersistentDataType.SHORT, value)
}

/** Sets an Int value in this BlockPDC. */
fun BlockPDC.setInt(key: org.bukkit.NamespacedKey, value: Int) {
    set(key, PersistentDataType.INTEGER, value)
}

/** Sets a Long value in this BlockPDC. */
fun BlockPDC.setLong(key: org.bukkit.NamespacedKey, value: Long) {
    set(key, PersistentDataType.LONG, value)
}

/** Sets a Float value in this BlockPDC. */
fun BlockPDC.setFloat(key: org.bukkit.NamespacedKey, value: Float) {
    set(key, PersistentDataType.FLOAT, value)
}

/** Sets a Double value in this BlockPDC. */
fun BlockPDC.setDouble(key: org.bukkit.NamespacedKey, value: Double) {
    set(key, PersistentDataType.DOUBLE, value)
}

/** Sets a Boolean value in this BlockPDC. */
fun BlockPDC.setBoolean(key: org.bukkit.NamespacedKey, value: Boolean) {
    set(key, PersistentDataType.BOOLEAN, value)
}

/** Sets a ByteArray value in this BlockPDC. */
fun BlockPDC.setByteArray(key: org.bukkit.NamespacedKey, value: ByteArray) {
    set(key, PersistentDataType.BYTE_ARRAY, value)
}

/** Sets an IntArray value in this BlockPDC. */
fun BlockPDC.setIntArray(key: org.bukkit.NamespacedKey, value: IntArray) {
    set(key, PersistentDataType.INTEGER_ARRAY, value)
}

/** Sets a LongArray value in this BlockPDC. */
fun BlockPDC.setLongArray(key: org.bukkit.NamespacedKey, value: LongArray) {
    set(key, PersistentDataType.LONG_ARRAY, value)
}
