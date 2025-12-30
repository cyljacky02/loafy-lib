package me.cyljacky02.loafylib.blockdata

import org.bukkit.block.Block
import org.bukkit.event.Cancellable
import org.bukkit.event.Event
import org.bukkit.event.HandlerList
import org.bukkit.plugin.Plugin
import org.jspecify.annotations.NullMarked

/**
 * Base event for block data lifecycle changes.
 *
 * Fired at MONITOR priority of the underlying Bukkit event, allowing plugins
 * to cancel automatic data removal or movement.
 *
 * Plugins can listen to this base class to receive all block data events,
 * or listen to specific subclasses ([BlockDataRemoveEvent], [BlockDataMoveEvent])
 * for more targeted handling.
 *
 * @property plugin The plugin owning the block data
 * @property block The block whose data is being affected
 * @property blockData The BlockPDC containing the data
 * @property bukkitEvent The underlying Bukkit event that triggered this change
 * @property reason The reason for the data change
 */
@NullMarked
open class BlockDataEvent(
    val plugin: Plugin,
    val block: Block,
    val blockData: BlockPDC,
    val bukkitEvent: Event,
    val reason: Reason
) : Event(), Cancellable {

    private var cancelled = false

    override fun isCancelled(): Boolean = cancelled

    override fun setCancelled(cancel: Boolean) {
        cancelled = cancel
    }

    override fun getHandlers(): HandlerList = HANDLER_LIST

    /**
     * Reasons for block data lifecycle changes.
     */
    enum class Reason {
        /** Block was broken by a player */
        BLOCK_BREAK,
        /** Block was replaced by BlockPlaceEvent */
        BLOCK_PLACE,
        /** Block was destroyed by explosion */
        EXPLOSION,
        /** Block was pushed/pulled by piston */
        PISTON,
        /** Block was burned */
        BURN,
        /** Block was changed by an entity (e.g., Enderman, Silverfish) */
        ENTITY_CHANGE,
        /** Block faded (e.g., ice melting, coral dying) */
        FADE,
        /** Block was replaced by structure growth (e.g., tree, mushroom) */
        STRUCTURE_GROW,
        /** Block was affected by bone meal fertilization */
        FERTILIZE,
        /** Unknown or unspecified reason */
        UNKNOWN
    }

    companion object {
        @JvmStatic
        private val HANDLER_LIST = HandlerList()

        @JvmStatic
        fun getHandlerList(): HandlerList = HANDLER_LIST
    }
}

/**
 * Fired when block data is about to be removed.
 *
 * Cancel this event to prevent the data from being removed.
 *
 * @see BlockDataEvent
 */
@NullMarked
class BlockDataRemoveEvent(
    plugin: Plugin,
    block: Block,
    blockData: BlockPDC,
    bukkitEvent: Event,
    reason: Reason
) : BlockDataEvent(plugin, block, blockData, bukkitEvent, reason) {

    override fun getHandlers(): HandlerList = HANDLER_LIST

    companion object {
        @JvmStatic
        private val HANDLER_LIST = HandlerList()

        @JvmStatic
        fun getHandlerList(): HandlerList = HANDLER_LIST
    }
}

/**
 * Fired when block data is about to be moved (e.g., by piston).
 *
 * Cancel this event to prevent the data from being moved.
 *
 * @property destinationBlock The block the data will be moved to
 * @see BlockDataEvent
 */
@NullMarked
class BlockDataMoveEvent(
    plugin: Plugin,
    block: Block,
    blockData: BlockPDC,
    bukkitEvent: Event,
    reason: Reason,
    val destinationBlock: Block
) : BlockDataEvent(plugin, block, blockData, bukkitEvent, reason) {

    override fun getHandlers(): HandlerList = HANDLER_LIST

    companion object {
        @JvmStatic
        private val HANDLER_LIST = HandlerList()

        @JvmStatic
        fun getHandlerList(): HandlerList = HANDLER_LIST
    }
}
