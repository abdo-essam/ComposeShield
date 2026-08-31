package io.github.composeshield.internal

import platform.UIKit.UIApplication
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowScene

private val windows = mutableMapOf<WindowKey, UIWindow>()

private var nextWindowId = 0

internal fun registerWindow(window: UIWindow): WindowKey {
    windows.entries.firstOrNull { it.value === window }?.let { return it.key }

    val key = WindowKey("ios-window-${nextWindowId++}")
    windows[key] = window
    return key
}

internal fun windowFor(key: WindowKey): UIWindow? = windows[key]

internal fun keyForWindow(window: UIWindow): WindowKey? = windows.entries.firstOrNull { it.value === window }?.key

internal fun forget(key: WindowKey) {
    windows.remove(key)
}

internal fun dismissScene(scene: UIWindowScene) {
    scene.windows.filterIsInstance<UIWindow>().forEach { window ->
        keyForWindow(window)?.let { key ->
            shieldCore.registry.releaseWindow(key)
            forget(key)
        }
    }
}

internal fun activeWindowScene(): UIWindowScene? =
    try {
        val app: UIApplication = UIApplication.sharedApplication
        val scenes = app.connectedScenes
        scenes.filterIsInstance<UIWindowScene>().firstOrNull()
    } catch (_: Throwable) {
        null
    }

internal fun activeWindow(): UIWindow? =
    try {
        val scene = activeWindowScene()
        val sceneWindows = scene?.windows
        sceneWindows?.filterIsInstance<UIWindow>()?.firstOrNull { it.isKeyWindow() }
    } catch (_: Throwable) {
        null
    }
