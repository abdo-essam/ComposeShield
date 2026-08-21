@file:Suppress("MagicNumber")

package io.github.composeshield.validation

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.ComponentActivity

/**
 * Minimal host activity for instrumentation tests on Firebase Test Lab.
 *
 * Renders the [SHIELD_TEST_SECRET_001] marker — a coloured block at a fixed
 * screen position — so [ScreenshotValidationTest] and [AppSwitcherValidationTest]
 * can assert whether it appears in captured bitmaps.
 *
 * The marker is intentionally bright and distinct ([MARKER_COLOR]) so it cannot
 * be confused with OS-replacement output (black frames, blur, etc.).
 */
class ShieldValidationActivity : ComponentActivity() {
    companion object {
        /** Marker fill color: bright red (#FF4444). Must match detection in test files. */
        val MARKER_COLOR: Int = Color.rgb(255, 68, 68)

        /** Accessibility label exposed to test code for view lookup. */
        const val MARKER_CONTENT_DESC = "SHIELD_TEST_SECRET_001"
    }

    private lateinit var markerView: View
    private lateinit var shieldController: ShieldController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = FrameLayout(this)
        root.setBackgroundColor(Color.BLACK)

        // Marker block — 150×150 dp at (width/4, height/4) so the test's
        // region sampler hits it reliably across different screen sizes.
        markerView = buildMarkerView()
        root.addView(markerView, buildMarkerLayoutParams())

        setContentView(root)
        shieldController = ShieldController(window)
    }

    /** Enables or disables ComposeShield protection on this window. */
    fun enableShield(enabled: Boolean) {
        if (enabled) shieldController.activate() else shieldController.deactivate()
    }

    /** Releases the shield scope entirely (used by R-001). */
    fun releaseShield() {
        shieldController.release()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::shieldController.isInitialized) {
            shieldController.release()
        }
    }

    private fun buildMarkerView(): TextView =
        TextView(this).apply {
            text = MARKER_CONTENT_DESC
            contentDescription = MARKER_CONTENT_DESC
            setBackgroundColor(MARKER_COLOR)
            setTextColor(Color.WHITE)
            textSize = 12f
            gravity = Gravity.CENTER
            setPadding(8, 8, 8, 8)
        }

    private fun buildMarkerLayoutParams(): FrameLayout.LayoutParams {
        val sizePx = dpToPx(200)
        return FrameLayout.LayoutParams(sizePx, sizePx).apply {
            gravity = Gravity.CENTER
        }
    }

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()
}
