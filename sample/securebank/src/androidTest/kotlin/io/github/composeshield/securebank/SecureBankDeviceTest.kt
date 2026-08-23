@file:Suppress("MagicNumber")

package io.github.composeshield.securebank

import android.content.Intent
import android.graphics.BitmapFactory
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Device-level proof that SecureBank's sensitive screens are actually blocked from capture.
 *
 * Screenshots are taken via UiAutomator (framebuffer path — the same output a real screen capture
 * produces), so a passing pair of tests proves both directions:
 *  - protection ON  -> the framebuffer shows nothing (FLAG_SECURE honoured);
 *  - demo mode      -> the same screen IS captured (the pipeline itself works).
 *
 * Run on an emulator or device: `./gradlew :sample:securebank:connectedDebugAndroidTest`
 */
@RunWith(AndroidJUnit4::class)
class SecureBankDeviceTest {
    private val device: UiDevice =
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    @org.junit.Before
    fun resetSessionLock() {
        // The tracker is a process-wide latch; a previous test's teardown onStop must not
        // push this test's launch into the (dark) LockOverlay.
        BackgroundTracker.unlockSession()
        // A sleeping device makes EVERY framebuffer capture black, which would make the
        // protected test pass vacuously and the demo control fail spuriously.
        runCatching { device.wakeUp() }
        device.executeShellCommand("wm dismiss-keyguard")
        device.waitForIdle(WAIT_IDLE_MS)
    }

    // -------------------------------------------------------------------
    // Protection ON: FLAG_SECURE must be applied AND the framebuffer must
    // show nothing — either a dark frame or an outright capture refusal
    // (some OEM skins refuse every capture path while a secure window is
    // showing, which blocks even harder).
    // -------------------------------------------------------------------

    @Test
    fun accountsScreen_blockedFromCapture_whenProtectionActive() {
        launchAccounts(demoMode = false).use { scenario ->
            assertTrue(
                "SecureContent did not apply FLAG_SECURE within the timeout.",
                awaitWindowFlagSecure(scenario, expected = true),
            )
            waitForIdle()
            val bitmap = grabFramebufferOrNull()
            assertFalse(
                "Capture shows bright content while FLAG_SECURE is set.",
                bitmap != null && bitmap.hasBrightContent(),
            )
        }
    }

    // -------------------------------------------------------------------
    // Negative control (demo mode): the SAME screen MUST produce a bright,
    // decodable capture. This validates the whole evidence pipeline so the
    // protected test above can never pass vacuously.
    // -------------------------------------------------------------------

    @Test
    fun accountsScreen_capturable_inDemoMode() {
        launchAccounts(demoMode = true).use { scenario ->
            assertTrue(
                "Demo mode unexpectedly left FLAG_SECURE applied.",
                awaitWindowFlagSecure(scenario, expected = false),
            )
            waitForIdle()
            val bitmap = grabFramebufferOrNull()
            assertTrue(
                "Demo-mode capture produced no content (bitmap=$bitmap) — the negative control is broken.",
                bitmap?.hasBrightContent() == true,
            )
        }
    }

    // -------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------

    private fun launchAccounts(demoMode: Boolean): ActivityScenario<MainActivity> {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val intent =
            Intent(context, MainActivity::class.java).apply {
                putExtra(MainActivity.EXTRA_START_ROUTE, "accounts")
                putExtra(MainActivity.EXTRA_DEMO_MODE, demoMode)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        return ActivityScenario.launch(intent)
    }

    /** Polls until the activity window's FLAG_SECURE matches [expected], or times out. */
    private fun awaitWindowFlagSecure(
        scenario: ActivityScenario<MainActivity>,
        expected: Boolean,
    ): Boolean {
        repeat(POLL_ATTEMPTS) {
            var matches = false
            scenario.onActivity { activity ->
                val secure =
                    (activity.window.attributes.flags and android.view.WindowManager.LayoutParams.FLAG_SECURE) != 0
                matches = secure == expected
            }
            if (matches) return true
            Thread.sleep(POLL_INTERVAL_MS)
        }
        return false
    }

    private fun waitForIdle() {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        device.waitForIdle(WAIT_IDLE_MS)
        Thread.sleep(SETTLE_MS)
    }

    /**
     * Captures the framebuffer, or returns null when the OS refused the capture entirely.
     *
     * A null result while FLAG_SECURE is showing is itself evidence of blocking — some OEM
     * skins refuse every capture path against a secure window instead of returning black.
     */
    private fun grabFramebufferOrNull(): android.graphics.Bitmap? {
        val file =
            java.io.File(
                InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,
                "fb-evidence.png",
            )
        // OEM screenshot services fail transiently and the screen may doze mid-test:
        // wake and retry rather than letting one flaky call decide pass/fail.
        repeat(FRAMEBUFFER_ATTEMPTS) {
            runCatching { device.wakeUp() }
            if (device.takeScreenshot(file)) {
                BitmapFactory.decodeFile(file.absolutePath)?.let { return it }
            }
            Thread.sleep(RETRY_INTERVAL_MS)
        }
        // Some OEM skins (ColorOS among them) block or break the AccessibilityService
        // screenshot path. `screencap` uses the same framebuffer a real capture does and
        // honours FLAG_SECURE identically, so it is an equally valid evidence source.
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val descriptor = automation.executeShellCommand("screencap -p")
        android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { stream ->
            return android.graphics.BitmapFactory.decodeStream(stream)
        }
    }

    /** True when any sampled pixel is clearly brighter than FLAG_SECURE's replacement output. */
    private fun android.graphics.Bitmap.hasBrightContent(): Boolean {
        val step = maxOf(1, width / 64)
        for (x in 0 until width step step) {
            for (y in 0 until height step step) {
                val pixel = getPixel(x, y)
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF
                if (r + g + b > BRIGHT_THRESHOLD) return true
            }
        }
        return false
    }

    private companion object {
        const val POLL_ATTEMPTS = 15
        const val POLL_INTERVAL_MS = 200L
        const val WAIT_IDLE_MS = 2_000L
        const val SETTLE_MS = 500L
        const val FRAMEBUFFER_ATTEMPTS = 3
        const val RETRY_INTERVAL_MS = 1_000L

        /** Sum-of-channels threshold; FLAG_SECURE frames are pure black or near-black. */
        const val BRIGHT_THRESHOLD = 150
    }
}
