@file:Suppress("MagicNumber")

package io.github.composeshield.validation

import android.graphics.Bitmap
import android.graphics.Color

internal object MarkerDetector {
    fun isMarkerVisible(bitmap: Bitmap?): Boolean {
        if (bitmap == null) return false
        val sampleSize = 60
        val regionX = (bitmap.width - sampleSize) / 2
        val regionY = (bitmap.height - sampleSize) / 2

        if (regionX < 0 || regionY < 0) return false

        var matchCount = 0
        for (dx in 0 until sampleSize) {
            for (dy in 0 until sampleSize) {
                if (isMarkerColor(bitmap.getPixel(regionX + dx, regionY + dy))) matchCount++
            }
        }

        val threshold = (sampleSize * sampleSize * 0.10).toInt()
        return matchCount > threshold
    }

    private fun isMarkerColor(pixel: Int): Boolean {
        val r = Color.red(pixel)
        val g = Color.green(pixel)
        val b = Color.blue(pixel)
        return r in 195..255 && g in 0..80 && b in 0..80
    }
}
