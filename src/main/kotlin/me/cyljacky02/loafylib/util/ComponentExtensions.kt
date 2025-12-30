package me.cyljacky02.loafylib.util

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.ComponentLike
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver

/**
 * Disables italic decoration if not explicitly set.
 * Useful for item lore where Minecraft defaults to italic.
 *
 * ```kotlin
 * val lore = Component.text("My lore line").noItalic()
 * ```
 */
fun Component.noItalic(): Component =
    decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE)

/**
 * Parses a MiniMessage string into a Component.
 *
 * ```kotlin
 * val component = "<red>Hello <bold>World</bold>!".mini()
 * ```
 */
fun String.mini(): Component = MiniMessage.miniMessage().deserialize(this)

/**
 * Parses a MiniMessage string with tag resolvers.
 *
 * ```kotlin
 * val component = "<player> joined!".mini("player" to playerName)
 * ```
 */
fun String.mini(vararg placeholders: Pair<String, Any>): Component {
    val resolvers = placeholders.map { (key, value) ->
        when (value) {
            is ComponentLike -> Placeholder.component(key, value)
            else -> Placeholder.unparsed(key, value.toString())
        }
    }.toTypedArray()
    return MiniMessage.miniMessage().deserialize(this, *resolvers)
}

/**
 * Parses a MiniMessage string with tag resolvers.
 *
 * ```kotlin
 * val component = "<player> joined!".mini(Placeholder.unparsed("player", name))
 * ```
 */
fun String.mini(vararg resolvers: TagResolver): Component =
    MiniMessage.miniMessage().deserialize(this, *resolvers)

/**
 * Creates a component placeholder for MiniMessage.
 *
 * ```kotlin
 * val resolver = "player".placeholder(playerComponent)
 * ```
 */
fun String.placeholder(value: ComponentLike): TagResolver.Single =
    Placeholder.component(this, value)

/**
 * Creates an unparsed placeholder for MiniMessage.
 *
 * ```kotlin
 * val resolver = "count".placeholder(42)
 * ```
 */
fun String.placeholder(value: Any): TagResolver.Single =
    Placeholder.unparsed(this, value.toString())
