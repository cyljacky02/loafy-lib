package me.cyljacky02.loafylib.protection

import me.cyljacky02.loafylib.protection.impl.GriefPreventionHook
import me.cyljacky02.loafylib.protection.impl.LandsHook
import me.cyljacky02.loafylib.protection.impl.ResidenceHook
import me.cyljacky02.loafylib.protection.impl.WorldGuardHook
import org.bukkit.plugin.Plugin

/**
 * Factory for creating [ProtectionHook] instances.
 *
 * Detects available protection plugins at runtime and creates
 * appropriate hook implementations. Uses class isolation to
 * prevent ClassNotFoundException when plugins aren't installed.
 *
 * ## Supported Plugins
 *
 * - **WorldGuard** (EngineHub/WorldGuard) - Region-based protection
 * - **Lands** (IncrediblePlugins/LandsAPI) - Land claiming protection
 * - **Residence** (Zrips/Residence) - Residence-based protection
 * - **GriefPrevention** (GriefPrevention/GriefPrevention) - Claim-based protection
 *
 * ## Design Notes
 *
 * The factory uses `Class.forName()` for initial checks because:
 * 1. It's the safest way to detect a soft dependency before any code references it
 * 2. Directly referencing API classes would throw `NoClassDefFoundError` if not installed
 * 3. This pattern is standard for Bukkit/Paper soft dependencies
 *
 * Each implementation is in a separate file to ensure class isolation.
 * The implementation classes are only loaded AFTER `Class.forName()` succeeds.
 *
 * ## Usage
 *
 * ```kotlin
 * class MyPlugin : LoafyPlugin() {
 *     lateinit var protection: ProtectionHook
 *         private set
 *
 *     override fun onPluginEnable() {
 *         protection = ProtectionHookFactory.create(this)
 *     }
 * }
 *
 * // In event handler
 * if (plugin.protection.canBuild(player, location)) {
 *     // Allow action
 * }
 * ```
 *
 * @see ProtectionHook
 */
object ProtectionHookFactory {

    /**
     * Creates a [ProtectionHook] based on available protection plugins.
     *
     * Logs which integrations are enabled for debugging purposes.
     *
     * @param plugin The plugin instance (required for LandsAPI integration)
     * @return A hook that integrates with all available protection plugins,
     *         or [NoOpProtectionHook] if none are available
     */
    fun create(plugin: Plugin): ProtectionHook {
        val logger = plugin.logger
        val hooks = buildList {
            if (isWorldGuardAvailable()) {
                add(createWorldGuardHook())
                logger.info("ProtectionHook: WorldGuard integration enabled")
            }
            if (isLandsAvailable()) {
                add(createLandsHook(plugin))
                logger.info("ProtectionHook: Lands integration enabled")
            }
            if (isResidenceAvailable()) {
                add(createResidenceHook())
                logger.info("ProtectionHook: Residence integration enabled")
            }
            if (isGriefPreventionAvailable()) {
                add(createGriefPreventionHook())
                logger.info("ProtectionHook: GriefPrevention integration enabled")
            }
        }

        return when {
            hooks.isEmpty() -> {
                logger.info("ProtectionHook: No protection plugins found")
                NoOpProtectionHook
            }
            hooks.size == 1 -> hooks.first()
            else -> CompositeProtectionHook(hooks)
        }
    }

    /**
     * Checks if WorldGuard is available at runtime.
     */
    private fun isWorldGuardAvailable(): Boolean = try {
        Class.forName("com.sk89q.worldguard.WorldGuard")
        true
    } catch (_: ClassNotFoundException) {
        false
    } catch (_: NoClassDefFoundError) {
        false
    }

    /**
     * Checks if Lands is available at runtime.
     */
    private fun isLandsAvailable(): Boolean = try {
        Class.forName("me.angeschossen.lands.api.LandsIntegration")
        true
    } catch (_: ClassNotFoundException) {
        false
    } catch (_: NoClassDefFoundError) {
        false
    }

    /**
     * Checks if Residence is available at runtime.
     */
    private fun isResidenceAvailable(): Boolean = try {
        // Use main plugin class for detection (more reliable entry point)
        Class.forName("com.bekvon.bukkit.residence.Residence")
        true
    } catch (_: ClassNotFoundException) {
        false
    } catch (_: NoClassDefFoundError) {
        false
    }

    /**
     * Checks if GriefPrevention is available at runtime.
     */
    private fun isGriefPreventionAvailable(): Boolean = try {
        // Check main plugin class for detection, ProtectionHelper for API availability
        Class.forName("me.ryanhamshire.GriefPrevention.GriefPrevention")
        Class.forName("com.griefprevention.protection.ProtectionHelper")
        true
    } catch (_: ClassNotFoundException) {
        false
    } catch (_: NoClassDefFoundError) {
        false
    }

    /**
     * Creates WorldGuard hook.
     * Only called after [isWorldGuardAvailable] returns true.
     */
    private fun createWorldGuardHook(): ProtectionHook = WorldGuardHook()

    /**
     * Creates Lands hook.
     * Only called after [isLandsAvailable] returns true.
     */
    private fun createLandsHook(plugin: Plugin): ProtectionHook = LandsHook(plugin)

    /**
     * Creates Residence hook.
     * Only called after [isResidenceAvailable] returns true.
     */
    private fun createResidenceHook(): ProtectionHook = ResidenceHook()

    /**
     * Creates GriefPrevention hook.
     * Only called after [isGriefPreventionAvailable] returns true.
     */
    private fun createGriefPreventionHook(): ProtectionHook = GriefPreventionHook()
}

