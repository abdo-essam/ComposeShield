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

/**
 * Screenshot validation tests — run on a real physical Android device via Firebase Test Lab.
 *
 * **Validation strategy (protected screenshot evidence)**:
 * The test renders the [SHIELD_TEST_SECRET_001] marker in a known region of the screen.
 * When ComposeShield protection is active, the test asserts the marker is **absent** from
 * a captured bitmap sample of that region. The test does NOT assert a specific output colour
 * (solid black, blur, placeholder); only marker absence matters. This decouples the assertion
 * from OS implementation details and focuses on the actual guarantee: protected content is
 * not exposed. See FR-010, research.md D3.
 *
 * **Negative control** (C-002): with protection OFF, the marker IS detectable — confirming
 * the detection mechanism itself works and eliminating false positives (FR-011).
 *
 * Requirement coverage:
 * - C-001: protection ON → marker absent
 * - C-002: protection OFF → marker present (negative control)
 * - C-003: shield disabled after active → marker returns
 * - I-001: double-enable idempotency
 * - R-001: cleanup after scope ends → marker returns
 */
@RunWith(AndroidJUnit4::class)
class ScreenshotValidationTest {
    @get:Rule
    val activityRule = ActivityScenarioRule(ShieldValidationActivity::class.java)

    // -----------------------------------------------------------------------
    // C-001 — Protection ON: marker absent (OS enforcement confirmed)
    // -----------------------------------------------------------------------

    /**
     * Activates ComposeShield, captures a screenshot via [Screenshot.capture],
     * and asserts the [SHIELD_TEST_SECRET_001] marker is absent from the known region.
     *
     * Passes when: the OS honored the protection request and the content is not detectable.
     * Fails when: the marker is detectable — the OS did NOT honor the protection.
     */
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

    // -----------------------------------------------------------------------
    // C-002 — Negative control: protection OFF → marker present
    // -----------------------------------------------------------------------

    /**
     * Deactivates ComposeShield, captures a screenshot, and asserts the marker IS present.
     * This proves the marker-detection mechanism works (no false negatives or false positives).
     */
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

    // -----------------------------------------------------------------------
    // C-003 — Shield disabled after active → marker returns
    // -----------------------------------------------------------------------

    @Test
    fun screenshotAfterShieldDisabled_markerPresent() {
        activityRule.scenario.onActivity { it.enableShield(true) }
        // Shield was on — verify protection was applied
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

    // -----------------------------------------------------------------------
    // I-001 — Double-enable idempotency
    // -----------------------------------------------------------------------

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

    // -----------------------------------------------------------------------
    // R-001 — Cleanup after scope ends → marker returns
    // -----------------------------------------------------------------------

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

    // -----------------------------------------------------------------------
    // Detection helpers — marker-absent assertion (FR-010)
    // -----------------------------------------------------------------------

    /**
     * Captures the current screen as a [Bitmap] using the in-process
     * [androidx.test.runner.screenshot.Screenshot] API — trustworthy proof
     * of OS-level enforcement (spec Assumption 3).
     */
    private fun captureScreenshot(): Bitmap = androidx.test.runner.screenshot.Screenshot.capture().bitmap

    /** Returns a brief description of the bitmap for failure messages. */
    private fun Bitmap.describe(): String {
        val cx = width / 4 + 25
        val cy = height / 4 + 25
        return "bitmap=${width}x$height, sample_center=($cx,$cy), " +
            "center_pixel=#${Integer.toHexString(getPixel(cx, cy)).uppercase()}"
    }
}
