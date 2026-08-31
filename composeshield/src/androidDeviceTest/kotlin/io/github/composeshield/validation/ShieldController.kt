package io.github.composeshield.validation

import android.view.Window
import android.view.WindowManager

internal class ShieldController(
    private val window: Window,
) {
    private var released = false

    fun activate() {
        check(!released) { "ShieldController has been released; create a new instance." }
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }

    fun deactivate() {
        check(!released) { "ShieldController has been released; create a new instance." }
        window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }

    fun release() {
        window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        released = true
    }
}
