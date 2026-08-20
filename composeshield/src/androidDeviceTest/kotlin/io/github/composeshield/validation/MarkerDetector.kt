@file:Suppress("MagicNumber")

package io.github.composeshield.validation

import android.graphics.Bitmap
import android.graphics.Color

/**
 * Shared marker-detection logic used by all validation tests.
 *
 * Samples a [Bitmap] at the known [ShieldValidationActivity] marker region and
 * returns whether the [ShieldValidationActivity.MARKER_CONTENT_DESC] marker is
 * detectable. See [ScreenshotValidationTest] KDoc for the detection rationale.
 */
internal object MarkerDetector {
    /**
     * Returns `true` if the [SHIELD_TEST_SECRET_001] marker is detectable
     * in [bitmap]; `false` if it is absent (protection applied or marker not rendered).
     *
     * Samples a 50×50 px region at the position where [ShieldValidationActivity]
     * renders the marker. At least 10% of pixels must match [MARKER_COLOR] for
     * the marker to be considered visible — tolerates minor JPEG artefacts.
     */
    fun isMarkerVisible(bitmap: Bitmap): Boolean {
        val regionX = bitmap.width / 4
        val regionY = bitmap.height / 4
        val sampleSize = minOf(50, bitmap.width - regionX, bitmap.height - regionY)

        if (sampleSize <= 0) return false

        var matchCount = 0
        for (dx in 0 until sampleSize) {
            for (dy in 0 until sampleSize) {
                if (isMarkerColor(bitmap.getPixel(regionX + dx, regionY + dy))) matchCount++
            }
        }

        val threshold = (sampleSize * sampleSize * 0.10).toInt()
        return matchCount > threshold
    }

    /**
     * Returns `true` if [pixel] matches the marker fill color within a ±30 tolerance
     * per channel (accommodates color-space conversions and JPEG compression).
     */
    private fun isMarkerColor(pixel: Int): Boolean {
        val r = Color.red(pixel)
        val g = Color.green(pixel)
        val b = Color.blue(pixel)
        return r in 195..255 && g in 0..80 && b in 0..80
    }
}
