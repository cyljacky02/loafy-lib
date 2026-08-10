package me.cyljacky02.loafylib

import me.cyljacky02.loafylib.blockdata.BlockDataService
import me.cyljacky02.loafylib.blockdata.BlockDataServiceImpl
import me.cyljacky02.loafylib.glow.GlowingService
import me.cyljacky02.loafylib.glow.GlowingServiceFactory
import me.cyljacky02.loafylib.permission.PermissionAccess
import me.cyljacky02.loafylib.plugin.LoafyPlugin
import me.cyljacky02.loafylib.plugin.PluginComponent

/**
 * Main plugin class for LoafyLib.
 *
 * LoafyLib is a shared library providing infrastructure for other Loafy plugins:
 * - Coroutine dispatchers for Paper/Folia
 * - Redis management with Lettuce
 * - Database management with HikariCP
 * - Configuration utilities with Configurate
 * - Component lifecycle management
 * - Per-player glowing entity support (requires PacketEvents)
 * - Per-block persistent data storage
 *
 * ## GlowingService
 * The [GlowingService] is automatically registered and available via the component registry.
 * It gracefully degrades when PacketEvents is not installed.
 *
 * ```kotlin
 * val glowingService = registry.get<GlowingService>()
 * if (glowingService.isAvailable()) {
 *     glowingService.setGlowing(entity, player, GlowColor.RED)
 * }
 * ```
 *
 * ## BlockDataService
 * The [BlockDataService] provides per-block persistent data storage using chunk PDCs.
 *
 * ```kotlin
 * val blockDataService = registry.get<BlockDataService>()
 * val blockPdc = blockDataService.getBlockData(block, plugin)
 * blockPdc.set(myKey, PersistentDataType.STRING, "value")
 * ```
 */
class LoafyLibPlugin : LoafyPlugin() {

    /**
     * The GlowingService instance, created lazily during component registration.
     * Stored as a field to allow direct access and interface-based registration.
     */
    private lateinit var glowingService: GlowingService

    /**
     * The BlockDataService instance for per-block persistent data storage.
     * Stored as a field to allow interface-based registration.
     */
    private lateinit var blockDataService: BlockDataServiceImpl

    override fun components(): List<PluginComponent> {
        glowingService = GlowingServiceFactory.create(this)
        blockDataService = BlockDataServiceImpl(this)
        return listOf(glowingService, blockDataService)
    }

    override fun onPluginEnable() {
        // Detect thread-safe permission provider (LuckPerms) — must run before consumer plugins
        PermissionAccess.detect()

        // Register services with interface types for retrieval
        // The concrete types are already registered by LoafyPlugin.onEnable()
        registry.register(GlowingService::class, glowingService)
        registry.register(BlockDataService::class, blockDataService)
        
        val glowStatus = if (glowingService.isAvailable()) "enabled" else "disabled (PacketEvents not found)"
        val permStatus = if (PermissionAccess.isAvailable) "thread-safe (LuckPerms)" else "entity-thread only"
        logger.info("LoafyLib infrastructure ready - Glowing: $glowStatus, BlockData: enabled, Permissions: $permStatus")
    }

    override fun onPluginDisable() {
        logger.info("LoafyLib shutting down")
    }
}
