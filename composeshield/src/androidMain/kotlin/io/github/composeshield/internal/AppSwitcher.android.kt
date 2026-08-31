package io.github.composeshield.internal

import android.os.Build
import androidx.annotation.RequiresApi
import io.github.composeshield.SupportLevel

private const val RECENTS_SCREENSHOT_API = Build.VERSION_CODES.TIRAMISU

internal class AppSwitcher {
    fun support(): SupportLevel = supportedFromApi(RECENTS_SCREENSHOT_API)

    fun apply(
        window: WindowKey,
        enabled: Boolean,
    ) {
        if (Build.VERSION.SDK_INT < RECENTS_SCREENSHOT_API) return
        val activity = activityFor(window) ?: anyRegisteredActivity() ?: return

        onMainThread(ifDeferred = Unit) {
            setRecentsScreenshotEnabled(activity, enabled)
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun setRecentsScreenshotEnabled(
        activity: android.app.Activity,
        enabled: Boolean,
    ) {
        activity.setRecentsScreenshotEnabled(!enabled)
    }
}
