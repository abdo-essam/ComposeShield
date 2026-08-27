package io.github.composeshield.internal

import androidx.compose.runtime.Composable
import io.github.composeshield.Capability

/**
 * No-op on iOS.
 *
 * Compose sheets and dialogs on iOS are presented within the same [platform.UIKit.UIWindow] as
 * their parent content, so protection applied to that window already covers all popup surfaces.
 * No window interception is required.
 */
@Composable
internal actual fun ProtectedContent(
    capabilities: Set<Capability>,
    content: @Composable () -> Unit,
) {
    content()
}
