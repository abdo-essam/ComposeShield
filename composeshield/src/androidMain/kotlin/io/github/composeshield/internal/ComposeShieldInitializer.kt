package io.github.composeshield.internal

import android.app.Activity
import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Bundle

/**
 * Initializes ComposeShield's activity-lifecycle tracker before any [Activity.onCreate] runs.
 *
 * A [ContentProvider] is the standard Android-library pattern for zero-user-setup initialization
 * (used by WorkManager, Firebase App, Glide, and many others). The OS calls [onCreate] during
 * [Application.attachBaseContext], which happens **before** any [Activity.onCreate] and therefore
 * before [Activity.onResume] — guaranteeing that the lifecycle callbacks are in place before the
 * first activity ever reaches the resumed state.
 *
 * This provider is registered in the library's `AndroidManifest.xml` and merged automatically
 * into the consuming application's manifest by the Android Gradle Plugin. No user code required.
 *
 * **Why a [ContentProvider] rather than [Application.registerActivityLifecycleCallbacks] lazily?**
 * Lazy registration (from a composable or from the first `acquire()` call) races with the activity
 * lifecycle: by the time the first composable runs, [Activity.onResume] has already fired, so the
 * callback misses the current session entirely. A [ContentProvider] sidesteps the race entirely.
 */
internal class ComposeShieldInitializer : ContentProvider() {
    override fun onCreate(): Boolean {
        val application = context?.applicationContext as? Application ?: return true
        installActivityTracker(application)
        return true
    }

    // ContentProvider contract — none of these are ever called.
    override fun query(
        uri: Uri,
        p: Array<out String>?,
        s: String?,
        a: Array<out String>?,
        so: String?,
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null

    override fun insert(
        uri: Uri,
        values: ContentValues?,
    ): Uri? = null

    override fun delete(
        uri: Uri,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0
}

/**
 * Registers [Application.ActivityLifecycleCallbacks] so every resumed activity's window is
 * present in the [windows] table.
 *
 * Split from [ComposeShieldInitializer] so it can also be called from [anyRegisteredActivity] as a
 * last-resort fallback (e.g. in tests or processes where the [ContentProvider] is suppressed via
 * `tools:node="remove"`).
 */
internal fun installActivityTracker(application: Application) {
    application.registerActivityLifecycleCallbacks(
        object : Application.ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) {
                // Register (or refresh) the window entry and re-point any unbound requests that
                // arrived before the first composition.
                val key = registerWindow(activity.window, activity)
                // shieldCore lazy-init is safe here: the activity is resumed, so the UI exists and
                // any platform call is appropriate. If shieldCore is already initialized, this is a
                // fast no-op when there are no Unbound requests.
                shieldCore.registry.bindWindow(key)
            }

            override fun onActivityCreated(
                activity: Activity,
                savedInstanceState: Bundle?,
            ) = Unit

            override fun onActivityStarted(activity: Activity) = Unit

            override fun onActivityPaused(activity: Activity) = Unit

            override fun onActivityStopped(activity: Activity) = Unit

            override fun onActivitySaveInstanceState(
                activity: Activity,
                outState: Bundle,
            ) = Unit

            override fun onActivityDestroyed(activity: Activity) = Unit
        },
    )
}
