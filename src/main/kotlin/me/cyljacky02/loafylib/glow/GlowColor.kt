package me.cyljacky02.loafylib.glow

import net.kyori.adventure.text.format.NamedTextColor

/**
 * Glow colors available for existing entity glowing.
 *
 * These correspond to the 16 Minecraft team colors (NamedTextColor).
 * For custom RGB colors, use the Display entity methods instead.
 *
 * @property namedTextColor the underlying Adventure NamedTextColor
 */
enum class GlowColor(val namedTextColor: NamedTextColor) {
    BLACK(NamedTextColor.BLACK),
    DARK_BLUE(NamedTextColor.DARK_BLUE),
    DARK_GREEN(NamedTextColor.DARK_GREEN),
    DARK_AQUA(NamedTextColor.DARK_AQUA),
    DARK_RED(NamedTextColor.DARK_RED),
    DARK_PURPLE(NamedTextColor.DARK_PURPLE),
    GOLD(NamedTextColor.GOLD),
    GRAY(NamedTextColor.GRAY),
    DARK_GRAY(NamedTextColor.DARK_GRAY),
    BLUE(NamedTextColor.BLUE),
    GREEN(NamedTextColor.GREEN),
    AQUA(NamedTextColor.AQUA),
    RED(NamedTextColor.RED),
    LIGHT_PURPLE(NamedTextColor.LIGHT_PURPLE),
    YELLOW(NamedTextColor.YELLOW),
    WHITE(NamedTextColor.WHITE);

    companion object {
        /**
         * Gets the GlowColor from a NamedTextColor.
         *
         * @param color the NamedTextColor to convert
         * @return the corresponding GlowColor, or WHITE if not found
         */
        fun fromNamedTextColor(color: NamedTextColor): GlowColor {
            return entries.find { it.namedTextColor == color } ?: WHITE
        }
    }
}
