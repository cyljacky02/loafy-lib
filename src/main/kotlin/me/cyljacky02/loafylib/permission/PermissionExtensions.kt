package me.cyljacky02.loafylib.permission

import me.cyljacky02.loafylib.scheduler.withPlayerContext
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin

/**
 * Thread-safe permission checking extensions for Folia-compatible plugins.
 *
 * ## The Problem
 *
 * Bukkit's `PermissibleBase` uses `LinkedHashMap` — NOT thread-safe.
 * Calling `Player.hasPermission()` from a non-entity thread risks data corruption.
 * Folia does NOT add synchronization to per-player `PermissibleBase` (only to
 * the server-level `PaperPermissionManager`).
 *
 * ## The Solution
 *
 * [hasPermissionSafe] provides a single suspend function that is always thread-safe:
 *
 * - **LuckPerms installed** ([PermissionAccess.isAvailable] = true):
 *   Returns immediately via `Player.hasPermission()`. LP's `LuckPermsPermissible`
 *   uses `ConcurrentHashMap` + `AtomicBoolean` — fully thread-safe from any thread.
 *   Zero overhead, no dispatch.
 *
 * - **LuckPerms absent** ([PermissionAccess.isAvailable] = false):
 *   Dispatches to the player's entity thread via [withPlayerContext], ensuring
 *   `PermissibleBase.hasPermission()` runs on the correct owning thread.
 *
 * ## When to Use What
 *
 * | Caller Context | Function |
 * |----------------|----------|
 * | Entity thread (event handler, entity scheduler, player command) | `player.hasPermission()` — already safe |
 * | Async coroutine (regen tick, PAPI, cross-player command) | `player.hasPermissionSafe(plugin, perm)` — always safe |
 * | Hot path without LP (manual optimization) | Cache + `PermissionAccess.isAvailable` guard |
 *
 * ## Example
 *
 * ```kotlin
 * // In any coroutine — always thread-safe, zero overhead with LP
 * pluginScope.launch {
 *     if (player.hasPermissionSafe(plugin, "myplugin.feature")) {
 *         player.sendMessage("You have access!")
 *     }
 * }
 * ```
 *
 * @see PermissionAccess for LuckPerms detection
 * @see me.cyljacky02.loafylib.scheduler.withPlayerContext for entity-thread dispatch
 */

/**
 * Thread-safe permission check. Always safe from any thread.
 *
 * - LuckPerms available: returns immediately (zero overhead, LP is thread-safe)
 * - LuckPerms absent: dispatches to the player's entity thread via [withPlayerContext]
 *
 * @param plugin The plugin instance for entity-thread dispatch (only used when LP is absent)
 * @param permission The permission node to check
 * @return true if the player has the permission
 * @throws me.cyljacky02.loafylib.scheduler.EntityRetiredException if the player
 *   disconnects before the entity-thread dispatch completes (LP absent only)
 * @throws kotlinx.coroutines.CancellationException if the coroutine is cancelled
 */
suspend fun Player.hasPermissionSafe(plugin: Plugin, permission: String): Boolean =
    if (PermissionAccess.isAvailable) {
        hasPermission(permission)
    } else {
        withPlayerContext(plugin) { hasPermission(permission) }
    }
