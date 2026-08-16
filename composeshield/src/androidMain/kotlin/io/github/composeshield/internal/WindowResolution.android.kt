package io.github.composeshield.internal

import android.app.Activity
import android.view.Window
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider

/**
 * Resolves the Android [Window] hosting this composition.
 *
 * Two hosts are possible and they must be distinguished, because protection is window-scoped: a
 * dialog gets its own `Window`, and applying `FLAG_SECURE` to the activity behind it would leave
 * the dialog's own content capturable. The dialog is checked first for exactly that reason.
 *
 * [LocalActivity] rather than `LocalContext.current as Activity`: the cast is wrong under any
 * `ContextWrapper` and activity-compose ships a `ContextCastToActivity` lint check flagging it.
 * `LocalActivity` walks the wrapper chain properly and needs no reflection.
 */
@Composable
internal actual fun rememberWindowKey(): WindowKey {
    val view = LocalView.current
    val activity = LocalActivity.current

    return remember(view, activity) {
        // Dialog window first: dialog content lives in its own window, and the activity flag
        // does not cover it.
        val window = (view.parent as? DialogWindowProvider)?.window ?: activity?.window
        window?.let { registerWindow(it, activity) } ?: WindowKey.Unbound
    }
}

/**
 * Resolves a window key without a composition context.
 *
 * Used by [io.github.composeshield.ComposeShield.acquire] so the imperative path targets a real
 * window rather than parking the request under [WindowKey.Unbound] indefinitely.
 *
 * The target is the **first registered activity's window, not the foreground one** — tracking real
 * focus is deliberately out of scope, and the single-window app is the effective model. With
 * multiple live activities the resolved window is unspecified; compose `SecureContent` per window
 * for multi-window correctness.
 *
 * Falls back to [WindowKey.Unbound] at cold start before any activity window is registered — the
 * registry will re-point the pending request via [ProtectionRegistry.bindWindow] once the first
 * composable runs.
 */
internal actual fun resolveCurrentWindowKey(): WindowKey {
    val activity = anyRegisteredActivity() ?: return WindowKey.Unbound
    val window = activity.window ?: return WindowKey.Unbound
    return registerWindow(window, activity)
}
