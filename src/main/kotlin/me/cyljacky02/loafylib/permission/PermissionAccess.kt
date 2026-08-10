package me.cyljacky02.loafylib.permission

/**
 * Centralized thread-safe permission provider detection for Folia-compatible plugins.
 *
 * ## The Problem
 *
 * Bukkit's `PermissibleBase` stores permissions in a `LinkedHashMap` — NOT thread-safe.
 * Paper's `NormalPaperPermissionManager` adds NO synchronization on per-player checks.
 * Calling `Player.hasPermission()` from a non-entity thread risks torn reads or
 * `ConcurrentModificationException` if `recalculatePermissions()` runs concurrently.
 *
 * In Folia, `CraftPlayer.getHandle()` explicitly bypasses thread ownership checks
 * ("no checks for players"), so `hasPermission()` from the wrong thread will NOT throw —
 * it silently risks `LinkedHashMap` data corruption.
 *
 * ## The Solution
 *
 * LuckPerms replaces `PermissibleBase` with `LuckPermsPermissible`, which is explicitly
 * documented as thread-safe (uses `ConcurrentHashMap` + `AtomicBoolean`; LP source,
 * commit `6b7283ac`, 2025-03-20). When LP is installed, `Player.hasPermission()` is
 * safe from ANY thread.
 *
 * ## Usage Patterns
 *
 * ```kotlin
 * // From entity thread (event handler, entity scheduler, player command):
 * player.hasPermission("my.perm")  // Always safe — correct thread
 *
 * // From async coroutine when LP is available:
 * if (PermissionAccess.isAvailable) {
 *     player.hasPermission("my.perm")  // Safe — LP makes it thread-safe
 * } else {
 *     cachedPermissions[uuid]  // Use cached value from entity-thread resolution
 * }
 *
 * // From async context, dispatch to entity thread (always safe, suspends):
 * player.withPlayerContext(plugin) {
 *     hasPermission("my.perm")
 * }
 * ```
 *
 * ## Detection
 *
 * Call [detect] once during server startup. [LoafyLibPlugin][me.cyljacky02.loafylib.LoafyLibPlugin]
 * calls this automatically in `onPluginEnable()`. Consumer plugins can read [isAvailable]
 * without any setup.
 *
 * ## Why Not Wrap `hasPermission()`?
 *
 * A wrapper function like `Player.hasPermissionSafe()` would be a passthrough —
 * it cannot add thread safety. When LP is installed, `player.hasPermission()` is already
 * safe. When LP is absent, a wrapper still calls the same unsafe `PermissibleBase`.
 * The correct fallback is either caching (for hot paths) or dispatching to the entity
 * thread via `player.withPlayerContext(plugin) { hasPermission(...) }`.
 *
 * @see me.cyljacky02.loafylib.scheduler.PaperDispatchers for thread safety reference
 * @see me.cyljacky02.loafylib.scheduler.withPlayerContext for entity-thread dispatch
 */
object PermissionAccess {

    /**
     * Whether a thread-safe permission provider (LuckPerms) is installed.
     *
     * When `true`: `Player.hasPermission()` is safe from **any thread**.
     * LuckPerms replaces Bukkit's `PermissibleBase` with its own thread-safe
     * `LuckPermsPermissible` (`ConcurrentHashMap` + `AtomicBoolean`).
     *
     * When `false`: `Player.hasPermission()` is **only safe from the entity's
     * owning thread**. Use cached values for async contexts, or dispatch to
     * the entity thread via `player.withPlayerContext(plugin) { ... }`.
     */
    @Volatile
    var isAvailable: Boolean = false
        private set

    /**
     * Detects whether a thread-safe permission provider is installed and functional.
     *
     * Uses `LuckPermsProvider.get()` via reflection to confirm LP's API is actually
     * registered — not just that the JAR is present. This catches the edge case where
     * LP is installed but **failed to enable** (DB error, config error, etc.), in which
     * case `Bukkit.getPluginManager().getPlugin("LuckPerms")` would return non-null
     * (plugins are registered during load phase) but LP never injected its thread-safe
     * `LuckPermsPermissible` into players — a false positive.
     *
     * ## Load Order Guarantee
     *
     * LoafyLib's `paper-plugin.yml` declares `LuckPerms` with `load: BEFORE`,
     * `required: false`. Paper's dependency resolver guarantees that if LP is present,
     * its `onEnable()` completes before LoafyLib's `onEnable()`. This makes
     * `LuckPermsProvider.get()` safe to call here — LP's API is already registered.
     *
     * ## Failure Modes
     *
     * - LP absent → `ClassNotFoundException` → `false`
     * - LP present but failed to enable → `IllegalStateException` → `false`
     * - LP present and working → API instance returned → `true`
     *
     * Called automatically by `LoafyLibPlugin.onPluginEnable()`.
     */
    fun detect() {
        isAvailable = try {
            Class.forName("net.luckperms.api.LuckPermsProvider")
                .getMethod("get")
                .invoke(null)
            true
        } catch (_: Exception) {
            false
        }
    }
}
