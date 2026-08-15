package io.github.composeshield

import android.app.Activity
import android.view.WindowManager
import io.github.composeshield.internal.AndroidPlatformProtection
import io.github.composeshield.internal.ProtectionRegistry
import io.github.composeshield.internal.registerWindow
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Contract test C13 — protection never leaks across navigation (SC-007).
 *
 * The failure this guards against is asymmetric and quiet. A leaked `FLAG_SECURE` does not crash or
 * log; it makes some unrelated screen screenshot as solid black, hours later and far from the
 * navigation that caused it. Reference counting is what prevents it, and 100 cycles is what proves
 * the counting has no drift — an off-by-one that survives one cycle is obvious, one that accumulates
 * only under repetition is exactly the bug that reaches production.
 */
@RunWith(RobolectricTestRunner::class)
class LifecycleReleaseTest {
    @Test
    fun `C13 - the flag is clear after 100 rapid navigation cycles`() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val registry = ProtectionRegistry(AndroidPlatformProtection())
        val window = registerWindow(activity.window, activity)

        repeat(100) {
            val request = registry.acquire(window, setOf(Capability.ScreenshotPrevention))
            assertTrue(activity.window.isFlagSecureSet(), "protection must apply on entry")
            registry.release(request)
            assertFalse(activity.window.isFlagSecureSet(), "protection must release on exit")
        }

        assertFalse(
            activity.window.isFlagSecureSet(),
            "SC-007: no residual protection after repeated navigation",
        )
    }

    @Test
    fun `overlapping screens keep the flag until the last one leaves`() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val registry = ProtectionRegistry(AndroidPlatformProtection())
        val window = registerWindow(activity.window, activity)

        // The real navigation shape: the incoming screen composes before the outgoing one disposes.
        val outgoing = registry.acquire(window, setOf(Capability.ScreenshotPrevention))
        val incoming = registry.acquire(window, setOf(Capability.ScreenshotPrevention))

        registry.release(outgoing)

        assertTrue(
            activity.window.isFlagSecureSet(),
            "the departing screen must not unprotect the arriving one — this is the exposure " +
                "reference counting exists to prevent, and it is invisible until someone " +
                "screenshots the transition",
        )

        registry.release(incoming)
        assertFalse(activity.window.isFlagSecureSet())
    }

    @Test
    fun `destroying a window clears protection even with requests outstanding`() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val registry = ProtectionRegistry(AndroidPlatformProtection())
        val window = registerWindow(activity.window, activity)

        registry.acquire(window, setOf(Capability.ScreenshotPrevention))
        registry.releaseWindow(window)

        assertFalse(
            activity.window.isFlagSecureSet(),
            "a window torn down without its boundaries disposing cleanly must not leave the flag set",
        )
    }
}

private fun android.view.Window.isFlagSecureSet(): Boolean =
    attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0
