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
inline fun BlockPDC.edit(crossinline block: BlockPDC.() -> Unit): BlockPDC {
    this.batch { block() }
    return this
}

// ============================================================================
// BlockPDC Type-Safe Extensions
// ============================================================================
// BlockPDC implements PersistentDataContainer, so it automatically inherits all
// type-safe extensions from me.cyljacky02.loafylib.pdc.PdcExtensions.
//
// Usage:
//   import me.cyljacky02.loafylib.pdc.*
//   blockPdc.getString(key)
//   blockPdc.setInt(key, 42)
// ============================================================================
