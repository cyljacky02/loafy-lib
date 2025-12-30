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
import org.bukkit.persistence.PersistentDataContainer
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.Plugin
import java.util.UUID

// ============================================================================
// Item Key Identification
// ============================================================================

/** PDC key used for item identification */
private val ITEM_KEY = NamespacedKey("loafylib", "item_key")

/**
 * Marks this item with a unique identifier key.
 * Uses PersistentDataContainer for reliable identification across server restarts.
 *
 * ```kotlin
 * val wand = ItemStack(Material.STICK).edit {
 *     name("<gold>Magic Wand</gold>".mini())
 * }.markAs(plugin, "magic_wand")
 *
 * // Later, check if an item is this wand:
 * if (item.hasItemKey(NamespacedKey(plugin, "magic_wand"))) { ... }
 * ```
 */
fun ItemStack.markAs(plugin: Plugin, key: String): ItemStack {
    return markAs(NamespacedKey(plugin, key))
}

/**
 * Marks this item with a NamespacedKey identifier.
 */
fun ItemStack.markAs(key: NamespacedKey): ItemStack {
    editMeta { meta ->
        meta.persistentDataContainer.setString(ITEM_KEY, key.toString())
    }
    return this
}

/**
 * Gets the item key if this item was marked with [markAs], or null otherwise.
 */
fun ItemStack.getItemKey(): NamespacedKey? {
    val keyString = itemMeta?.persistentDataContainer?.getString(ITEM_KEY) ?: return null
    return NamespacedKey.fromString(keyString)
}

/**
 * Checks if this item has the specified key.
 *
 * ```kotlin
 * val wandKey = NamespacedKey(plugin, "magic_wand")
 * if (event.item?.hasItemKey(wandKey) == true) {
 *     // Handle wand interaction
 * }
 * ```
 */
fun ItemStack.hasItemKey(key: NamespacedKey): Boolean {
    return getItemKey() == key
}

/**
 * Edits the ItemMeta of this ItemStack using a DSL block.
 * Returns the same ItemStack for chaining.
 *
 * ```kotlin
 * val sword = ItemStack(Material.DIAMOND_SWORD).edit {
 *     customName(Component.text("Excalibur").noItalic())
 *     lore(listOf(Component.text("A legendary blade").noItalic()))
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
 * Removes an enchantment from this item.
 */
fun ItemMeta.removeEnchant(enchantment: Enchantment) {
    removeEnchant(enchantment)
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
 * val noGlow = ItemStack(Material.DIAMOND_SWORD).shiny(false) // remove glow from enchanted item
 * ```
 */
fun ItemStack.shiny(glowing: Boolean = true): ItemStack {
    editMeta { meta ->
        meta.setEnchantmentGlintOverride(glowing)
    }
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
 * // or using string
 * val customItem = ItemStack(Material.PAPER).itemModel("myplugin", "custom_wand")
 * ```
 */
fun ItemStack.itemModel(key: NamespacedKey): ItemStack {
    editMeta { it.setItemModel(key) }
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
fun ItemStack.itemModel(namespace: String, key: String): ItemStack {
    return itemModel(NamespacedKey(namespace, key))
}

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

// ============================================================================
// PersistentDataContainer Extensions
// ============================================================================

/**
 * Edits the PersistentDataContainer of this ItemStack using a DSL block.
 * Returns the ItemStack for chaining.
 *
 * ```kotlin
 * val key = NamespacedKey(plugin, "custom_id")
 * val item = ItemStack(Material.DIAMOND).pdc {
 *     set(key, PersistentDataType.STRING, "my_custom_item")
 * }
 * ```
 */
fun ItemStack.pdc(block: PersistentDataContainer.() -> Unit): ItemStack {
    editMeta { meta ->
        meta.persistentDataContainer.block()
    }
    return this
}

/**
 * Edits the PersistentDataContainer of this ItemMeta using a DSL block.
 *
 * ```kotlin
 * itemMeta.pdc {
 *     set(key, PersistentDataType.INTEGER, 42)
 * }
 * ```
 */
fun ItemMeta.pdc(block: PersistentDataContainer.() -> Unit) {
    persistentDataContainer.block()
}

/**
 * Gets a value from the PersistentDataContainer, or null if not present.
 *
 * ```kotlin
 * val value: String? = pdc.getOrNull(key, PersistentDataType.STRING)
 * ```
 */
fun <P : Any, C : Any> PersistentDataContainer.getOrNull(key: NamespacedKey, type: PersistentDataType<P, C>): C? {
    return if (has(key, type)) get(key, type) else null
}

/**
 * Gets a String value from the PersistentDataContainer, or null if not present.
 */
fun PersistentDataContainer.getString(key: NamespacedKey): String? =
    getOrNull(key, PersistentDataType.STRING)

/**
 * Gets an Int value from the PersistentDataContainer, or null if not present.
 */
fun PersistentDataContainer.getInt(key: NamespacedKey): Int? =
    getOrNull(key, PersistentDataType.INTEGER)

/**
 * Gets a Long value from the PersistentDataContainer, or null if not present.
 */
fun PersistentDataContainer.getLong(key: NamespacedKey): Long? =
    getOrNull(key, PersistentDataType.LONG)

/**
 * Gets a Double value from the PersistentDataContainer, or null if not present.
 */
fun PersistentDataContainer.getDouble(key: NamespacedKey): Double? =
    getOrNull(key, PersistentDataType.DOUBLE)

/**
 * Gets a Boolean value from the PersistentDataContainer, or null if not present.
 */
fun PersistentDataContainer.getBoolean(key: NamespacedKey): Boolean? =
    getOrNull(key, PersistentDataType.BOOLEAN)

/**
 * Gets a ByteArray value from the PersistentDataContainer, or null if not present.
 */
fun PersistentDataContainer.getByteArray(key: NamespacedKey): ByteArray? =
    getOrNull(key, PersistentDataType.BYTE_ARRAY)

/**
 * Sets a String value in the PersistentDataContainer.
 */
fun PersistentDataContainer.setString(key: NamespacedKey, value: String) {
    set(key, PersistentDataType.STRING, value)
}

/**
 * Sets an Int value in the PersistentDataContainer.
 */
fun PersistentDataContainer.setInt(key: NamespacedKey, value: Int) {
    set(key, PersistentDataType.INTEGER, value)
}

/**
 * Sets a Long value in the PersistentDataContainer.
 */
fun PersistentDataContainer.setLong(key: NamespacedKey, value: Long) {
    set(key, PersistentDataType.LONG, value)
}

/**
 * Sets a Double value in the PersistentDataContainer.
 */
fun PersistentDataContainer.setDouble(key: NamespacedKey, value: Double) {
    set(key, PersistentDataType.DOUBLE, value)
}

/**
 * Sets a Boolean value in the PersistentDataContainer.
 */
fun PersistentDataContainer.setBoolean(key: NamespacedKey, value: Boolean) {
    set(key, PersistentDataType.BOOLEAN, value)
}

/**
 * Sets a ByteArray value in the PersistentDataContainer.
 */
fun PersistentDataContainer.setByteArray(key: NamespacedKey, value: ByteArray) {
    set(key, PersistentDataType.BYTE_ARRAY, value)
}
