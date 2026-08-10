package me.cyljacky02.loafylib.blockdata

import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.block.PistonMoveReaction
import org.bukkit.event.Event
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockBurnEvent
import org.bukkit.event.block.BlockExplodeEvent
import org.bukkit.event.block.BlockFadeEvent
import org.bukkit.event.block.BlockFertilizeEvent
import org.bukkit.event.block.BlockPistonExtendEvent
import org.bukkit.event.block.BlockPistonRetractEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.entity.EntityChangeBlockEvent
import org.bukkit.event.entity.EntityExplodeEvent
import org.bukkit.event.world.StructureGrowEvent
import org.bukkit.plugin.Plugin
import org.bukkit.util.Vector

/**
 * Internal listener that handles automatic block data lifecycle management.
 *
 * Registered at MONITOR priority with ignoreCancelled = true to run after
 * other plugins and only when events actually proceed.
 *
 * Fires [BlockDataRemoveEvent] and [BlockDataMoveEvent] before making changes,
 * allowing other plugins to cancel the operation.
 *
 * @param plugin The plugin this listener manages data for
 * @param service The BlockDataService implementation
 */
internal class BlockDataListener(
    private val plugin: Plugin,
    private val service: BlockDataServiceImpl
) : Listener {

    // ==========================================================================
    // Block Break Events
    // ==========================================================================

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onBlockBreak(event: BlockBreakEvent) {
        handleBlockRemoval(event.block, event, BlockDataEvent.Reason.BLOCK_BREAK)
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onBlockPlace(event: BlockPlaceEvent) {
        // Skip if block was modified in the same tick (dirty block tracking)
        if (service.isDirty(event.block, plugin)) return
        handleBlockRemoval(event.block, event, BlockDataEvent.Reason.BLOCK_PLACE)
    }

    // ==========================================================================
    // Explosion Events
    // ==========================================================================

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onBlockExplode(event: BlockExplodeEvent) {
        for (block in event.blockList()) {
            handleBlockRemoval(block, event, BlockDataEvent.Reason.EXPLOSION)
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onEntityExplode(event: EntityExplodeEvent) {
        for (block in event.blockList()) {
            handleBlockRemoval(block, event, BlockDataEvent.Reason.EXPLOSION)
        }
    }

    // ==========================================================================
    // Fire/Burn Events
    // ==========================================================================

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onBlockBurn(event: BlockBurnEvent) {
        handleBlockRemoval(event.block, event, BlockDataEvent.Reason.BURN)
    }

    // ==========================================================================
    // Piston Events
    // ==========================================================================

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onPistonExtend(event: BlockPistonExtendEvent) {
        handlePistonMove(event.blocks, event.direction.direction, event)
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onPistonRetract(event: BlockPistonRetractEvent) {
        handlePistonMove(event.blocks, event.direction.direction, event)
    }

    // ==========================================================================
    // Entity Change Events
    // ==========================================================================

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onEntityChangeBlock(event: EntityChangeBlockEvent) {
        // Only handle if block type actually changes (e.g., enderman picking up block)
        // This aligns with CustomBlockData behavior
        if (event.to != event.block.type) {
            handleBlockRemoval(event.block, event, BlockDataEvent.Reason.ENTITY_CHANGE)
        }
    }

    // ==========================================================================
    // Natural Change Events
    // ==========================================================================

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onBlockFade(event: BlockFadeEvent) {
        // Skip fire blocks - fire fading is not a meaningful block change for data purposes
        // This aligns with CustomBlockData behavior
        if (event.block.type == Material.FIRE) return
        
        // Only handle if block type actually changes
        if (event.newState.type != event.block.type) {
            handleBlockRemoval(event.block, event, BlockDataEvent.Reason.FADE)
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onStructureGrow(event: StructureGrowEvent) {
        for (blockState in event.blocks) {
            handleBlockRemoval(blockState.block, event, BlockDataEvent.Reason.STRUCTURE_GROW)
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onBlockFertilize(event: BlockFertilizeEvent) {
        for (blockState in event.blocks) {
            handleBlockRemoval(blockState.block, event, BlockDataEvent.Reason.FERTILIZE)
        }
    }

    // ==========================================================================
    // Internal Handlers
    // ==========================================================================

    /**
     * Handles block removal by firing BlockDataRemoveEvent and removing data if not cancelled.
     */
    private fun handleBlockRemoval(block: Block, bukkitEvent: Event, reason: BlockDataEvent.Reason) {
        if (!service.hasBlockData(block, plugin)) return
        
        val blockPdc = service.getBlockData(block, plugin)
        
        // Skip protected data
        if (blockPdc.isProtected) return
        
        // Fire custom event
        val removeEvent = BlockDataRemoveEvent(plugin, block, blockPdc, bukkitEvent, reason)
        Bukkit.getPluginManager().callEvent(removeEvent)
        
        // Remove data if not cancelled
        if (!removeEvent.isCancelled) {
            service.removeBlockData(block, plugin)
        }
    }

    /**
     * Handles piston movement by firing BlockDataMoveEvent and moving data if not cancelled.
     * Processes blocks in reverse order to prevent data overwriting.
     * 
     * Blocks with PistonMoveReaction.BREAK are handled as removals rather than moves,
     * aligning with CustomBlockData behavior.
     */
    private fun handlePistonMove(blocks: List<Block>, direction: Vector, bukkitEvent: Event) {
        // Process in reverse order to prevent data overwriting
        for (block in blocks.reversed()) {
            if (!service.hasBlockData(block, plugin)) continue
            
            val blockPdc = service.getBlockData(block, plugin)
            
            // Skip protected data
            if (blockPdc.isProtected) continue
            
            // Check if block breaks when pushed by piston (e.g., torches, flowers)
            // These should be removed rather than moved
            if (block.pistonMoveReaction == PistonMoveReaction.BREAK) {
                handleBlockRemoval(block, bukkitEvent, BlockDataEvent.Reason.PISTON)
                continue
            }
            
            val destination = block.getRelative(
                direction.blockX,
                direction.blockY,
                direction.blockZ
            )
            
            // Fire custom event
            val moveEvent = BlockDataMoveEvent(
                plugin, 
                block, 
                blockPdc, 
                bukkitEvent, 
                BlockDataEvent.Reason.PISTON, 
                destination
            )
            Bukkit.getPluginManager().callEvent(moveEvent)
            
            // Move data if not cancelled
            if (!moveEvent.isCancelled) {
                service.moveBlockData(block, destination, plugin)
            }
        }
    }
}
