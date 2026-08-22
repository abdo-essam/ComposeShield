package io.github.composeshield

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.composeshield.internal.shieldCore
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * C1 at the composition level — the boundary's lifetime is the composition's lifetime.
 *
 * [io.github.composeshield.internal.ProtectionRegistryTest] proves the registry counts correctly when
 * told to. This proves `SecureContent` does the telling: entering composition acquires, leaving
 * releases, and nesting behaves. The distinction matters because the failure it guards against is
 * invisible — a boundary that never acquires looks identical to one that does, right up until the
 * screen is captured.
 *
 * **Runs under Robolectric rather than in `commonTest`**, despite `SecureContent` being common code.
 * `runComposeUiTest` needs a real composition host: on the JVM its idling strategy reads
 * `Build.FINGERPRINT`, which is null outside an Android environment. `commonTest` sources compile
 * into this source set anyway, so the boundary is still exercised as common code — only the host
 * differs.
 *
 * The v1 test APIs are deprecated in CMP 1.11; `androidx.compose.ui.test.v2.runComposeUiTest` is the
 * replacement.
 */
@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalTestApi::class)
class SecureContentTest {
    @get:Rule
    internal val host: RobolectricComposeHost = RobolectricComposeHost()

    @Test
    fun `C1 - entering composition acquires protection and leaving releases it`() =
        runComposeUiTest {
            var visible by mutableStateOf(true)

            setContent {
                if (visible) SecureContent { }
            }
            waitForIdle()

            assertTrue(
                ComposeShield.isProtectionActive(),
                "composing the boundary must acquire — a boundary that renders without acquiring is " +
                    "indistinguishable from one that works, until the screen is captured",
            )

            visible = false
            waitForIdle()

            assertFalse(
                ComposeShield.isProtectionActive(),
                "FR-002: release is automatic on leaving composition, with no teardown call to forget",
            )
        }

    @Test
    fun `C2 - nested boundaries release only when the last one leaves`() =
        runComposeUiTest {
            var innerVisible by mutableStateOf(true)

            setContent {
                SecureContent {
                    if (innerVisible) SecureContent { }
                }
            }
            waitForIdle()

            innerVisible = false
            waitForIdle()

            assertTrue(
                ComposeShield.isProtectionActive(),
                "FR-004: the outer boundary still wants protection the inner one just gave up",
            )
        }

    @Test
    fun `sibling boundaries are counted separately`() =
        runComposeUiTest {
            var firstVisible by mutableStateOf(true)

            setContent {
                if (firstVisible) SecureContent { }
                SecureContent { }
            }
            waitForIdle()

            firstVisible = false
            waitForIdle()

            assertTrue(ComposeShield.isProtectionActive())
        }

    @Test
    fun `changing the capability set re-acquires rather than dropping protection`() =
        runComposeUiTest {
            var capabilities by mutableStateOf(setOf(Capability.ScreenshotPrevention))

            setContent {
                SecureContent(capabilities = capabilities) { }
            }
            waitForIdle()

            capabilities = setOf(Capability.RecordingPrevention)
            waitForIdle()

            assertTrue(
                ComposeShield.isProtectionActive(),
                "the DisposableEffect restarts on a capability change; protection must survive the swap",
            )
        }

    @Test
    fun `a freshly allocated equal capability set does not restart the protection effect`() =
        runComposeUiTest {
            // The hazard: a consumer writing `capabilities = setOf(...)` inline allocates a new,
            // equal set on every recomposition. Keying the effect on that raw parameter would
            // release/re-apply each time — on Android a FLAG_SECURE toggle, i.e. surface teardown.
            val capabilities = mutableStateOf(setOf(Capability.ScreenshotPrevention))

            setContent {
                // toSet() makes the instability explicit: a fresh instance every recomposition.
                SecureContent(capabilities = capabilities.value.toSet()) { }
            }
            waitForIdle()
            val settled = shieldCore.registry.snapshots.value

            // Every acquire/release produces a new RegistryState instance, so reference identity of
            // the published snapshot is an effect-restart detector: same instance ⇒ no restart.
            capabilities.value = setOf(Capability.ScreenshotPrevention)
            waitForIdle()

            assertSame(
                settled,
                shieldCore.registry.snapshots.value,
                "a fresh-but-equal set must not release/re-acquire protection",
            )

            capabilities.value = setOf(Capability.ScreenshotPrevention, Capability.RecordingPrevention)
            waitForIdle()

            assertNotSame(
                settled,
                shieldCore.registry.snapshots.value,
                "a genuinely different set must still re-acquire",
            )
            assertTrue(ComposeShield.isProtectionActive())
        }

    @Test
    fun `content is composed exactly once`() =
        runComposeUiTest {
            // FR-006. The boundary is a pass-through in the ordinary case — it must not skip content,
            // double-compose it, or introduce a wrapper that changes layout.
            var composed = 0

            setContent {
                SecureContent {
                    composed++
                }
            }
            waitForIdle()

            assertEquals(1, composed)
        }

    @Test
    fun `a hundred rapid enter-and-exit cycles leave no protection outstanding`() =
        runComposeUiTest {
            // SC-007, at the composition level. A single leaked request renders every later screenshot
            // of the window black, and the leak is silent until someone tries.
            var visible by mutableStateOf(false)

            setContent {
                if (visible) SecureContent { }
            }

            repeat(NAVIGATION_CYCLES) {
                visible = true
                waitForIdle()
                visible = false
                waitForIdle()
            }

            assertFalse(
                ComposeShield.isProtectionActive(),
                "a leaked request after rapid navigation is the failure SC-007 exists to catch",
            )
        }

    private companion object {
        const val NAVIGATION_CYCLES = 100
    }
}
