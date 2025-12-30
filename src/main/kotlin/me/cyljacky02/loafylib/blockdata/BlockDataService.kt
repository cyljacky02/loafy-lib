package me.cyljacky02.loafylib.blockdata

import me.cyljacky02.loafylib.plugin.PluginComponent
import org.bukkit.Chunk
import org.bukkit.block.Block
import org.bukkit.plugin.Plugin

/**
 * Service for storing and retrieving persistent data on individual blocks.
 *
 * Block data is stored within the chunk's PersistentDataContainer using
 * coordinate-based keys, providing automatic persistence without external
 * files or databases.
 *
 * ## Usage Example
 *
 * ```kotlin
 * val blockDataService = registry.get<BlockDataService>()
 *
 * // Store data on a block
 * val blockPdc = blockDataService.getBlockData(block, plugin)
 * blockPdc.set(myKey, PersistentDataType.STRING, "custom value")
 *
 * // Check if block has data
 * if (blockDataService.hasBlockData(block, plugin)) {
 *     val data = blockDataService.getBlockData(block, plugin)
 *     val value = data.get(myKey, PersistentDataType.STRING)
 * }
 *
 * // Get all blocks with data in a chunk
 * val blocksWithData = blockDataService.getBlocksWithData(chunk, plugin)
 * ```
 *
 * ## Automatic Lifecycle Management
 *
 * Call [registerListener] to enable automatic data removal/movement when
 * blocks are broken, exploded, or pushed by pistons. Protected data is
 * excluded from automatic management.
 *
 * ```kotlin
 * // Enable automatic lifecycle management
 * blockDataService.registerListener(plugin)
 *
 * // Protect data from automatic removal
 * val blockPdc = blockDataService.getBlockData(block, plugin)
 * blockPdc.isProtected = true
 * ```
 *
 * @see BlockPDC
 */
interface BlockDataService : PluginComponent {

    /**
     * Gets or creates a [BlockPDC] for the specified block.
     *
     * The returned container is scoped to the calling plugin's namespace,
     * ensuring isolation between plugins storing data on the same block.
     *
     * @param block the block to get data for
     * @param plugin the plugin owning the data
     * @return a [BlockPDC] for storing/retrieving data on this block
     */
    fun getBlockData(block: Block, plugin: Plugin): BlockPDC

    /**
     * Checks if a block has any custom data for the specified plugin.
     *
     * This is more efficient than [getBlockData] when you only need to
     * check existence, as it doesn't create a full [BlockPDC] instance.
     *
     * @param block the block to check
     * @param plugin the plugin to check data for
     * @return true if the block has data for this plugin, false otherwise
     */
    fun hasBlockData(block: Block, plugin: Plugin): Boolean

    /**
     * Returns all blocks in the chunk that have custom data for the specified plugin.
     *
     * @param chunk the chunk to query
     * @param plugin the plugin to filter by
     * @return set of blocks containing data for this plugin (may be empty)
     */
    fun getBlocksWithData(chunk: Chunk, plugin: Plugin): Set<Block>

    /**
     * Checks if block data is protected from automatic lifecycle changes.
     *
     * Protected data is not automatically removed or moved when blocks are
     * broken, exploded, or pushed by pistons.
     *
     * @param block the block to check
     * @param plugin the plugin owning the data
     * @return true if the data is protected, false otherwise
     */
    fun isProtected(block: Block, plugin: Plugin): Boolean

    /**
     * Registers the automatic lifecycle listener for the specified plugin.
     *
     * Once registered, block data will be automatically:
     * - Removed when blocks are broken, exploded, burned, or changed
     * - Moved when blocks are pushed by pistons
     *
     * Protected data is excluded from automatic management.
     *
     * This method should be called once per plugin during initialization.
     *
     * @param plugin the plugin to register lifecycle management for
     */
    fun registerListener(plugin: Plugin)
}
