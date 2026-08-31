package io.github.composeshield.internal

import android.app.Activity
import android.view.Window
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider

@Composable
internal actual fun rememberWindowKey(): WindowKey {
    val view = LocalView.current
    val activity = LocalActivity.current

    return remember(view, activity) {
        val window = (view.parent as? DialogWindowProvider)?.window ?: activity?.window
        window?.let { registerWindow(it, activity) } ?: WindowKey.Unbound
    }
}

internal actual fun resolveCurrentWindowKey(): WindowKey {
    val activity = anyRegisteredActivity() ?: return WindowKey.Unbound
    val window = activity.window ?: return WindowKey.Unbound
    return registerWindow(window, activity)
}
