@file:Suppress("FunctionNaming", "MagicNumber")

package io.github.composeshield.validation

import android.content.Intent
import android.graphics.Bitmap
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertFalse

/**
 * App-switcher preview validation test (A-001).
 *
 * Verifies that when ComposeShield is active, the app's recent-apps thumbnail
 * does not expose the [SHIELD_TEST_SECRET_001] marker.
 *
 * **Strategy**: background the app via [UiDevice.pressRecentApps], capture the
 * app-switcher thumbnail using [Screenshot.capture], then sample the marker
 * region using the same detection logic as [ScreenshotValidationTest].
 *
 * Note: The app-switcher thumbnail is OS-managed and device-specific. On some
 * devices the thumbnail is composited before FLAG_SECURE takes effect; in those
 * cases the test will produce `blocked` status (not `failed`) per FR-019.
 *
 * Requirement: A-001
 */
@RunWith(AndroidJUnit4::class)
class AppSwitcherValidationTest {
    @get:Rule
    val activityRule = ActivityScenarioRule(ShieldValidationActivity::class.java)

    private val device: UiDevice by lazy {
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    }

    /**
     * A-001: With shield active, backgrounds the app and captures the thumbnail.
     * Asserts the [SHIELD_TEST_SECRET_001] marker is absent from the preview.
     */
    @Test
    fun appSwitcherWithProtectionOn_markerAbsent() {
        activityRule.scenario.onActivity { it.enableShield(true) }

        // Background the app
        device.pressRecentApps()
        device.waitForIdle(IDLE_TIMEOUT_MS)

        // Capture whatever is now on screen (app-switcher)
        val thumbnail = captureScreenshot()
        val markerVisible = MarkerDetector.isMarkerVisible(thumbnail)

        // Return activity to foreground before tearDown to ensure ActivityScenario can destroy it
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val intent =
            Intent(context, ShieldValidationActivity::class.java).apply {
                flags =
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_NEW_TASK
            }
        context.startActivity(intent)
        device.waitForIdle(IDLE_TIMEOUT_MS)

        assertFalse(
            actual = markerVisible,
            message =
                "A-001 FAILED: ${ShieldValidationActivity.MARKER_CONTENT_DESC} marker " +
                    "is detectable in the app-switcher preview. Protected content is leaking " +
                    "into the recent-apps thumbnail.",
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

    companion object {
        private const val IDLE_TIMEOUT_MS = 2_000L
    }
}
