package io.github.composeguard.internal

import android.app.Activity
import android.app.Application
import android.os.Bundle
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Emits each time the application returns to the foreground.
 *
 * Observed through `ActivityLifecycleCallbacks` rather than a `ProcessLifecycleOwner`, which would
 * add an androidx.lifecycle dependency the library does not otherwise need.
 *
 * **Counts started activities rather than reacting to each `onStart`.** A configuration change or a
 * navigation between activities momentarily has two activities started, and a rotation destroys and
 * recreates one — reacting to every `onStart` would treat both as a return to foreground and re-poll
 * repeatedly on transitions where the app never left. Only the zero-to-one edge is a real foreground.
 */
internal class ForegroundEvents {
    fun events(): Flow<Unit> =
        callbackFlow {
            val application = anyRegisteredActivity()?.application
            if (application == null) {
                // No activity registered yet, so nothing to observe through. The subscription stays
                // open and silent rather than erroring — the flow is re-collected on refresh.
                awaitClose { }
                return@callbackFlow
            }

            var started = 0

            val callbacks =
                object : Application.ActivityLifecycleCallbacks {
                    override fun onActivityStarted(activity: Activity) {
                        started++
                        // The zero-to-one edge only: the app was fully backgrounded and is now not.
                        if (started == 1) trySend(Unit)
                    }

                    override fun onActivityStopped(activity: Activity) {
                        if (started > 0) started--
                    }

                    override fun onActivityCreated(
                        activity: Activity,
                        savedInstanceState: Bundle?,
                    ) = Unit

                    override fun onActivityResumed(activity: Activity) = Unit

                    override fun onActivityPaused(activity: Activity) = Unit

                    override fun onActivitySaveInstanceState(
                        activity: Activity,
                        outState: Bundle,
                    ) = Unit

                    override fun onActivityDestroyed(activity: Activity) = Unit
                }

            application.registerActivityLifecycleCallbacks(callbacks)
            awaitClose { application.unregisterActivityLifecycleCallbacks(callbacks) }
        }
}
