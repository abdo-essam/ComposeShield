@file:Suppress("FunctionNaming", "MagicNumber")

package io.github.composeshield.validation

import android.content.Intent
import android.graphics.Bitmap
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertFalse

@RunWith(AndroidJUnit4::class)
class AppSwitcherValidationTest {
    @get:Rule
    val activityRule = ActivityScenarioRule(ShieldValidationActivity::class.java)

    private val device: UiDevice by lazy {
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    }

    @Test
    fun appSwitcherWithProtectionOn_markerAbsent() {
        activityRule.scenario.onActivity { it.enableShield(true) }

        device.pressRecentApps()
        device.waitForIdle(IDLE_TIMEOUT_MS)

        val thumbnail = captureScreenshot()
        val markerVisible = MarkerDetector.isMarkerVisible(thumbnail)

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
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
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
