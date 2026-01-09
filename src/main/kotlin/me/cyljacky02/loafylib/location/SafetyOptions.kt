package me.cyljacky02.loafylib.location

/**
 * Configuration options for safe location detection.
 *
 * This immutable data class allows customizing safety check behavior
 * for different use cases like bulk RTP generation or aquatic teleports.
 *
 * @property checkCollision Enable/disable VoxelShape collision checking for partial blocks
 * @property allowWater Treat water blocks as passable (for aquatic teleports)
 * @property checkWorldBorder Enable/disable world border boundary checking
 * @property checkYBounds Enable/disable Y-level bounds checking (minHeight to maxHeight-1)
 * @property checkEntities Enable/disable entity collision checking (boats, players, armor stands)
 */
data class SafetyOptions(
    val checkCollision: Boolean = true,
    val allowWater: Boolean = false,
    val checkWorldBorder: Boolean = true,
    val checkYBounds: Boolean = true,
    val checkEntities: Boolean = false
) {
    companion object {
        /**
         * Default preset with block checks enabled and entity checks disabled.
         * Suitable for most teleportation scenarios.
         */
        val DEFAULT = SafetyOptions()

        /**
         * Fast preset with collision checking disabled for bulk operations.
         * Use when performance is critical and partial block collision is acceptable.
         */
        val FAST = SafetyOptions(checkCollision = false)

        /**
         * Aquatic preset with water allowance enabled.
         * Use for underwater teleportation or aquatic-themed features.
         */
        val AQUATIC = SafetyOptions(allowWater = true)

        /**
         * Strict preset with all checks enabled including entity collision.
         * Use when maximum safety is required (e.g., spawn points).
         */
        val STRICT = SafetyOptions(checkEntities = true)
    }
}
