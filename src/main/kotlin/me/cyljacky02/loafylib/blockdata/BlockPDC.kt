package me.cyljacky02.loafylib.blockdata

import org.bukkit.NamespacedKey
import org.bukkit.block.Block
import org.bukkit.persistence.PersistentDataAdapterContext
import org.bukkit.persistence.PersistentDataContainer
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.Plugin

/**
 * A PersistentDataContainer wrapper for storing data on a specific block.
 *
 * Data is stored within the chunk's PDC using bit-packed coordinate keys in the format
 * `{plugin_namespace}:{5-char hex}`, where coordinates are packed as:
 * - Bits 16-19: relX (0-15, chunk-relative)
 * - Bits 12-15: relZ (0-15, chunk-relative)
 * - Bits 0-11: absY + 2048 (full Y range support)
 *
 * ## Usage Example
 *
 * ```kotlin
 * val blockPdc = blockDataService.getBlockData(block, plugin)
 *
 * // Store data
 * blockPdc.set(myKey, PersistentDataType.STRING, "value")
 * blockPdc.set(countKey, PersistentDataType.INTEGER, 42)
 *
 * // Retrieve data
 * val value = blockPdc.get(myKey, PersistentDataType.STRING)
 * val count = blockPdc.getOrDefault(countKey, PersistentDataType.INTEGER, 0)
 *
 * // Protection from automatic lifecycle management
 * blockPdc.isProtected = true
 *
 * // Copy data to another block
 * blockPdc.copyTo(destinationBlock)
 *
 * // Clear all data
 * blockPdc.clear()
 * ```
 *
 * @property block the block this PDC is associated with
 * @property plugin the plugin owning this data
 */
class BlockPDC(
    val block: Block,
    val plugin: Plugin,
    private val onModified: () -> Unit = {}
) : PersistentDataContainer {

    companion object {

        /**
         * Y-coordinate offset to convert signed Y (-2048 to 2047) to unsigned (0 to 4095).
         * This allows storing the full Minecraft Y range in 12 unsigned bits.
         */
        private const val Y_OFFSET = 2048

        /**
         * Packs chunk-relative block coordinates into a compact 20-bit integer.
         *
         * Bit layout (20 bits total):
         * - Bits 16-19: relX (4 bits, 0-15)
         * - Bits 12-15: relZ (4 bits, 0-15)
         * - Bits 0-11: absY + 2048 (12 bits, 0-4095)
         *
         * This follows Paper/Minecraft's bit-packing philosophy for efficient
         * coordinate storage while avoiding redundancy with chunk PDC location.
         *
         * @param relX relative X coordinate within chunk (0-15)
         * @param absY absolute Y coordinate (-2048 to 2047)
         * @param relZ relative Z coordinate within chunk (0-15)
         * @return packed integer containing all coordinates
         */
        fun packBlockKey(relX: Int, absY: Int, relZ: Int): Int {
            return ((relX and 0xF) shl 16) or
                   ((relZ and 0xF) shl 12) or
                   ((absY + Y_OFFSET) and 0xFFF)
        }

        /**
         * Unpacks block coordinates from a packed integer key.
         *
         * @param packed the packed integer from [packBlockKey]
         * @return triple of (relX, absY, relZ)
         */
        fun unpackBlockKey(packed: Int): Triple<Int, Int, Int> {
            val relX = (packed shr 16) and 0xF
            val relZ = (packed shr 12) and 0xF
            val absY = (packed and 0xFFF) - Y_OFFSET
            return Triple(relX, absY, relZ)
        }

        /**
         * Generates the coordinate-based key for storing block data in the chunk PDC.
         *
         * Format: `{namespace}:{5-char hex}` (e.g., "myplugin:0f140")
         *
         * Uses bit-packed coordinates converted to zero-padded hex for:
         * - Fixed 5-character key length
         * - Fast parsing (no regex)
         * - Compact storage (~50% smaller than string format)
         *
         * @param block the block to generate a key for
         * @param plugin the plugin namespace
         * @return the NamespacedKey for this block's data
         */
        fun createBlockKey(block: Block, plugin: Plugin): NamespacedKey {
            val packed = packBlockKey(
                relX = block.x and 0xF,
                absY = block.y,
                relZ = block.z and 0xF
            )
            return NamespacedKey(plugin, packed.toString(16).padStart(5, '0'))
        }

        /**
         * Parses block coordinates from a hex-encoded block key.
         *
         * @param key the key string (5-char hex, e.g., "0f140")
         * @return triple of (relX, absY, relZ) or null if parsing fails
         */
        fun parseBlockKey(key: String): Triple<Int, Int, Int>? {
            return try {
                unpackBlockKey(key.toInt(16))
            } catch (e: NumberFormatException) {
                null
            }
        }
    }

    /**
     * The underlying PDC stored in the chunk, lazily loaded.
     */
    private val pdc: PersistentDataContainer by lazy {
        val chunkPdc = block.chunk.persistentDataContainer
        val blockKey = createBlockKey(block, plugin)
        
        chunkPdc.get(blockKey, PersistentDataType.TAG_CONTAINER)
            ?: chunkPdc.adapterContext.newPersistentDataContainer()
    }

    /**
     * Whether this block data is protected from automatic lifecycle changes.
     *
     * When true, the data will not be automatically removed or moved when
     * the block is broken, exploded, or pushed by pistons.
     */
    var isProtected: Boolean
        get() = pdc.has(BlockDataKeys.PROTECTED, PersistentDataType.BYTE) &&
                pdc.get(BlockDataKeys.PROTECTED, PersistentDataType.BYTE) == 1.toByte()
        set(value) {
            if (value) {
                pdc.set(BlockDataKeys.PROTECTED, PersistentDataType.BYTE, 1.toByte())
            } else {
                pdc.remove(BlockDataKeys.PROTECTED)
            }
            save()
        }

    /**
     * Clears all data from this block, including protection status.
     *
     * After clearing, the block's entry is removed from the chunk PDC.
     */
    fun clear() {
        val keys = pdc.keys.toList()
        for (key in keys) {
            pdc.remove(key)
        }
        save()
    }

    /**
     * Copies all data from this block to another block.
     *
     * Existing data at the destination is preserved unless overwritten
     * by keys from this block.
     *
     * @param destination the block to copy data to
     * @return the BlockPDC for the destination block
     */
    fun copyTo(destination: Block): BlockPDC {
        val destPdc = BlockPDC(destination, plugin, onModified)
        pdc.copyTo(destPdc.pdc, false)
        destPdc.save()
        return destPdc
    }

    /**
     * Saves the current PDC state to the chunk.
     *
     * If the PDC is empty, removes the block's entry from the chunk PDC.
     */
    private fun save() {
        val chunkPdc = block.chunk.persistentDataContainer
        val blockKey = createBlockKey(block, plugin)
        
        if (pdc.isEmpty) {
            chunkPdc.remove(blockKey)
        } else {
            chunkPdc.set(blockKey, PersistentDataType.TAG_CONTAINER, pdc)
        }
        onModified()
    }

    // ==========================================================================
    // PersistentDataContainer Interface Implementation
    // ==========================================================================

    override fun <T : Any, Z : Any> set(key: NamespacedKey, type: PersistentDataType<T, Z>, value: Z) {
        pdc.set(key, type, value)
        save()
    }

    override fun <T : Any, Z : Any> has(key: NamespacedKey, type: PersistentDataType<T, Z>): Boolean {
        return pdc.has(key, type)
    }

    override fun has(key: NamespacedKey): Boolean {
        return pdc.has(key)
    }

    override fun <T : Any, Z : Any> get(key: NamespacedKey, type: PersistentDataType<T, Z>): Z? {
        return pdc.get(key, type)
    }

    override fun <T : Any, Z : Any> getOrDefault(
        key: NamespacedKey,
        type: PersistentDataType<T, Z>,
        defaultValue: Z
    ): Z {
        return pdc.getOrDefault(key, type, defaultValue)
    }

    override fun getKeys(): MutableSet<NamespacedKey> {
        return pdc.keys
    }

    override fun remove(key: NamespacedKey) {
        pdc.remove(key)
        save()
    }

    override fun isEmpty(): Boolean {
        return pdc.isEmpty
    }

    override fun getSize(): Int {
        return pdc.getSize()
    }

    override fun copyTo(other: PersistentDataContainer, replace: Boolean) {
        pdc.copyTo(other, replace)
    }

    override fun getAdapterContext(): PersistentDataAdapterContext {
        return pdc.adapterContext
    }

    override fun serializeToBytes(): ByteArray {
        return pdc.serializeToBytes()
    }

    override fun readFromBytes(bytes: ByteArray, clear: Boolean) {
        pdc.readFromBytes(bytes, clear)
        save()
    }

    override fun readFromBytes(bytes: ByteArray) {
        pdc.readFromBytes(bytes)
        save()
    }
}
