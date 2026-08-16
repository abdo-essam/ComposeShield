package io.github.composeshield.internal

import android.app.Activity
import android.view.Window
import java.util.WeakHashMap

/**
 * Maps opaque [WindowKey]s back to the Android windows they name.
 *
 * Common code coordinates protection per window but must never hold a platform window — it would
 * outlive the activity and leak the whole view hierarchy with it. So common code carries a string
 * key and this table holds the association.
 *
 * **Weakly**, and that is the point: a destroyed activity's window becomes collectable even if a
 * stale [WindowKey] is still referenced somewhere. A strong map would turn every rotation into a
 * leaked activity.
 *
 * Access is synchronised on the table itself. Registration happens on the main thread from
 * composition or from [ComposeShieldInitializer]'s lifecycle callbacks, but lookups arrive from
 * any thread via the imperative API.
 */
private val windows = WeakHashMap<Window, Entry>()

/** A window's key, plus the activity that owns it where there is one. */
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
            // Re-register to pick up an activity that was absent on first call (e.g. a dialog
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

/**
 * The table entry [key] names, or `null` if it was never registered or has since been collected.
 *
 * A linear scan over a single-digit-length table is cheaper than maintaining a second index.
 */
private fun entryFor(key: WindowKey): Map.Entry<Window, Entry>? = windows.entries.firstOrNull { it.value.key == key }

/** The window [key] names, or `null` if it was never registered or has since been collected. */
internal fun windowFor(key: WindowKey): Window? = synchronized(windows) { entryFor(key)?.key }

/** The activity owning the window [key] names, or `null` for a dialog with no resolvable host. */
internal fun activityFor(key: WindowKey): Activity? = synchronized(windows) { entryFor(key)?.value?.activity }

/** The key of the window [activity] owns, or `null` if it was never registered. */
internal fun keyForActivity(activity: Activity): WindowKey? =
    synchronized(windows) {
        windows.entries.firstOrNull { it.value.activity === activity }?.value?.key
    }

/**
 * The activity of any currently-registered window, for application-scoped platform callbacks.
 *
 * Deliberately arbitrary: the first registered entry, not the foreground activity. Focus tracking
 * is out of scope, and the single-window app is the effective model. Callers that need a specific
 * window must resolve it themselves (e.g. via [keyForActivity]).
 */
internal fun anyRegisteredActivity(): Activity? =
    synchronized(windows) {
        windows.values.firstNotNullOfOrNull { it.activity }
    }
