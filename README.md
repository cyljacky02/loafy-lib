# LoafyLib

A shared library plugin for Paper/Folia servers providing common infrastructure for Loafy plugins.

## Quick Start

```kotlin
// 1. Extend LoafyPlugin
class MyPlugin : LoafyPlugin() {
    override fun components() = listOf(
        LettuceRedisManager(config.redis, logger),
        HikariDatabaseManager(config.database, logger)
    )
    
    override fun onPluginEnable() {
        // Components are initialized - ready to use
    }
}

// 2. Use coroutines anywhere
pluginScope.launch {
    val data = redis.execute { get("key") }
    player.sendMessage("Data: $data") // Safe from async thread
}
```

## Features

- **Folia-Compatible Coroutine Dispatchers** - Paper scheduler integration for async, main, entity, and region threads
- **Redis Management** - Lettuce-based async Redis with pub/sub, pipelining, and Lua scripts
- **Database Management** - HikariCP connection pooling with retry logic for MariaDB
- **Configuration Utilities** - Configurate YAML helpers with type-safe serialization
- **Component Lifecycle** - Dependency-ordered initialization and shutdown with automatic listener registration
- **Suspend Event Handlers** - Use suspend functions in `@EventHandler` methods
- **Per-Player Glowing Entities** - PacketEvents-based glowing with team colors (16), RGB Display entities (unlimited), or invisible Shulker markers (pure glow outline)
- **Per-Block Persistent Data** - Store custom data on individual blocks using chunk PDC storage
- **Utility Extensions** - Kotlin-idiomatic helpers for ItemStack, Components, and MiniMessage
- **PDC Extensions** - Unified marker system for Items, Entities, and Chunks with type-safe helpers


## Installation

### For Dependent Plugins

Add LoafyLib as a compile-only dependency in your `build.gradle.kts`:

```kotlin
repositories {
    mavenLocal() // or your artifact repository
}

dependencies {
    compileOnly("me.cyljacky02:loafylib:1.0")
    compileOnly("io.papermc.paper:paper-api:1.21.1-R0.1-SNAPSHOT")
}
```

Declare the dependency in your `paper-plugin.yml`:

```yaml
name: MyPlugin
version: '1.0'
main: me.example.myplugin.MyPlugin
api-version: '1.21'
dependencies:
  server:
    LoafyLib:
      load: BEFORE
      required: true
```

## Usage

### LoafyPlugin Base Class

Extend `LoafyPlugin` for automatic component lifecycle management:

```kotlin
class MyPlugin : LoafyPlugin() {

    override fun components(): List<PluginComponent> {
        val config = loadConfig()
        val redisManager = LettuceRedisManager(config.redis, logger)
        val databaseManager = HikariDatabaseManager(config.database, logger)
        val myService = MyServiceImpl(databaseManager, redisManager)
        
        return listOf(redisManager, databaseManager, myService)
    }

    override fun onPluginEnable() {
        val myService = registry.get<MyServiceImpl>()
        // Register commands, etc.
    }
}
```


### Automatic Listener Registration

Components implementing `Listener` are automatically registered as Bukkit event listeners - no annotation needed:

```kotlin
class MyService(private val database: DatabaseManager) : PluginComponent, Listener {
    
    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        // Automatically registered when component initializes!
        event.player.sendMessage("Welcome!")
    }
    
    @EventHandler(priority = EventPriority.MONITOR)
    fun onPlayerQuit(event: PlayerQuitEvent) {
        // Multiple handlers work fine
    }
    
    override suspend fun initialize() {
        // Your initialization logic
    }
    
    override suspend fun shutdown() {
        // Listener is automatically unregistered
    }
}
```

This provides the same convenience as Fairy's `@RegisterAsListener` annotation, but without requiring annotations or reflection-based classpath scanning.

### PaperDispatchers

Folia-compatible coroutine dispatchers:

```kotlin
// Async work (database, HTTP, file I/O)
pluginScope.launch {
    val data = database.fetch(playerId)
    player.sendMessage("Loaded: $data") // Safe from async thread
}

// Player GUI operations (requires entity thread)
pluginScope.launch {
    val items = database.fetchItems(playerId)
    player.withPlayerContext(plugin) {
        items.forEach { player.inventory.addItem(it) }
    }
}

// Block modifications (requires region thread)
pluginScope.launch {
    location.withRegionContext(plugin) {
        location.block.type = Material.STONE
    }
}

// Server-wide operations (requires global region thread)
pluginScope.launch {
    plugin.withMainContext {
        Bukkit.broadcastMessage("Server announcement!")
    }
}
```


### BlockDataService

Per-block persistent data storage using chunk PDCs. Data is automatically persisted without external files or databases.

```kotlin
val blockDataService = registry.get<BlockDataService>()

// Store data on a block
val blockPdc = blockDataService.getBlockData(block, plugin)
blockPdc.set(myKey, PersistentDataType.STRING, "custom value")
blockPdc.set(countKey, PersistentDataType.INTEGER, 42)

// Check if block has data
if (blockDataService.hasBlockData(block, plugin)) {
    val data = blockDataService.getBlockData(block, plugin)
    val value = data.get(myKey, PersistentDataType.STRING)
}

// Get all blocks with data in a chunk
val blocksWithData = blockDataService.getBlocksWithData(chunk, plugin)

// Protect data from automatic removal (break, explode, piston)
blockPdc.isProtected = true

// Copy data to another block
blockPdc.copyTo(destinationBlock)

// Clear all data from a block
blockPdc.clear()
```

#### Block Extension Functions

Kotlin-idiomatic extensions for common operations:

```kotlin
import me.cyljacky02.loafylib.blockdata.*

// Get block data directly from Block
val blockPdc = block.getBlockData(plugin)

// Check existence without creating BlockPDC
if (block.hasBlockData(plugin)) {
    // Process data...
}

// Clear all data
block.clearBlockData(plugin)

// Check protection status
if (block.isBlockDataProtected(plugin)) {
    // Data won't be auto-removed on block break
}
```


#### BlockPDC DSL

Edit multiple values with a clean DSL:

```kotlin
val blockPdc = block.getBlockData(plugin).edit {
    setString(nameKey, "Custom Block")
    setInt(levelKey, 5)
    setBoolean(activeKey, true)
    isProtected = true
}
```

#### Type-Safe Getters/Setters

Convenience methods for common data types:

```kotlin
// Setters
blockPdc.setString(key, "value")
blockPdc.setInt(key, 42)
blockPdc.setLong(key, 123456789L)
blockPdc.setDouble(key, 3.14)
blockPdc.setBoolean(key, true)
blockPdc.setByteArray(key, byteArrayOf(1, 2, 3))
blockPdc.setIntArray(key, intArrayOf(1, 2, 3))
blockPdc.setLongArray(key, longArrayOf(1L, 2L, 3L))

// Getters (return null if not present)
val str: String? = blockPdc.getString(key)
val num: Int? = blockPdc.getInt(key)
val lng: Long? = blockPdc.getLong(key)
val dbl: Double? = blockPdc.getDouble(key)
val bool: Boolean? = blockPdc.getBoolean(key)
val bytes: ByteArray? = blockPdc.getByteArray(key)
val ints: IntArray? = blockPdc.getIntArray(key)
val longs: LongArray? = blockPdc.getLongArray(key)
```

#### Automatic Lifecycle Management

Register a listener to automatically handle block data when blocks are modified:

```kotlin
// Enable automatic lifecycle management for your plugin
blockDataService.registerListener(plugin)
```

When registered, block data is automatically:
- **Removed** when blocks are broken, exploded, burned, or changed
- **Moved** when blocks are pushed by pistons

Protected data (`isProtected = true`) is excluded from automatic management.

Custom events are fired before changes, allowing cancellation:

```kotlin
@EventHandler
fun onBlockDataRemove(event: BlockDataRemoveEvent) {
    if (event.reason == BlockDataEvent.Reason.EXPLOSION) {
        event.isCancelled = true // Prevent removal from explosions
    }
}

@EventHandler
fun onBlockDataMove(event: BlockDataMoveEvent) {
    logger.info("Data moving from ${event.block} to ${event.destinationBlock}")
}
```


### RedisManager

Async Redis operations with Lettuce:

```kotlin
class MyComponent(private val redis: RedisManager) : PluginComponent {
    
    override suspend fun initialize() {
        redis.connect()
        
        // Register reconnect callback (MUST close handle to prevent memory leaks)
        reconnectHandle = redis.onReconnect {
            logger.info("Redis reconnected, restoring state...")
        }
    }
    
    suspend fun cachePlayer(uuid: UUID, data: ByteArray) {
        redis.execute { setex("player:$uuid", 3600, data) }
    }
    
    suspend fun publishEvent(channel: String, message: ByteArray) {
        redis.publish(channel, message)
    }
    
    suspend fun subscribeToEvents() {
        redis.subscribe("events") { message ->
            // Handle incoming message
        }
    }
    
    override suspend fun shutdown() {
        reconnectHandle?.close() // Prevent memory leaks
        redis.shutdown()
    }
}
```

#### Reconnect Callback Memory Leak Prevention

The `onReconnect()` method returns an `AutoCloseable` handle that **MUST** be closed when no longer needed:

```kotlin
class MyPlugin : LoafyPlugin() {
    private var reconnectHandle: AutoCloseable? = null

    override fun onPluginEnable() {
        val redis = registry.get<RedisManager>()

        // Register reconnect callback and store the handle
        reconnectHandle = redis.onReconnect {
            // Re-register all online players after Redis reconnects
            server.onlinePlayers.forEach { player ->
                sessionService.register(player.uniqueId, player.name)
            }
        }
    }

    override fun onPluginDisable() {
        // CRITICAL: Close the handle to prevent memory leaks
        reconnectHandle?.close()
        reconnectHandle = null
    }
}
```

#### Lua Script Execution

Execute atomic Lua scripts with configurable output types:

```kotlin
// CAS (Compare-And-Swap) script returning INTEGER
val success = redis.evalScript(
    script = """
        if redis.call('GET', KEYS[1]) == ARGV[1] then
            redis.call('SET', KEYS[1], ARGV[2])
            return 1
        end
        return 0
    """,
    keys = listOf("my:key"),
    args = listOf(oldValue, newValue),
    outputType = ScriptOutputType.INTEGER  // Avoid type coercion
) { result -> (result as Long) == 1L }

// Script returning array (default MULTI)
val results = redis.evalScript(
    script = "return {KEYS[1], ARGV[1]}",
    keys = listOf("key1"),
    args = listOf(value)
) { result -> result as List<*> }
```

#### Pipeline Operations

Batch multiple commands for efficiency:

```kotlin
redis.pipeline {
    // String operations
    set("key1", value1)
    setex("key2", 3600, value2)
    setnx("key3", value3)  // Set if not exists
    
    // Sorted set operations (leaderboards, queues)
    zadd("leaderboard", 100.0, playerId)
    zrank("leaderboard", playerId)      // Get rank (lowest first)
    zrevrank("leaderboard", playerId)   // Get rank (highest first)
    zscore("leaderboard", playerId)     // Get score
    zrem("leaderboard", playerId)       // Remove member
    zcard("leaderboard")                // Get count
    
    // For commands not in the interface, use add {}
    add { zincrby("leaderboard", 10.0, playerId) }
}
```

### DatabaseManager

HikariCP with exponential backoff retry:

```kotlin
class MyRepository(private val db: DatabaseManager) {
    
    suspend fun fetchPlayer(uuid: UUID): PlayerData? {
        return db.withRetry {
            db.getConnection().use { conn ->
                conn.prepareStatement("SELECT * FROM players WHERE uuid = ?").use { stmt ->
                    stmt.setString(1, uuid.toString())
                    stmt.executeQuery().use { rs ->
                        if (rs.next()) PlayerData.from(rs) else null
                    }
                }
            }
        }
    }
}
```


### ConfigurateUtils

Type-safe YAML configuration:

```kotlin
@ConfigSerializable
data class MyConfig(
    val redis: RedisConfig = RedisConfig(),
    val database: DatabaseConfig = DatabaseConfig(),
    val customSetting: String = "default"
)

// Load configuration
val loader = ConfigurateUtils.createYamlLoader(
    path = plugin.ensureDataFolder().resolve("config.yml"),
    header = "My Plugin Configuration"
)
val config = loader.loadAndSaveDefaults(MyConfig())
```

### Suspend Event Handlers

Use suspend functions in event handlers:

```kotlin
class MyListener(private val database: DatabaseManager) : Listener {
    
    @EventHandler
    suspend fun onPlayerJoin(event: PlayerJoinEvent) {
        // Async database call - no blocking!
        val stats = database.fetchStats(event.player.uniqueId)
        event.player.sendMessage("Welcome! Score: ${stats.score}")
    }
    
    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        // Regular handlers work too - no overhead
        event.quitMessage(null)
    }
}

// Registration
server.pluginManager.registerSuspendingEvents(MyListener(db), plugin, pluginScope)
```


### Utility Extensions

Kotlin-idiomatic helpers for common tasks:

```kotlin
import me.cyljacky02.loafylib.util.*

// ItemStack editing with DSL
val sword = ItemStack(Material.DIAMOND_SWORD).edit {
    name("<gradient:gold:yellow>Excalibur</gradient>".mini())
    loreLines(
        "<gray>A legendary blade".mini(),
        "<gray>Damage: <red>+15".mini()
    )
    enchant(Enchantment.SHARPNESS, 5)
    unbreakable()
    hideAll() // Hide all item flags
}

// Typed meta editing
val head = ItemStack(Material.PLAYER_HEAD).editTyped<SkullMeta> {
    owningPlayer = player
}

// Player heads from base64 texture, player, or UUID
val customHead = playerHead("eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA...")
val playerHead = playerHead(player)
val uuidHead = playerHead(uuid)

// Chaining helpers
val stack = ItemStack(Material.DIAMOND)
    .amount(64)
    .shiny()  // Enchantment glint without enchantments
    .itemModel(plugin, "custom_diamond")  // Custom model (1.21+)

// PersistentDataContainer DSL (see PDC Extensions section for full API)
val item = ItemStack(Material.PAPER).pdc {
    setString(myKey, "custom_value")
    setInt(countKey, 42)
}

// Read PDC values
val value = item.persistentDataContainer.getString(myKey)

// Component utilities
val component = "<red>Hello <player>!".mini("player" to playerName)
val noItalic = Component.text("Lore line").noItalic()

// MiniMessage placeholders
val resolver = "count".placeholder(42)
val componentResolver = "player".placeholder(playerComponent)
```


### PDC Extensions (Items, Entities, Chunks)

Unified PDC (PersistentDataContainer) extensions with a consistent marker system:

```kotlin
import me.cyljacky02.loafylib.pdc.*

// === Item Markers ===
val wandKey = NamespacedKey(plugin, "magic_wand")
val wand = ItemStack(Material.STICK).edit {
    name("<gradient:gold:yellow>Magic Wand</gradient>".mini())
}.markAs(wandKey)

// Check items
if (item.hasItemKey(wandKey)) { /* handle wand */ }
item.unmarkAs(wandKey)  // Remove marker

// === Entity Markers ===
val bossKey = NamespacedKey(plugin, "boss_mob")
entity.markAs(bossKey)
if (entity.hasEntityKey(bossKey)) { /* handle boss */ }

// === Chunk Markers ===
val claimedKey = NamespacedKey(plugin, "claimed")
chunk.markAs(claimedKey)
if (chunk.hasChunkKey(claimedKey)) { /* handle claimed chunk */ }

// === Match Multiple Keys ===
when (item.matchesAnyKey(wandKey, swordKey, bowKey)) {
    wandKey -> handleWand()
    swordKey -> handleSword()
    null -> { /* not a custom item */ }
}

// === PDC DSL (works on Item, Entity, Chunk) ===
item.pdc {
    setString(myKey, "custom_value")
    setInt(countKey, 42)
}

entity.pdc {
    setDouble(healthKey, 100.0)
    setInt(levelKey, 5)
}

chunk.pdc {
    setString(ownerKey, playerUuid.toString())
    setLong(claimedAtKey, System.currentTimeMillis())
}
```

#### Event Extensions

```kotlin
import me.cyljacky02.loafylib.event.*

@EventHandler
suspend fun onInteract(event: PlayerInteractEvent) {
    if (!event.matchesItem(wandKey)) return
    // Handle wand interaction...
}

@EventHandler
fun onPlace(event: BlockPlaceEvent) {
    if (event.matchesItem(customBlockKey)) {
        // Handle custom block placement
    }
}
```

### Player Cooldowns

Per-player cooldown management with automatic cleanup:

```kotlin
import me.cyljacky02.loafylib.util.PlayerCooldowns
import kotlin.time.Duration.Companion.seconds

class MyPlugin : LoafyPlugin() {
    override fun components() = listOf(
        PlayerCooldowns(),
        WandService(registry.get())
    )
}

class WandService(private val cooldowns: PlayerCooldowns) : PluginComponent, Listener {
    private val wandKey = NamespacedKey("myplugin", "wand")
    private val cooldownKey = NamespacedKey("myplugin", "wand_cooldown")
    
    @EventHandler
    suspend fun onInteract(event: PlayerInteractEvent) {
        if (!event.matchesItem(wandKey)) return
        
        val player = event.player
        
        // Check cooldown
        if (cooldowns.isOnCooldown(player, cooldownKey)) {
            val remaining = cooldowns.getRemainingCooldown(player, cooldownKey)
            player.sendMessage("Cooldown: ${remaining?.inWholeSeconds}s remaining")
            return
        }
        
        // Cast spell...
        player.sendMessage("Spell cast!")
        
        // Apply cooldown
        cooldowns.setCooldown(player, cooldownKey, 5.seconds)
    }
    
    override suspend fun initialize() {}
    override suspend fun shutdown() {}
}
```

**PlayerCooldowns API:**
- `isOnCooldown(player, key)` - Check if player is on cooldown
- `setCooldown(player, key, duration)` - Set a cooldown
- `getRemainingCooldown(player, key)` - Get remaining time (or null)
- `clearCooldown(player, key)` - Clear a specific cooldown
- `clearAllCooldowns(player)` - Clear all cooldowns for a player
- Auto-cleans up when players disconnect


### GlowingService

Per-player glowing entity effects with PacketEvents (optional soft dependency):

```kotlin
val glowingService = registry.get<GlowingService>()

// Check if PacketEvents is available
if (glowingService.isAvailable()) {
    
    // === Existing Entity Glowing (16 team colors) ===
    // Make an entity glow for a specific player
    glowingService.setGlowing(entity, player, GlowColor.RED)
    
    // Check if glowing
    if (glowingService.isGlowing(entity, player)) {
        // Remove glow
        glowingService.unsetGlowing(entity, player)
    }
    
    // === Display Entity Glowing (unlimited RGB colors) ===
    // Spawn a glowing block visible only to one player
    val blockId = glowingService.spawnGlowingBlock(
        location = block.location,
        blockData = Material.DIAMOND_BLOCK.createBlockData(),
        receiver = player,
        color = Color.fromRGB(255, 128, 0) // Orange
    )
    
    // Spawn a glowing item
    val itemId = glowingService.spawnGlowingItem(
        location = location,
        itemStack = ItemStack(Material.DIAMOND_SWORD),
        receiver = player,
        color = Color.AQUA
    )
    
    // Spawn glowing text
    val textId = glowingService.spawnGlowingText(
        location = location,
        text = Component.text("Hello!"),
        receiver = player,
        color = Color.LIME
    )
    
    // Update display color
    glowingService.updateDisplayColor(blockId, player, Color.RED)
    
    // Remove display
    glowingService.removeDisplay(blockId, player)
    
    // Get all active displays for a player
    val activeIds = glowingService.getActiveDisplays(player)
    
    // === Shulker Marker Glowing (pure glow outline, 16 team colors) ===
    // Spawn an invisible glowing marker (1x1x1 outline only, no visible block)
    val markerId = glowingService.spawnGlowingMarker(
        location = block.location,
        receiver = player,
        color = GlowColor.GOLD
    )
    
    // Update marker color
    glowingService.updateMarkerColor(markerId, player, GlowColor.RED)
    
    // Remove marker
    glowingService.removeMarker(markerId, player)
    
    // Get all active markers for a player
    val markerIds = glowingService.getActiveMarkers(player)
}
```

**Glowing Approaches Comparison:**

| Approach | Colors | Use Case |
|----------|--------|----------|
| Entity Glowing | 16 team colors | Make existing entities glow |
| Display Entity | Unlimited RGB | Glowing blocks/items/text with custom colors |
| Shulker Marker | 16 team colors | Pure glow outline only (no visible content) |

**Note:** GlowingService requires [PacketEvents](https://github.com/retrooper/packetevents) as an optional soft dependency. When PacketEvents is not installed, `isAvailable()` returns false and all methods become no-ops (graceful degradation).


## Design Philosophy

LoafyLib uses **manual constructor injection** instead of annotation-based DI frameworks like Spring or Fairy. This is intentional:

| Aspect | LoafyLib Approach | Why |
|--------|-------------------|-----|
| Component Discovery | Explicit `components()` list | No classpath scanning = faster startup |
| Dependency Wiring | Constructor injection | Compile-time safety, easy to test |
| Listener Registration | Implement `Listener` interface | No annotations, instanceof check is fast |
| Lifecycle | `PluginComponent` interface | Clear contract, suspend support |

This keeps LoafyLib lightweight (~200 lines for DI) while providing the convenience features you'd expect from a framework.

## Shaded Libraries

LoafyLib includes these libraries (no need to add them to your plugin):

| Library | Version | Relocation | Notes |
|---------|---------|------------|-------|
| Lettuce | 7.2.1 | `me.cyljacky02.loafylib.libs.lettuce` | Netty excluded (uses Paper's) |
| InvUI | 2.0.0-alpha.25 | Not relocated | See below |

### Why InvUI Cannot Be Relocated or Use Library Loader

InvUI v2.x has specific constraints that require it to be shaded without relocation:

1. **Cannot use Paper's library loader**: InvUI v2 uses `paperweight-userdev` and contains NMS (Net Minecraft Server) code that's version-specific. Library loader only works with version-agnostic libraries.

2. **Cannot be relocated**: InvUI v2 uses Netty's `ChannelDuplexHandler` for packet interception:
   ```java
   // From InvUI's PacketListener.java
   channel.pipeline().addBefore(MC_PACKET_HANDLER_NAME, invuiPacketHandlerName, packetHandler);
   ```
   Relocating would change `io.netty` references, breaking the pipeline injection into Paper's native Netty.

3. **Shared library pattern**: All Loafy plugins share LoafyLib's InvUI instance, avoiding conflicts within the ecosystem.

## NOT Shaded (Each Plugin Should Shade)

| Library | Reason |
|---------|--------|
| Lamp | `BukkitLamp.builder(plugin)` binds commands to specific plugin instance. Each plugin must shade their own copy with unique relocation. |


### Lamp Setup for Dependent Plugins

```kotlin
// In your plugin's build.gradle.kts
dependencies {
    // Shade Lamp in YOUR plugin (not from LoafyLib)
    implementation("io.github.revxrsal:lamp.common:4.0.0-rc.14")
    implementation("io.github.revxrsal:lamp.bukkit:4.0.0-rc.14") {
        exclude(group = "net.kyori")
    }
}

tasks.shadowJar {
    // Relocate to YOUR plugin's package
    relocate("revxrsal", "me.example.myplugin.libs.lamp")
}
```

## Optional Dependencies

| Dependency | Purpose | Notes |
|------------|---------|-------|
| [PacketEvents](https://github.com/retrooper/packetevents) | GlowingService | Enables per-player glowing entities. Without it, GlowingService gracefully degrades to no-ops. |

## Library Loader Dependencies

These are loaded via Paper's library loader (available at runtime):

- Kotlin stdlib 2.2.21
- kotlinx-coroutines-core 1.10.2
- HikariCP 7.0.2
- MariaDB JDBC 3.5.7
- Configurate YAML/HOCON 4.2.0
- Reactor Core 3.8.1

## Configuration Notes

### HikariCP Pool Sizing

HikariCP recommends keeping pool sizes small. From the [HikariCP wiki](https://github.com/brettwooldridge/HikariCP/wiki/About-Pool-Sizing):

> "A formula which has held up pretty well across a lot of benchmarks for years is: connections = ((core_count * 2) + effective_spindle_count)"

For most Minecraft servers, a pool size of 5-10 is sufficient. Larger pools can actually *decrease* performance due to connection contention.

### Lettuce Connection Handling

Lettuce uses a single connection by default (multiplexed). This is intentional - Lettuce pipelines commands automatically. The `RedisManager` interface leverages this for optimal performance without manual connection pooling.


## API Reference

### Public Interfaces

- `RedisManager` - Redis connection and command execution
- `DatabaseManager` - Database connection pooling and retry logic
- `PluginComponent` - Component lifecycle interface
- `RedisPipeline` - Redis batch operations
- `GlowingService` - Per-player glowing entity effects (requires PacketEvents)
- `BlockDataService` - Per-block persistent data storage

### RedisPipeline Operations

*String Operations:*
- `set(key, value, ttlSeconds?)` - Set with optional TTL
- `setex(key, ttlSeconds, value)` - Set with TTL
- `setnx(key, value)` - Set if not exists
- `get(key)` - Get value
- `getdel(key)` - Get value and delete atomically

*Key Operations:*
- `del(keys...)` - Delete keys
- `expire(key, ttlSeconds)` - Set TTL
- `exists(keys...)` - Check existence

*Hash Operations:*
- `hset/hmset/hget/hgetall/hdel` - Hash field operations

*List Operations:*
- `lpush/rpush/lrange` - List operations

*Set Operations:*
- `sadd/smembers/srem` - Set operations

*Sorted Set Operations:*
- `zadd(key, score, member)` - Add with score
- `zrank(key, member)` - Get rank (lowest first)
- `zrevrank(key, member)` - Get rank (highest first)
- `zscore(key, member)` - Get score
- `zrem(key, members...)` - Remove members
- `zrangebyscore(key, min, max)` - Get by score range
- `zremrangebyscore(key, min, max)` - Remove by score range
- `zcard(key)` - Get cardinality

*Generic Access:*
- `add { }` - Execute any Lettuce command

### Public Classes

- `LoafyPlugin` - Abstract base class for plugins
- `ComponentRegistry` - Component dependency management
- `LettuceRedisManager` - Redis implementation
- `HikariDatabaseManager` - Database implementation
- `RedisConfig` / `DatabaseConfig` - Configuration data classes
- `ConfigurateUtils` - YAML configuration helpers
- `PaperDispatchers` - Coroutine dispatcher factory
- `SuspendingEventService` - Suspend event handler registration
- `GlowColor` - Enum of 16 team-based glow colors (maps to NamedTextColor)
- `GlowingServiceFactory` - Creates appropriate GlowingService based on PacketEvents availability
- `BlockPDC` - PersistentDataContainer wrapper for block data
- `BlockDataEvent` - Base event for block data lifecycle changes
- `BlockDataRemoveEvent` - Fired when block data is about to be removed
- `BlockDataMoveEvent` - Fired when block data is about to be moved


### Utility Extensions (me.cyljacky02.loafylib.util)

**ItemExtensions.kt:**

*ItemStack DSL:*
- `ItemStack.edit {}` - Edit ItemMeta with DSL, returns ItemStack for chaining
- `ItemStack.editTyped<M> {}` - Type-safe meta editing (e.g., SkullMeta)
- `ItemStack.amount(n)` - Set amount, returns ItemStack for chaining
- `ItemStack.shiny(Boolean)` - Add/remove enchantment glint (1.21+)
- `ItemStack.itemModel(key)` - Set custom item model (1.21+)

*ItemMeta Helpers:*
- `ItemMeta.name(Component)` - Set custom name with italic disabled
- `ItemMeta.loreLines(...)` - Set lore with italic disabled
- `ItemMeta.enchant(enchantment, level)` - Add enchantment ignoring restrictions
- `ItemMeta.clearEnchants()` - Remove all enchantments
- `ItemMeta.flags(...)` - Add multiple ItemFlags
- `ItemMeta.hideAll()` - Hide all item flags
- `ItemMeta.unbreakable(Boolean)` - Set unbreakable state
- `ItemMeta.itemModel(key)` - Set custom item model

*Player Heads:*
- `playerHead(base64)` - Create head from base64 texture
- `playerHead(player)` - Create head from OfflinePlayer
- `playerHead(uuid)` - Create head from UUID


### PDC Extensions (me.cyljacky02.loafylib.pdc)

**PdcExtensions.kt** - Shared type-safe helpers for any PersistentDataContainer:
- `getOrNull(key, type)` - Get value or null
- `getString/getByte/getShort/getInt/getLong(key)` - Type-safe getters
- `getFloat/getDouble/getBoolean(key)` - Type-safe getters
- `getByteArray/getIntArray/getLongArray(key)` - Array getters
- `setString/setByte/setShort/setInt/setLong(key, value)` - Type-safe setters
- `setFloat/setDouble/setBoolean(key, value)` - Type-safe setters
- `setByteArray/setIntArray/setLongArray(key, value)` - Array setters

**ItemPdcExtensions.kt** - Item marker system:
- `ItemStack.markAs(plugin, key)` / `markAs(NamespacedKey)` - Mark item with identifier
- `ItemStack.unmarkAs(plugin, key)` - Remove marker
- `ItemStack.hasItemKey(key)` - Check if item has marker
- `ItemStack.matchesAnyKey(vararg keys)` - Match against multiple keys
- `ItemStack.pdc {}` - Edit PDC with DSL (uses Paper's optimized API)
- `ItemMeta.pdc {}` - Edit ItemMeta's PDC

**EntityPdcExtensions.kt** - Entity marker system:
- `Entity.markAs(plugin, key)` / `markAs(NamespacedKey)` - Mark entity
- `Entity.unmarkAs(plugin, key)` - Remove marker
- `Entity.hasEntityKey(key)` - Check if entity has marker
- `Entity.matchesAnyKey(vararg keys)` - Match against multiple keys
- `Entity.pdc {}` - Edit PDC with DSL

**ChunkPdcExtensions.kt** - Chunk marker system:
- `Chunk.markAs(plugin, key)` / `markAs(NamespacedKey)` - Mark chunk
- `Chunk.unmarkAs(plugin, key)` - Remove marker
- `Chunk.hasChunkKey(key)` - Check if chunk has marker
- `Chunk.matchesAnyKey(vararg keys)` - Match against multiple keys
- `Chunk.pdc {}` - Edit PDC with DSL

**PlayerCooldowns.kt:**
- `PlayerCooldowns` - PluginComponent for per-player cooldown management
- `isOnCooldown(player, key)` - Check cooldown status
- `setCooldown(player, key, duration)` - Apply cooldown
- `getRemainingCooldown(player, key)` - Get remaining duration
- `clearCooldown(player, key)` - Clear specific cooldown
- `clearAllCooldowns(player)` - Clear all player cooldowns

**ComponentExtensions.kt:**
- `Component.noItalic()` - Disable italic decoration
- `String.mini()` - Parse MiniMessage string
- `String.mini(vararg placeholders)` - Parse with key-value placeholders
- `String.mini(vararg resolvers)` - Parse with TagResolvers
- `String.placeholder(value)` - Create TagResolver

**EventExtensions.kt:**
- `PlayerInteractEvent.matchesItem(key)` - Check if interaction item matches key
- `BlockPlaceEvent.matchesItem(key)` - Check if placed item matches key
- `BlockBreakEvent.matchesItem(key)` - Check if held item matches key


### Block Data Extensions (me.cyljacky02.loafylib.blockdata)

**BlockDataExtensions.kt:**

*Block Extensions:*
- `Block.getBlockData(plugin)` - Get BlockPDC for this block
- `Block.hasBlockData(plugin)` - Check if block has data (efficient)
- `Block.clearBlockData(plugin)` - Clear all data from block
- `Block.isBlockDataProtected(plugin)` - Check protection status

*BlockPDC DSL:*
- `BlockPDC.edit {}` - DSL for setting multiple values

*Type-Safe Getters:*
- `BlockPDC.getString/getByte/getShort/getInt/getLong(key)` - Get values or null
- `BlockPDC.getFloat/getDouble/getBoolean(key)` - Get values or null
- `BlockPDC.getByteArray/getIntArray/getLongArray(key)` - Get arrays or null

*Type-Safe Setters:*
- `BlockPDC.setString/setByte/setShort/setInt/setLong(key, value)` - Set values
- `BlockPDC.setFloat/setDouble/setBoolean(key, value)` - Set values
- `BlockPDC.setByteArray/setIntArray/setLongArray(key, value)` - Set arrays

### Exception Classes

- `RedisConnectionException` - Redis connection failures
- `RedisScriptException` - Lua script execution failures
- `RedisPipelineException` - Pipeline batch failures


## Technical Notes

### PDC Package Architecture

The `pdc/` package provides a unified marker system across Items, Entities, and Chunks:

| File | Purpose | Key Functions |
|------|---------|---------------|
| `PdcExtensions.kt` | Shared type-safe getters/setters | `getString`, `setInt`, etc. |
| `ItemPdcExtensions.kt` | Item markers + DSL | `markAs`, `hasItemKey`, `pdc {}` |
| `EntityPdcExtensions.kt` | Entity markers + DSL | `markAs`, `hasEntityKey`, `pdc {}` |
| `ChunkPdcExtensions.kt` | Chunk markers + DSL | `markAs`, `hasChunkKey`, `pdc {}` |

**Why separate from `blockdata/`?** The `blockdata/` package uses `BlockPDC` - a wrapper class that stores data IN chunk PDC with coordinate-based keys. The `pdc/` package provides lightweight markers for direct PDC access on Items, Entities, and Chunks.

**Marker efficiency:** Uses 1-byte `BYTE` markers instead of string values (~97% storage reduction).

### BlockPDC Key Format

Block data keys use bit-packed hex encoding following Paper/Minecraft's coordinate packing philosophy:

```
Format: {namespace}:{5-char hex}  (e.g., "myplugin:0f940")

Bit layout (20 bits):
- Bits 16-19: relX (4 bits, 0-15 within chunk)
- Bits 12-15: relZ (4 bits, 0-15 within chunk)  
- Bits 0-11:  absY + 2048 (12 bits, supports -2048 to 2047)
```

**Why not BlockPos.asLong()?** Paper's `BlockPos.asLong()` packs absolute world coordinates (64 bits). Since block data is stored in chunk PDC (which already identifies the chunk), using absolute coords would be redundant. Chunk-relative packing is ~50% more compact and avoids storing chunk coordinates twice.


## License

MIT License
