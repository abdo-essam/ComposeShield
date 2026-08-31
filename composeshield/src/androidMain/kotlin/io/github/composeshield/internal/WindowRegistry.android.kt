package io.github.composeshield.internal

import android.app.Activity
import android.view.Window
import java.util.WeakHashMap

private val windows = WeakHashMap<Window, Entry>()

private class Entry(
    val key: WindowKey,
    val activity: Activity?,
)

private var nextWindowId = 0

internal fun registerWindow(
    window: Window,
    activity: Activity?,
): WindowKey =
    synchronized(windows) {
        windows[window]?.let { existing ->
            if (existing.activity == null && activity != null) {
                windows[window] = Entry(existing.key, activity)
            }
            return existing.key
        }
        val key = WindowKey("android-window-${nextWindowId++}")
        windows[window] = Entry(key, activity)
        key
    }

private fun entryFor(key: WindowKey): Map.Entry<Window, Entry>? = windows.entries.firstOrNull { it.value.key == key }

internal fun windowFor(key: WindowKey): Window? = synchronized(windows) { entryFor(key)?.key }

internal fun activityFor(key: WindowKey): Activity? = synchronized(windows) { entryFor(key)?.value?.activity }

internal fun keyForActivity(activity: Activity): WindowKey? =
    synchronized(windows) {
        windows.entries.firstOrNull { it.value.activity === activity }?.value?.key
    }

internal fun anyRegisteredActivity(): Activity? =
    synchronized(windows) {
        windows.values.firstNotNullOfOrNull { it.activity }
    }
