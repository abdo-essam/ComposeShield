package io.github.composeshield.validation

import android.view.Window
import android.view.WindowManager

/**
 * Thin adapter that applies or removes [WindowManager.LayoutParams.FLAG_SECURE]
 * on the host window, mirroring what ComposeShield does in production.
 *
 * Using a dedicated controller keeps test code free of `WindowManager` details
 * and makes the activation surface easy to swap when the real library API stabilises.
 */
internal class ShieldController(
    private val window: Window,
) {
    private var released = false

    /** Applies FLAG_SECURE — marks the window as protected. */
    fun activate() {
        check(!released) { "ShieldController has been released; create a new instance." }
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }

    /** Removes FLAG_SECURE — content becomes capturable again. */
    fun deactivate() {
        check(!released) { "ShieldController has been released; create a new instance." }
        window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }

    /**
     * Removes FLAG_SECURE and marks this controller as released.
     * Subsequent calls to [activate] or [deactivate] will throw.
     */
    fun release() {
        window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        released = true
    }
}
