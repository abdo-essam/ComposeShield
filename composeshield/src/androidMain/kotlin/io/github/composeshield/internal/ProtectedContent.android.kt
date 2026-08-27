package io.github.composeshield.internal

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import io.github.composeshield.Capability

/**
 * Wraps [content] with a [SecureContextWrapper] that intercepts [WindowManager.addView] so that
 * every child window created inside the composition — Compose [Dialog], [Popup], or any
 * [android.app.Dialog] subclass including Material3 [ModalBottomSheet] — automatically receives
 * [WindowManager.LayoutParams.FLAG_SECURE] before it is ever visible.
 *
 * The interception is installed only when [capabilities] is non-empty; when the set is empty
 * [content] is composed with the original context unchanged.
 *
 * **Why [remember(base, isActive)]?**  The [SecureContextWrapper] instance is stable across
 * recompositions that share the same base context and activation state, so Compose's
 * equality-based skip logic is not broken by a new wrapper object on each frame. Transitioning
 * from active → inactive re-creates the entry as the plain [base] context, causing
 * [LocalContext] to revert and new dialogs to receive no interception.
 */
@Composable
internal actual fun ProtectedContent(
    capabilities: Set<Capability>,
    content: @Composable () -> Unit,
) {
    val base = LocalContext.current
    val isActive = capabilities.isNotEmpty()
    val effectiveContext = remember(base, isActive) {
        if (isActive) SecureContextWrapper(base) else base
    }
    CompositionLocalProvider(LocalContext provides effectiveContext) {
        content()
    }
}

/**
 * A [android.content.ContextWrapper] that returns a [SecureWindowManager] for the
 * [Context.WINDOW_SERVICE] system service.
 *
 * Any [android.app.Dialog] — including Compose [Dialog] and every composable that wraps one —
 * stores its [WindowManager] reference at construction time via
 * `context.getSystemService(Context.WINDOW_SERVICE)`. Providing this wrapper as
 * [androidx.compose.ui.platform.LocalContext] inside [SecureContent] therefore covers all dialog
 * and popup types that respect [LocalContext], with no per-dialog boilerplate and no dependency on
 * any specific dialog implementation.
 */
internal class SecureContextWrapper(base: Context) : android.content.ContextWrapper(base) {
    override fun getSystemService(name: String): Any? {
        val service = super.getSystemService(name)
        return if (name == WINDOW_SERVICE && service is WindowManager) {
            SecureWindowManager(service)
        } else {
            service
        }
    }
}

/**
 * A [WindowManager] decorator that stamps [WindowManager.LayoutParams.FLAG_SECURE] on every
 * window added or updated through it.
 *
 * All other [WindowManager] and [ViewManager] methods are forwarded to [delegate] via Kotlin
 * interface delegation, keeping this class surgically minimal.
 *
 * [updateViewLayout] is also intercepted because a dialog may update its layout params after
 * showing (e.g. to resize), which would otherwise create a window-attributes object without the
 * flag set.
 */
internal class SecureWindowManager(private val delegate: WindowManager) : WindowManager by delegate {

    override fun addView(view: View, params: ViewGroup.LayoutParams) {
        params.stampSecureFlag()
        delegate.addView(view, params)
    }

    override fun updateViewLayout(view: View, params: ViewGroup.LayoutParams) {
        params.stampSecureFlag()
        delegate.updateViewLayout(view, params)
    }

    private fun ViewGroup.LayoutParams.stampSecureFlag() {
        if (this is WindowManager.LayoutParams) {
            flags = flags or WindowManager.LayoutParams.FLAG_SECURE
        }
    }
}
