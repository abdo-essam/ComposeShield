package io.github.composeguard

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import io.github.composeguard.internal.guardCore
import io.github.composeguard.internal.rememberWindowKey

/**
 * Protects the window from screen capture for as long as this is composed.
 *
 * The recommended way to use the library. Protection is acquired when the boundary enters
 * composition and released when it leaves — there is no teardown call to forget, which is
 * where hand-managed protection usually leaks.
 *
 * ```kotlin
 * SecureContent {
 *     AccountBalance(balance)
 * }
 * ```
 *
 * **Protection is window-scoped, not subtree-scoped.** While this is composed, the *entire window*
 * is protected, not only [content] — so a sibling composable outside this boundary is protected too,
 * and content in a *different* window (a dialog with its own window, the other half of a split
 * screen) is not.
 *
 * Nesting is safe: protection releases only when the last boundary leaves. It survives configuration
 * change and background/restore without re-invocation, and renders [content] identically to an
 * unwrapped call.
 *
 * @param capabilities which preventions to request. The default is a compile-time constant, not a
 *   fresh set per recomposition.
 * @param onProtectionFailure invoked when a requested mechanism fails to install or stops working
 *   mid-session.
 * @param content the protected content. Rendered unchanged.
 */
@Composable
public fun SecureContent(
    capabilities: Set<Capability> = DefaultPreventionCapabilities,
    onProtectionFailure: ((Capability) -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val window = rememberWindowKey()

    // Read through a snapshot state so a callback that changes between recompositions does not
    // restart the DisposableEffect below — restarting it would release and re-apply protection,
    // which on a visible window is a surface teardown the user sees as a black frame.
    val currentOnFailure by rememberUpdatedState(onProtectionFailure)

    DisposableEffect(window, capabilities) {
        val request = guardCore.registry.acquire(window, capabilities)
        guardCore.registry.bindWindow(window)
        onDispose { guardCore.registry.release(request) }
    }

    if (onProtectionFailure != null) {
        LaunchedEffect(Unit) {
            guardCore.protectionFailures.collect { failed ->
                if (failed in capabilities) currentOnFailure?.invoke(failed)
            }
        }
    }

    content()
}
