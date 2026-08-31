package io.github.composeshield

import android.app.Activity
import android.view.WindowManager
import io.github.composeshield.internal.AndroidPlatformProtection
import io.github.composeshield.internal.ProtectionOutcome
import io.github.composeshield.internal.registerWindow
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class SecureFlagTest {
    @Test
    fun `C12 - applying protection sets FLAG_SECURE and clearing removes it`() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val platform = AndroidPlatformProtection()
        val window = registerWindow(activity.window, activity)

        val outcome = platform.applyProtection(window, setOf(Capability.ScreenshotPrevention))

        assertEquals(ProtectionOutcome.Applied, outcome)
        assertTrue(activity.window.isFlagSecureSet(), "FLAG_SECURE must be set on the host window")

        platform.clearProtection(window)

        assertFalse(
            activity.window.isFlagSecureSet(),
            "the flag must be cleared, not merely stop being requested — a leaked flag renders " +
                "every later screenshot of this window black",
        )
    }

    @Test
    fun `applying protection preserves unrelated window flags`() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val platform = AndroidPlatformProtection()
        val window = registerWindow(activity.window, activity)

        activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        platform.applyProtection(window, setOf(Capability.ScreenshotPrevention))

        assertTrue(
            activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON != 0,
            "addFlags, never setFlags(flags, ALL) — the latter clobbers flags the host app set",
        )
    }

    @Test
    fun `a request against an unregistered window defers rather than failing`() {
        val platform = AndroidPlatformProtection()

        val outcome =
            platform.applyProtection(
                io.github.composeshield.internal.WindowKey.Unbound,
                setOf(Capability.ScreenshotPrevention),
            )

        assertEquals(
            ProtectionOutcome.Deferred,
            outcome,
            "no host yet is ordinary startup ordering, not a mechanism failure — reporting Failed " +
                "here would fire the failure posture and blank a screen for no reason",
        )
    }

    @Test
    @Config(sdk = [34])
    fun `prevention precludes screenshot events on Android`() {
        assertTrue(
            AndroidPlatformProtection().preventionPrecludesScreenshotEvents,
            "AOSP Activity.java: the capture callback is not invoked on a window with FLAG_SECURE",
        )
    }
}

private fun android.view.Window.isFlagSecureSet(): Boolean =
    attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0
