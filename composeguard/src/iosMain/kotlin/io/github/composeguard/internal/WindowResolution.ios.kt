package io.github.composeguard.internal

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/**
 * Resolves the `UIWindow` hosting this composition.
 *
 * Compose Multiplatform hosts content in the scene's key window, so that is what protection applies
 * to. Resolved from the application rather than from the composition because CMP exposes no
 * composition-local for the hosting window — and unlike Android there is no separate dialog window
 * to distinguish, since iOS presents sheets within the same window.
 *
 * Returns [WindowKey.Unbound] before the scene has a key window, at which point the registry holds
 * the request and applies it once one appears.
 */
@Composable
internal actual fun rememberWindowKey(): WindowKey =
    remember {
        activeWindow()?.let(::registerWindow) ?: WindowKey.Unbound
    }
