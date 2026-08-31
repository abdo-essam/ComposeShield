package io.github.composeshield.internal

import android.annotation.TargetApi
import android.app.Activity
import android.os.Build
import androidx.annotation.RequiresApi
import io.github.composeshield.SupportLevel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.emptyFlow

private const val SCREEN_CAPTURE_CALLBACKS_API = Build.VERSION_CODES.UPSIDE_DOWN_CAKE

internal class ScreenshotEvents {
    fun support(): SupportLevel = supportedFromApi(SCREEN_CAPTURE_CALLBACKS_API)

    fun events(): Flow<Unit> {
        if (Build.VERSION.SDK_INT < SCREEN_CAPTURE_CALLBACKS_API) return emptyFlow()

        return eventsAtApi34()
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun eventsAtApi34(): Flow<Unit> =
        callbackFlow {
            val activity = anyRegisteredActivity()
            if (activity == null) {
                awaitClose { }
                return@callbackFlow
            }

            val callback = Activity.ScreenCaptureCallback { trySend(Unit) }
            activity.registerScreenCaptureCallback(activity.mainExecutor, callback)
            awaitClose { activity.unregisterScreenCaptureCallback(callback) }
        }
}
