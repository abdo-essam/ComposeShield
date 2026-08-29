package io.github.composeshield

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import io.github.composeshield.internal.ProtectedContent
import io.github.composeshield.internal.rememberWindowKey
import io.github.composeshield.internal.shieldCore

/**
 * Protects the window from screen capture for as long as this is composed.
 *
 * The recommended way to use the library. Protection is acquired when the boundary enters
 * composition and released when it leaves — there is no teardown call to forget, which is
 * where hand-managed protection usually leaks.
 *
 * ```kotlin
 * // Wrap your root composable to protect the entire application, or scope to a screen
 * SecureContent {
 *     AppContent()
 * }
 * ```
 *
 * **Protection is window-scoped, not subtree-scoped.** While this is composed, the *entire window*
 * is protected, not only [content] — so a sibling composable outside this boundary is protected too.
 *
 * **Dialog and bottom-sheet windows are protected automatically.** Any [Dialog] or [Popup]
 * composable (including Material3 [ModalBottomSheet]) declared inside [content] creates its own
 * window; that child window inherits the same protection without any extra code inside the dialog
 * body. Content in an unrelated window (the other half of a split screen, for example) is not
 * covered.
 *
 * Nesting is safe: protection releases only when the last boundary leaves. It survives configuration
 * change and background/restore without re-invocation, and renders [content] identically to an
 * unwrapped call.
 *
 * @param capabilities which preventions to request. Changes are honoured — a genuinely different set
 *   re-acquires — but a freshly-allocated yet *equal* set per recomposition neither releases nor
 *   re-applies: the boundary stabilizes the set internally, because re-applying on Android toggles
 *   `FLAG_SECURE` and tears down the window's surface, which the user sees as a black frame.
 *   Defaults to `{ScreenshotPrevention, RecordingPrevention}`.
 * @param onProtectionFailure invoked when a requested mechanism fails to install or stops working
 *   mid-session. Invoked from a composition coroutine and guarded: a throwing callback cannot crash
 *   the host application, though it will not be re-invoked for that failure either.
 * @param content the protected content. Rendered unchanged.
 */
@Composable
public fun SecureContent(
    capabilities: Set<Capability> = DefaultPreventionCapabilities,
    onProtectionFailure: ((Capability) -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val window = rememberWindowKey()

    // remember compares its key by equality, not identity, so a consumer allocating a fresh-but-equal
    // set every recomposition recomputes nothing here and the effect below does not restart. Keying
    // the effect on the raw parameter instead would release/re-apply protection on each of those
    // recompositions — the surface-tearing toggle documented above. The copy runs inside remember's
    // calculation, so equal sets cost no allocation at all on the steady-state path.
    val stableCapabilities = remember(capabilities) { capabilities.toSet() }

    val currentOnFailure by rememberUpdatedState(onProtectionFailure)
    // The failure filter must read the CURRENT set, not the one captured when the collector started:
    // a capability added mid-session should report its failures, a removed one should stop reporting.
    val currentCapabilities by rememberUpdatedState(capabilities)

    DisposableEffect(window, stableCapabilities) {
        val request = shieldCore.registry.acquire(window, stableCapabilities)
        shieldCore.registry.bindWindow(window)
        onDispose { shieldCore.registry.release(request) }
    }

    if (onProtectionFailure != null) {
        LaunchedEffect(Unit) {
            shieldCore.protectionFailures.collect { failed ->
                // The flow replays its most recent failure to late collectors; that replay can
                // outlive the failure it describes (the capability was released and has since
                // installed cleanly — pruned from failedMechanisms). A capability not currently
                // recorded as failed is stale news, so it is dropped here: fresh failures are
                // always still in the set when emitted, so nothing genuine is lost.
                if (failed !in shieldCore.registry.current.failedMechanisms) return@collect

                if (failed in currentCapabilities) {
                    // A consumer callback runs inside this composition coroutine; letting it throw
                    // would kill the host app — precisely the failure mode this library exists to
                    // prevent. The swallow is deliberate: the durable truth (supportLevel and the
                    // registry's failedMechanisms) is unaffected, only this best-effort channel is.
                    runCatching { currentOnFailure?.invoke(failed) }
                }
            }
        }
    }

    // Wraps content with a platform-specific interceptor so any child window created inside
    // (Compose Dialog, Popup, ModalBottomSheet, or any android.app.Dialog subclass) automatically
    // inherits the active protection. On iOS this is a no-op — popup surfaces share the same
    // UIWindow and are already covered.
    ProtectedContent(stableCapabilities) { content() }
}
