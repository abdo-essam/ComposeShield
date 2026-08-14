package io.github.composeguard

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import io.github.composeguard.internal.guardCore
import io.github.composeguard.internal.rememberWindowKey
import kotlinx.coroutines.flow.map

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
 * screen) is not. This is a platform constraint rather than a design choice, and it is the most
 * likely misunderstanding of the API; see `docs/platform-notes.md`.
 *
 * Nesting is safe: protection releases only when the last boundary leaves. It survives configuration
 * change and background/restore without re-invocation, and renders [content] identically to an
 * unwrapped call.
 *
 * **Support is not uniform.** On Android this is genuinely preventive. On iOS the underlying
 * mechanism is not sanctioned by Apple and does nothing until
 * [ComposeGuard.optInToUnsanctionedCapability] is called — composing this on iOS without that opt-in
 * protects nothing, by design. Query [ComposeGuard.supportLevel] rather than assuming.
 *
 * @param capabilities which preventions to request. The default is a compile-time constant, not a
 *   fresh set per recomposition.
 * @param onProtectionFailure invoked when a requested mechanism fails to install or stops working
 *   mid-session. Reports the failure whatever the declared [FailurePosture] — the posture governs
 *   the content, this governs what the application knows.
 * @param content the protected content. Rendered unchanged, except when a fail-closed posture is in
 *   force and the mechanism has broken, in which case it is obscured.
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

    // Evaluated continuously rather than once at installation: a mechanism that installs
    // successfully and breaks later must obscure the content at the moment of loss, not stay
    // visible until something unrelated happens to recompose.
    val obscured by remember(window) {
        guardCore.registry.snapshots.map { it.shouldObscureContent(window) }
    }.collectAsState(initial = guardCore.registry.current.shouldObscureContent(window))

    if (obscured) {
        // Occupies the same space and keeps the same layout, so the screen does not reflow as it
        // blanks. alpha(0f) rather than skipping composition: content that stopped being composed
        // would lose its state, and restoring protection would reset the user's screen.
        Box(Modifier.alpha(0f)) { content() }
    } else {
        content()
    }
}
