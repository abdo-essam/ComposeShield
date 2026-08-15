package io.github.composeshield.internal

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

/**
 * Resolves the foreground window key without a composition context.
 *
 * Used by [io.github.composeshield.ComposeShield.acquire] so the imperative path targets the active
 * key window rather than parking under [WindowKey.Unbound] indefinitely.
 *
 * Falls back to [WindowKey.Unbound] when no key window exists yet (app launch before the scene is
 * active); the registry will re-point the pending request via [ProtectionRegistry.bindWindow] once
 * the first composable runs.
 */
internal actual fun resolveCurrentWindowKey(): WindowKey = activeWindow()?.let(::registerWindow) ?: WindowKey.Unbound
