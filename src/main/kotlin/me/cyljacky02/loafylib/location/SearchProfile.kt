package me.cyljacky02.loafylib.location

import org.bukkit.World

/**
 * Search profile for vertical location scanning strategy.
 *
 * Different environments benefit from different search strategies:
 * - **Overworld**: Top-down or alternating works well (open sky above)
 * - **Nether**: Middle-out is optimal (cave environment with bedrock ceiling)
 * - **End**: Alternating works well (floating islands)
 *
 * ## Performance Considerations
 *
 * The search profile affects how quickly a safe location is found:
 * - `ALTERNATING`: Best for unknown environments, checks closest Y first
 * - `MIDDLE_OUT`: Best for cave environments like Nether, starts from middle of Y range
 * - `AUTO`: Automatically selects optimal strategy based on world environment
 *
 * ## Nether-Specific Behavior
 *
 * When `AUTO` detects a Nether world (`World.Environment.NETHER`):
 * - Uses `MIDDLE_OUT` search strategy
 * - Caps maximum Y at 126 to avoid bedrock ceiling (Y=127)
 * - Starts search from Y=64 (middle of safe zone)
 *
 * @see SafeLocationSearch for usage
 */
enum class SearchProfile {
    /**
     * Alternating search: checks origin Y first, then alternates up/down.
     * Sequence: 0, +1, -1, +2, -2, +3, -3, ...
     *
     * Best for: Unknown environments, when origin Y is likely safe.
     */
    ALTERNATING,

    /**
     * Middle-out search: starts from middle of Y range, alternates up/down.
     * Optimal for cave environments where safe spots are scattered vertically.
     *
     * Best for: Nether, underground areas, cave systems.
     */
    MIDDLE_OUT,

    /**
     * Automatic profile selection based on world environment.
     * - NETHER → MIDDLE_OUT with Y cap at 126
     * - Other environments → ALTERNATING
     *
     * Recommended for most use cases.
     */
    AUTO;

    companion object {
        /** Nether bedrock ceiling Y level (bedrock is at Y=127) */
        const val NETHER_CEILING_Y = 126

        /** Nether safe zone middle Y level */
        const val NETHER_MIDDLE_Y = 64

        /** Nether minimum safe Y level (above bedrock floor) */
        const val NETHER_FLOOR_Y = 1

        /**
         * Resolves the effective search profile for a given world.
         *
         * @param profile The requested search profile
         * @param world The world to search in (nullable for safety)
         * @return The resolved profile (AUTO becomes ALTERNATING or MIDDLE_OUT)
         */
        @JvmStatic
        fun resolve(profile: SearchProfile, world: World?): SearchProfile {
            if (profile != AUTO) return profile
            if (world == null) return ALTERNATING

            return when (world.environment) {
                World.Environment.NETHER -> MIDDLE_OUT
                else -> ALTERNATING
            }
        }

        /**
         * Gets the effective maximum Y for searching in a world.
         * For Nether worlds, caps at [NETHER_CEILING_Y] to avoid bedrock ceiling.
         *
         * @param world The world to search in
         * @param profile The search profile being used
         * @return The effective maximum Y coordinate
         */
        @JvmStatic
        fun getEffectiveMaxY(world: World, profile: SearchProfile): Int {
            val resolvedProfile = resolve(profile, world)
            val worldMaxY = world.maxHeight - 1

            return if (resolvedProfile == MIDDLE_OUT && world.environment == World.Environment.NETHER) {
                minOf(worldMaxY, NETHER_CEILING_Y)
            } else {
                worldMaxY
            }
        }

        /**
         * Gets the starting Y coordinate for middle-out search.
         *
         * @param world The world to search in
         * @param originY The original Y coordinate from the search origin
         * @return The Y coordinate to start searching from
         */
        @JvmStatic
        fun getMiddleOutStartY(world: World, originY: Int): Int {
            return if (world.environment == World.Environment.NETHER) {
                // For Nether, start from middle of safe zone (Y=64)
                NETHER_MIDDLE_Y
            } else {
                // For other worlds, use origin Y as starting point
                originY
            }
        }
    }
}
