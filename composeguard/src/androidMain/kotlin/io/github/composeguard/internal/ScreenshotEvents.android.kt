package io.github.composeguard.internal

import android.app.Activity
import android.os.Build
import io.github.composeguard.SupportLevel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.emptyFlow

/**
 * Emits once per screenshot, via `Activity.registerScreenCaptureCallback` (API 34+).
 *
 * Below API 34 this is unsupported and the stream is empty. The only pre-34 technique is a
 * `ContentObserver` on MediaStore, which needs `READ_MEDIA_IMAGES` — a permission the library is
 * forbidden from requesting — and is heuristic across OEMs anyway.
 *
 * Note this capability is also suppressed *at runtime* whenever screenshot prevention is active,
 * because the OS does not invoke the callback on a window with `FLAG_SECURE`. That exclusion is
 * applied by [io.github.composeguard.internal.SupportResolver] rather than here, since it depends on
 * registry state this class deliberately knows nothing about.
 */
internal class ScreenshotEvents {
    fun support(): SupportLevel = supportedFromApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)

    fun events(): Flow<Unit> {
        if (sdkInt < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return emptyFlow()

        return callbackFlow {
            val activity = anyRegisteredActivity()
            if (activity == null) {
                // Empty rather than an error: an unsupported or unavailable stream must be silent,
                // so a consumer's collector is never handed an exception.
                awaitClose { }
                return@callbackFlow
            }

            val callback = Activity.ScreenCaptureCallback { trySend(Unit) }
            activity.registerScreenCaptureCallback(activity.mainExecutor, callback)
            awaitClose { activity.unregisterScreenCaptureCallback(callback) }
        }
    }
}
