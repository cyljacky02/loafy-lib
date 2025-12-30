package me.cyljacky02.loafylib.blockdata

import org.bukkit.NamespacedKey

/**
 * Internal NamespacedKeys used by LoafyLib's custom block data system.
 *
 * These keys are reserved for internal use and should not be used by dependent plugins.
 * The namespace `loafylib_cbd` (LoafyLib Custom Block Data) clearly identifies
 * these as LoafyLib internal keys.
 */
internal object BlockDataKeys {
    
    /**
     * Key for storing protection status on block data.
     *
     * Protected blocks won't have their data automatically removed or moved
     * when blocks are broken, exploded, or pushed by pistons.
     *
     * Stored as a BYTE value: 1 = protected, absent = not protected.
     */
    val PROTECTED: NamespacedKey = NamespacedKey("loafylib_cbd", "protected")
}
