package io.github.composeguard.internal

import android.app.Activity
import android.view.Window
import java.util.WeakHashMap

/**
 * Maps opaque [WindowKey]s back to the Android windows they name.
 *
 * Common code coordinates protection per window but must never hold a platform window — it would
 * outlive the activity and leak the whole view hierarchy with it. So common code carries a string
 * key and this table, which lives entirely in `androidMain`, holds the association.
 *
 * **Weakly**, and that is the point: a destroyed activity's window becomes collectable even if a
 * stale [WindowKey] is still referenced somewhere. A strong map here would turn every rotation into
 * a leaked activity, which is the classic Android library defect this design is avoiding.
 *
 * Access is synchronised on the table itself. Registration happens on the main thread from
 * composition, but lookups arrive from any thread via the imperative API (FR-018).
 */
private val windows = WeakHashMap<Window, Entry>()

/** A window's key, plus the activity that owns it where there is one. */
private class Entry(
    val key: WindowKey,
    val activity: Activity?,
)

/**
 * Assigns [window] a stable key, reusing it if one was already assigned.
 *
 * Stability matters: the key is what the registry counts requests against, so a window that
 * produced a fresh key per composition would accumulate phantom entries that never release.
 */
internal fun registerWindow(
    window: Window,
    activity: Activity?,
): WindowKey =
    synchronized(windows) {
        windows[window]?.let { existing ->
            // Re-register to pick up an activity that was not resolvable the first time (a dialog
            // composed before its host was attached), without disturbing the established key.
            if (existing.activity == null && activity != null) {
                windows[window] = Entry(existing.key, activity)
            }
            return existing.key
        }

        val key = WindowKey("android-window-${nextWindowId++}")
        windows[window] = Entry(key, activity)
        key
    }

private var nextWindowId = 0

/** The window [key] names, or `null` if it was never registered or has since been collected. */
internal fun windowFor(key: WindowKey): Window? =
    synchronized(windows) {
        windows.entries.firstOrNull { it.value.key == key }?.key
    }

/** The activity owning the window [key] names, or `null` for a dialog with no resolvable host. */
internal fun activityFor(key: WindowKey): Activity? =
    synchronized(windows) {
        windows.entries
            .firstOrNull { it.value.key == key }
            ?.value
            ?.activity
    }

/** The activity of any currently-registered window, for application-scoped platform callbacks. */
internal fun anyRegisteredActivity(): Activity? =
    synchronized(windows) {
        windows.values.firstNotNullOfOrNull { it.activity }
    }
