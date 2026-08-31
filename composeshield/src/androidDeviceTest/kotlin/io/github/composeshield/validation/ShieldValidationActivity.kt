@file:Suppress("MagicNumber")

package io.github.composeshield.validation

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.ComponentActivity

class ShieldValidationActivity : ComponentActivity() {
    companion object {
        val MARKER_COLOR: Int = Color.rgb(255, 68, 68)

        const val MARKER_CONTENT_DESC = "SHIELD_TEST_SECRET_001"
    }

    private lateinit var markerView: View
    private lateinit var shieldController: ShieldController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = FrameLayout(this)
        root.setBackgroundColor(Color.BLACK)

        markerView = buildMarkerView()
        root.addView(markerView, buildMarkerLayoutParams())

        setContentView(root)
        shieldController = ShieldController(window)
    }

    fun enableShield(enabled: Boolean) {
        if (enabled) shieldController.activate() else shieldController.deactivate()
    }

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
