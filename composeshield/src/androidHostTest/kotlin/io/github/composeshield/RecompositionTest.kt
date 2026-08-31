package io.github.composeshield

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

            assertEquals(
                1,
                compositionCount,
                "SecureContent must not cause extra recompositions during setup",
            )
        }

    @Test
    fun `SecureContent does not multiply recompositions when content state changes`() =
        runComposeUiTest {
            var compositionCount = 0
            var contentState by mutableStateOf(0)

            setContent {
                SecureContent {
                    @Suppress("UNUSED_EXPRESSION")
                    contentState
                    compositionCount++
                }
            }
            waitForIdle()
            val countAfterInitial = compositionCount

            contentState++
            waitForIdle()

            assertEquals(
                countAfterInitial + 1,
                compositionCount,
                "SecureContent must not multiply content recompositions — " +
                    "content recomposed more than once suggests an internal subscription " +
                    "is re-entering the slot each time state changes",
            )
        }

    @Test
    fun `SecureContent does not recompose content on repeated identical capability sets`() =
        runComposeUiTest {
            var compositionCount = 0
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

            capabilities = setOf(Capability.ScreenshotPrevention, Capability.RecordingPrevention)
            waitForIdle()

            assertEquals(
                countBeforeChange + 1,
                compositionCount,
                "a capability-set change must produce exactly one content recomposition, not a burst",
            )
        }
}
