package me.cyljacky02.loafylib.util

import com.destroystokyo.paper.profile.ProfileProperty
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.OfflinePlayer
import org.bukkit.enchantments.Enchantment
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.inventory.meta.SkullMeta
import java.util.UUID

// ============================================================================
// ItemStack & ItemMeta Utility Extensions
// ============================================================================
// Pure item utilities: editing, display, enchantments, flags, models.
// For PDC operations, see me.cyljacky02.loafylib.pdc package.
// ============================================================================

/**
 * Edits the ItemMeta of this ItemStack using a DSL block.
 * Returns the same ItemStack for chaining.
 *
 * ```kotlin
 * val sword = ItemStack(Material.DIAMOND_SWORD).edit {
 *     name(Component.text("Excalibur"))
 *     loreLines(Component.text("A legendary blade"))
 * }
 * ```
 */
fun ItemStack.edit(block: ItemMeta.() -> Unit): ItemStack {
    editMeta(block)
    return this
}

/**
 * Edits the ItemMeta with a specific type using a DSL block.
 * Returns the same ItemStack for chaining.
 *
 * ```kotlin
 * val head = ItemStack(Material.PLAYER_HEAD).editTyped<SkullMeta> {
 *     owningPlayer = player
 * }
 * ```
 */
inline fun <reified M : ItemMeta> ItemStack.editTyped(crossinline block: M.() -> Unit): ItemStack {
    editMeta(M::class.java) { it.block() }
    return this
}

// ============================================================================
// Display Name & Lore
// ============================================================================

/**
 * Sets the custom name with automatic italic disabled.
 *
 * ```kotlin
 * itemMeta.name(Component.text("My Item"))
 * // or with MiniMessage
 * itemMeta.name("<red>My Item".mini())
 * ```
 */
fun ItemMeta.name(name: Component) {
    customName(name.noItalic())
}

/**
 * Sets the lore with automatic italic disabled on each line.
 *
 * ```kotlin
 * itemMeta.loreLines(
 *     Component.text("Line 1"),
 *     Component.text("Line 2")
 * )
 * ```
 */
fun ItemMeta.loreLines(vararg lines: Component) {
    lore(lines.map { it.noItalic() })
}

/**
 * Sets the lore from a list with automatic italic disabled.
 */
fun ItemMeta.loreLines(lines: List<Component>) {
    lore(lines.map { it.noItalic() })
}

// ============================================================================
// Player Heads
// ============================================================================

/**
 * Creates a player head ItemStack from a base64 skin texture.
 *
 * ```kotlin
 * val head = playerHead("eyJ0ZXh0dXJlcyI6ey...")
 * ```
 */
fun playerHead(base64: String): ItemStack {
    val stack = ItemStack(Material.PLAYER_HEAD)
    stack.editMeta(SkullMeta::class.java) { meta ->
        val hashId = UUID(base64.hashCode().toLong(), base64.hashCode().toLong())
        val profile = Bukkit.createProfile(hashId, "")
        profile.setProperty(ProfileProperty("textures", base64))
        meta.playerProfile = profile
    }
    return stack
}

/**
 * Creates a player head ItemStack from an OfflinePlayer.
 *
 * ```kotlin
 * val head = playerHead(player)
 * ```
 */
fun playerHead(player: OfflinePlayer): ItemStack {
    val stack = ItemStack(Material.PLAYER_HEAD)
    stack.editMeta(SkullMeta::class.java) { meta ->
        meta.playerProfile = player.playerProfile
    }
    return stack
}

/**
 * Creates a player head ItemStack from a UUID.
 * Note: This may trigger a network request to fetch the profile.
 *
 * ```kotlin
 * val head = playerHead(uuid)
 * ```
 */
fun playerHead(uuid: UUID): ItemStack {
    val stack = ItemStack(Material.PLAYER_HEAD)
    stack.editMeta(SkullMeta::class.java) { meta ->
        meta.playerProfile = Bukkit.createProfile(uuid)
    }
    return stack
}

// ============================================================================
// Amount & Chaining
// ============================================================================

/**
 * Sets the amount and returns the ItemStack for chaining.
 *
 * ```kotlin
 * val stack = ItemStack(Material.DIAMOND).amount(64)
 * ```
 */
fun ItemStack.amount(amount: Int): ItemStack {
    this.amount = amount
    return this
}

// ============================================================================
// Enchantment Helpers
// ============================================================================

/**
 * Adds an enchantment to this item, ignoring level restrictions.
 *
 * ```kotlin
 * itemMeta.enchant(Enchantment.SHARPNESS, 5)
 * itemMeta.enchant(Enchantment.UNBREAKING) // level 1 by default
 * ```
 */
fun ItemMeta.enchant(enchantment: Enchantment, level: Int = 1) {
    addEnchant(enchantment, level, true)
}

/**
 * Removes all enchantments from this item.
 */
fun ItemMeta.clearEnchants() {
    enchants.keys.toList().forEach { removeEnchant(it) }
}

// ============================================================================
// ItemFlag Helpers
// ============================================================================

/**
 * Adds multiple ItemFlags at once.
 *
 * ```kotlin
 * itemMeta.flags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES)
 * ```
 */
fun ItemMeta.flags(vararg flags: ItemFlag) {
    addItemFlags(*flags)
}

/**
 * Hides all item attributes (enchants, attributes, unbreakable, etc.).
 * Useful for clean GUI items.
 *
 * ```kotlin
 * itemMeta.hideAll()
 * ```
 */
fun ItemMeta.hideAll() {
    addItemFlags(*ItemFlag.entries.toTypedArray())
}

/**
 * Makes this item appear shiny (enchanted glow) without showing enchantments.
 * Uses the modern enchantment_glint_override API (Paper 1.21+).
 * Returns the ItemStack for chaining.
 *
 * ```kotlin
 * val glowingItem = ItemStack(Material.PAPER).shiny()
 * val noGlow = ItemStack(Material.DIAMOND_SWORD).shiny(false)
 * ```
 */
fun ItemStack.shiny(glowing: Boolean = true): ItemStack {
    editMeta(ItemMeta::class.java) { it.setEnchantmentGlintOverride(glowing) }
    return this
}

/**
 * Sets the item as unbreakable.
 *
 * ```kotlin
 * itemMeta.unbreakable()
 * itemMeta.unbreakable(false) // make breakable again
 * ```
 */
fun ItemMeta.unbreakable(unbreakable: Boolean = true) {
    isUnbreakable = unbreakable
}

// ============================================================================
// Item Model (Custom Textures)
// ============================================================================

/**
 * Sets the item model directly using a NamespacedKey.
 * This is the modern way to specify custom item models in resource packs.
 * Returns the ItemStack for chaining.
 *
 * ```kotlin
 * val customItem = ItemStack(Material.PAPER).itemModel(NamespacedKey(plugin, "custom_wand"))
 * ```
 */
fun ItemStack.itemModel(key: NamespacedKey): ItemStack {
    editMeta(ItemMeta::class.java) { it.setItemModel(key) }
    return this
}

/**
 * Sets the item model directly using namespace and key strings.
 * Returns the ItemStack for chaining.
 *
 * ```kotlin
 * val customItem = ItemStack(Material.PAPER).itemModel("myplugin", "custom_wand")
 * ```
 */
fun ItemStack.itemModel(namespace: String, key: String): ItemStack =
    itemModel(NamespacedKey(namespace, key))

/**
 * Sets the item model on this ItemMeta.
 *
 * ```kotlin
 * itemMeta.itemModel(NamespacedKey(plugin, "custom_wand"))
 * ```
 */
fun ItemMeta.itemModel(key: NamespacedKey) {
    setItemModel(key)
}

/**
 * Sets the item model on this ItemMeta using namespace and key strings.
 *
 * ```kotlin
 * itemMeta.itemModel("myplugin", "custom_wand")
 * ```
 */
fun ItemMeta.itemModel(namespace: String, key: String) {
    setItemModel(NamespacedKey(namespace, key))
}
