package io.github.composeshield.internal

import androidx.compose.runtime.Composable
import io.github.composeshield.Capability

/**
 * Wraps [content] so that any child window created within the composition (Compose [Dialog],
 * [Popup], Material3 [ModalBottomSheet], or any [android.app.Dialog] subclass) automatically
 * inherits the active protection capabilities without requiring [SecureContent] to be placed
 * inside each one.
 *
 * **Android**: provides a [android.content.ContextWrapper] whose [android.view.WindowManager]
 * intercepts [android.view.WindowManager.addView] and stamps
 * [android.view.WindowManager.LayoutParams.FLAG_SECURE] on every new window before it becomes
 * visible. The interception is invisible to the caller — no new public API, no reflection, no
 * Material3 dependency.
 *
 * **iOS**: Compose sheets and dialogs share the same [platform.UIKit.UIWindow] as their parent
 * content, so protection applied to that window already covers all popup surfaces. This function
 * is a no-op there.
 *
 * When [capabilities] is empty nothing is being protected, so no interception is installed.
 */
@Composable
internal expect fun ProtectedContent(
    capabilities: Set<Capability>,
    content: @Composable () -> Unit,
)
