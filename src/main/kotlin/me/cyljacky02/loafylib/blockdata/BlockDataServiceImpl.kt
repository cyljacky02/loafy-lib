package me.cyljacky02.loafylib.blockdata

import org.bukkit.Bukkit
import org.bukkit.Chunk
import org.bukkit.block.Block
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.Plugin
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Implementation of [BlockDataService] providing per-block persistent data storage.
 *
 * Data is stored within chunk PDCs using coordinate-based keys, ensuring automatic
 * persistence without external files or databases. The implementation is Folia-compatible,
 * using Paper's GlobalRegionScheduler for dirty block cleanup.
 *
 * ## Storage Architecture
 *
 * Block data is stored as nested TAG_CONTAINER entries within the chunk's PDC:
 * - Key format: `{plugin_namespace}:{5-char hex}` (bit-packed coordinates)
 * - See [BlockPDC.createBlockKey] for coordinate encoding details
 *
 * ## Thread Safety
 *
 * All methods in this service access chunk/block data and require:
 * - **Paper**: Main server thread
 * - **Folia**: The region thread that owns the block/chunk
 *
 * For async chunk loading, use `World.getChunkAtAsync()` then access block data
 * in the callback (which runs on the correct thread automatically on Paper).
 *
 * ## Dirty Block Tracking
 *
 * Blocks modified in the current tick are tracked to prevent data loss during
 * BlockPlaceEvent. The dirty set is cleaned up after 1 tick using Folia-compatible
 * scheduling.
 *
 * @param loafyLibPlugin The LoafyLib plugin instance for scheduling
 */
class BlockDataServiceImpl(
    private val loafyLibPlugin: Plugin
) : BlockDataService {

    /**
     * Registered listeners per plugin for automatic lifecycle management.
     */
    private val registeredListeners = ConcurrentHashMap.newKeySet<String>()

    override fun getBlockData(block: Block, plugin: Plugin): BlockPDC {
        return BlockPDC(block, plugin)
    }

    override fun hasBlockData(block: Block, plugin: Plugin): Boolean {
        val chunkPdc = block.chunk.persistentDataContainer
        val blockKey = BlockPDC.createBlockKey(block, plugin)
        return chunkPdc.has(blockKey, PersistentDataType.TAG_CONTAINER)
    }

    override fun getBlocksWithData(chunk: Chunk, plugin: Plugin): Set<Block> {
        val chunkPdc = chunk.persistentDataContainer
        val namespace = plugin.namespace()
        val world = chunk.world
        val chunkX = chunk.x
        val chunkZ = chunk.z

        return chunkPdc.keys
            .filter { it.namespace == namespace }
            .mapNotNull { key ->
                BlockPDC.parseBlockKey(key.key)?.let { (relX, absY, relZ) ->
                    // Convert relative chunk coordinates to absolute world coordinates
                    val worldX = (chunkX shl 4) + relX
                    val worldZ = (chunkZ shl 4) + relZ
                    
                    // Validate Y is within world bounds
                    if (absY >= world.minHeight && absY < world.maxHeight) {
                        world.getBlockAt(worldX, absY, worldZ)
                    } else {
                        null
                    }
                }
            }
            .toSet()
    }

    override fun isProtected(block: Block, plugin: Plugin): Boolean {
        val chunkPdc = block.chunk.persistentDataContainer
        val blockKey = BlockPDC.createBlockKey(block, plugin)
        
        // Get the block's PDC directly from chunk without creating BlockPDC wrapper
        val blockPdc = chunkPdc.get(blockKey, PersistentDataType.TAG_CONTAINER)
            ?: return false
        
        // Check protection key directly using cached key
        return blockPdc.has(BlockDataKeys.PROTECTED, PersistentDataType.BYTE) &&
               blockPdc.get(BlockDataKeys.PROTECTED, PersistentDataType.BYTE) == 1.toByte()
    }

    override fun registerListener(plugin: Plugin) {
        val pluginName = plugin.name
        if (registeredListeners.add(pluginName)) {
            val listener = BlockDataListener(plugin, this)
            Bukkit.getPluginManager().registerEvents(listener, plugin)
        }
    }

    // ==========================================================================
    // PluginComponent Lifecycle
    // ==========================================================================

    override suspend fun initialize() {
        // No initialization needed - service is ready immediately
    }

    override suspend fun shutdown() {
        // Clear dirty blocks on shutdown
        DirtyBlockTracker.clear(loafyLibPlugin.name)
        for (pluginName in registeredListeners) {
            DirtyBlockTracker.clear(pluginName)
        }
        registeredListeners.clear()
    }

    // ==========================================================================
    // Internal Methods
    // ==========================================================================

    /**
     * Checks if a block was modified in the current tick.
     * Used by BlockDataListener to skip BlockPlaceEvent for recently modified blocks.
     */
    internal fun isDirty(block: Block, plugin: Plugin): Boolean = DirtyBlockTracker.isDirty(plugin, block)

    /**
     * Removes block data for a specific block and plugin.
     * Used by BlockDataListener when handling block removal events.
     */
    internal fun removeBlockData(block: Block, plugin: Plugin) {
        val chunkPdc = block.chunk.persistentDataContainer
        val blockKey = BlockPDC.createBlockKey(block, plugin)
        chunkPdc.remove(blockKey)
    }

    /**
     * Moves block data from source to destination.
     * Used by BlockDataListener when handling piston events.
     */
    internal fun moveBlockData(source: Block, destination: Block, plugin: Plugin) {
        if (!hasBlockData(source, plugin)) return
        
        val sourcePdc = getBlockData(source, plugin)
        sourcePdc.copyTo(destination)
        removeBlockData(source, plugin)
    }

    /**
     * Entry representing a block location for dirty tracking.
     */
    internal data class BlockEntry(
        val worldId: UUID,
        val x: Int,
        val y: Int,
        val z: Int
    )
}

internal object DirtyBlockTracker {
 
    private val dirtyBlocksByPlugin = ConcurrentHashMap<String, MutableSet<BlockDataServiceImpl.BlockEntry>>()

    fun markDirty(plugin: Plugin, block: Block) {
        if (!plugin.isEnabled) return

        val entry = BlockDataServiceImpl.BlockEntry(
            worldId = block.world.uid,
            x = block.x,
            y = block.y,
            z = block.z
        )

        val pluginName = plugin.name
        val dirtyBlocks = dirtyBlocksByPlugin.computeIfAbsent(pluginName) { ConcurrentHashMap.newKeySet() }
        dirtyBlocks.add(entry)

        Bukkit.getGlobalRegionScheduler().runDelayed(plugin, {
            dirtyBlocks.remove(entry)
            // Note: We don't remove empty sets from dirtyBlocksByPlugin here to avoid
            // race conditions. Empty sets have minimal overhead and are cleaned on shutdown.
        }, 1L)
    }

    fun isDirty(plugin: Plugin, block: Block): Boolean {
        val dirtyBlocks = dirtyBlocksByPlugin[plugin.name] ?: return false
        val entry = BlockDataServiceImpl.BlockEntry(
            worldId = block.world.uid,
            x = block.x,
            y = block.y,
            z = block.z
        )
        return dirtyBlocks.contains(entry)
    }

    fun clear(pluginName: String) {
        dirtyBlocksByPlugin.remove(pluginName)?.clear()
    }
}
