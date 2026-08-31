@file:Suppress("FunctionNaming", "MaxLineLength", "MagicNumber")

package io.github.composeshield.validation

import android.graphics.Bitmap
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class ScreenshotValidationTest {
    @get:Rule
    val activityRule = ActivityScenarioRule(ShieldValidationActivity::class.java)

    @Test
    fun screenshotWithProtectionOn_markerAbsent() {
        activityRule.scenario.onActivity { activity ->
            activity.enableShield(true)
        }

        val bitmap = captureScreenshot()

        assertFalse(
            actual = MarkerDetector.isMarkerVisible(bitmap),
            message =
                "C-001 FAILED: SHIELD_TEST_SECRET_001 marker is detectable with protection ON. " +
                    "The OS did not honor the protection request. Evidence: ${bitmap.describe()}",
        )
    }

    @Test
    fun screenshotWithProtectionOff_markerPresent() {
        activityRule.scenario.onActivity { activity ->
            activity.enableShield(false)
        }

        val bitmap = captureScreenshot()

        assertTrue(
            actual = MarkerDetector.isMarkerVisible(bitmap),
            message =
                "C-002 FAILED: SHIELD_TEST_SECRET_001 marker is NOT detectable with protection OFF. " +
                    "The detection mechanism is broken (false negative). Evidence: ${bitmap.describe()}",
        )
    }

    @Test
    fun screenshotAfterShieldDisabled_markerPresent() {
        activityRule.scenario.onActivity { it.enableShield(true) }
        val bitmapProtected = captureScreenshot()
        assertFalse(
            MarkerDetector.isMarkerVisible(bitmapProtected),
            "C-003 pre-condition: marker should be absent when shield is ON",
        )

        activityRule.scenario.onActivity { it.enableShield(false) }
        val bitmapUnprotected = captureScreenshot()

        assertTrue(
            actual = MarkerDetector.isMarkerVisible(bitmapUnprotected),
            message = "C-003 FAILED: marker did not return after shield was disabled (residual protection).",
        )
    }

    @Test
    fun screenshotValidation_idempotent() {
        activityRule.scenario.onActivity { activity ->
            activity.enableShield(true)
            activity.enableShield(true) // double-enable
        }

        val bitmap = captureScreenshot()
        assertFalse(
            actual = MarkerDetector.isMarkerVisible(bitmap),
            message = "I-001 FAILED: double-enable produced inconsistent protection state.",
        )
    }

    @Test
    fun screenshotAfterRelease_markerPresent() {
        activityRule.scenario.onActivity { it.enableShield(true) }
        val bitmapProtected = captureScreenshot()
        assertFalse(MarkerDetector.isMarkerVisible(bitmapProtected), "R-001 pre-condition: should be protected")

        activityRule.scenario.onActivity { it.releaseShield() }
        val bitmapReleased = captureScreenshot()

        assertTrue(
            actual = MarkerDetector.isMarkerVisible(bitmapReleased),
            message = "R-001 FAILED: marker did not return after ComposeShield scope was released.",
        )
    }

    private fun captureScreenshot(): Bitmap? {
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        Thread.sleep(300)
        return try {
            androidx.test.runner.screenshot.Screenshot.capture().bitmap
        } catch (_: Throwable) {
            null
        }
    }

    private fun Bitmap?.describe(): String {
        if (this == null) return "bitmap=null (OS blocked capture via FLAG_SECURE)"
        val cx = width / 2
        val cy = height / 2
        return "bitmap=${width}x$height, center=($cx,$cy), " +
            "center_pixel=#${Integer.toHexString(getPixel(cx, cy)).uppercase()}"
    }
}
