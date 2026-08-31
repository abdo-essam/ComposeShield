package io.github.composeshield.internal

import android.app.Activity
import android.app.Application
import android.os.Bundle
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

internal class ForegroundEvents {
    fun events(): Flow<Unit> =
        callbackFlow {
            val application = anyRegisteredActivity()?.application
            if (application == null) {
                awaitClose { }
                return@callbackFlow
            }

            var started = 0

            val callbacks =
                object : Application.ActivityLifecycleCallbacks {
                    override fun onActivityStarted(activity: Activity) {
                        started++
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
