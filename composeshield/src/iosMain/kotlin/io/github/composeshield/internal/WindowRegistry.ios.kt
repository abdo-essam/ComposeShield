package io.github.composeshield.internal

import platform.UIKit.UIApplication
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowScene

/**
 * Maps opaque [WindowKey]s to the `UIWindow`s they name.
 *
 * The counterpart of the Android table, and it exists for the same reason: common code coordinates
 * per-window state but must never hold a platform window. Unlike Android there is no weak-map type
 * in Kotlin/Native's stdlib, so entries are removed explicitly when a window is released — see
 * [forget]. Windows are few and long-lived, so this is a bounded, well-defined lifetime rather than
 * a leak waiting to happen.
 */
private val windows = mutableMapOf<WindowKey, UIWindow>()

private var nextWindowId = 0

/** Assigns [window] a stable key, reusing the existing one where it already has one. */
internal fun registerWindow(window: UIWindow): WindowKey {
    windows.entries.firstOrNull { it.value === window }?.let { return it.key }

    val key = WindowKey("ios-window-${nextWindowId++}")
    windows[key] = window
    return key
}

/** The window [key] names, or `null` if it was never registered or has been forgotten. */
internal fun windowFor(key: WindowKey): UIWindow? = windows[key]

/** Drops [key]'s entry, so a dismissed window is not retained by this table. */
internal fun forget(key: WindowKey) {
    windows.remove(key)
}

/**
 * The application's foreground-active window scene.
 *
 * Detection attaches here rather than to any view. The scene is the root trait
 * environment and the source of the capture trait, which puts it above the question of where
 * prevention may have reparented content — the two subsystems stay decoupled by construction.
 */
internal fun activeWindowScene(): UIWindowScene? =
    try {
        val app: UIApplication? = UIApplication.sharedApplication
        val scenes = app?.connectedScenes
        scenes?.filterIsInstance<UIWindowScene>()?.firstOrNull()
    } catch (_: Throwable) {
        null
    }

/** The key window of the active scene, which is what an imperative request applies to. */
internal fun activeWindow(): UIWindow? =
    try {
        val scene = activeWindowScene()
        val sceneWindows = scene?.windows
        sceneWindows?.filterIsInstance<UIWindow>()?.firstOrNull { it.isKeyWindow() }
    } catch (_: Throwable) {
        null
    }
