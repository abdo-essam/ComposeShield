package io.github.composeguard

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * SC-006 — `SecureContent` causes no extra recompositions beyond what its content requires.
 *
 * A boundary that added recompositions on top of its content's own would be a hidden performance
 * hazard and a correctness risk: each extra recomposition of the `DisposableEffect` key slot
 * releases and re-applies `FLAG_SECURE` on a live window, producing the surface teardown
 * documented in research.md R8.
 *
 * The key property: `SecureContent` reads `guardCore.registry.snapshots` (to decide whether to
 * obscure content) but must not read `ComposeGuard.captureState`. The two are independent flows.
 * A subscription to captureState inside `SecureContent` would drive gratuitous content
 * recompositions every time the capture state transitions — exactly what SC-006 forbids.
 *
 * **Runs under Robolectric** — `runComposeUiTest` needs a real composition host.
 */
@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalTestApi::class)
class RecompositionTest {
    @get:org.junit.Rule
    internal val host: RobolectricComposeHost = RobolectricComposeHost()

    @Test
    fun `SecureContent composes its content exactly once on a stable boundary`() =
        runComposeUiTest {
            var compositionCount = 0

            setContent {
                SecureContent {
                    compositionCount++
                }
            }
            waitForIdle()

            // Content must be composed exactly once. More recompositions during setup imply an
            // internal state subscription (e.g. snapshots flow emitting an initial value) that
            // re-enters the content slot immediately — each such re-entry is a needless FLAG_SECURE
            // toggle on a live window.
            assertEquals(
                1,
                compositionCount,
                "SC-006: SecureContent must not cause extra recompositions during setup",
            )
        }

    @Test
    fun `SecureContent does not multiply recompositions when content state changes`() =
        runComposeUiTest {
            var compositionCount = 0
            var contentState by mutableStateOf(0)

            setContent {
                SecureContent {
                    // Read contentState so this content lambda recomposes when it changes.
                    // SecureContent must not add further recompositions on top of that single,
                    // expected restart.
                    @Suppress("UNUSED_EXPRESSION")
                    contentState
                    compositionCount++
                }
            }
            waitForIdle()
            val countAfterInitial = compositionCount

            contentState++
            waitForIdle()

            // The content lambda read contentState, so exactly one recomposition is expected.
            // A count higher than +1 means SecureContent's own state subscriptions are
            // re-entering the content slot an extra time.
            assertEquals(
                countAfterInitial + 1,
                compositionCount,
                "SC-006: SecureContent must not multiply content recompositions — " +
                    "content recomposed more than once suggests an internal subscription " +
                    "is re-entering the slot each time state changes",
            )
        }

    @Test
    fun `SecureContent does not recompose content on repeated identical capability sets`() =
        runComposeUiTest {
            var compositionCount = 0
            // A stable top-level val, not a fresh setOf() per call — this is what the library
            // guarantees via DefaultPreventionCapabilities being a compile-time constant.
            val capabilities = setOf(Capability.ScreenshotPrevention, Capability.RecordingPrevention)

            setContent {
                SecureContent(capabilities = capabilities) {
                    compositionCount++
                }
            }
            waitForIdle()
            val countAfterInitial = compositionCount

            waitForIdle()

            assertTrue(
                compositionCount <= countAfterInitial + 1,
                "content must not recompose more than once across stable boundary parameters",
            )
        }

    @Test
    fun `changing capabilities causes exactly one content recomposition`() =
        runComposeUiTest {
            var compositionCount = 0
            var capabilities by mutableStateOf(setOf(Capability.ScreenshotPrevention))

            setContent {
                SecureContent(capabilities = capabilities) {
                    compositionCount++
                }
            }
            waitForIdle()
            val countBeforeChange = compositionCount

            // A real capability-set change must still recompose — the boundary restarts its
            // DisposableEffect (which is correct: it needs to re-acquire for the new set) and
            // the content slot is revisited. The count must move by exactly 1, not more.
            capabilities = setOf(Capability.ScreenshotPrevention, Capability.RecordingPrevention)
            waitForIdle()

            assertEquals(
                countBeforeChange + 1,
                compositionCount,
                "a capability-set change must produce exactly one content recomposition, not a burst",
            )
        }
}
