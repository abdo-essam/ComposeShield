package io.github.composeshield.internal

import kotlinx.cinterop.ExperimentalForeignApi
import platform.UIKit.UITextField
import platform.UIKit.UIView

internal class SecureContainer private constructor(
    private val owner: UITextField,
    private val canvas: UIView,
) {
    @OptIn(ExperimentalForeignApi::class)
    fun enclose(content: UIView): Boolean {
        val parent = content.superview ?: return false

        canvas.setFrame(content.frame)
        canvas.setAutoresizingMask(content.autoresizingMask)

        content.removeFromSuperview()
        canvas.addSubview(content)
        parent.addSubview(canvas)
        return true
    }

    @OptIn(ExperimentalForeignApi::class)
    fun release(content: UIView) {
        val parent = canvas.superview ?: return

        content.removeFromSuperview()
        canvas.removeFromSuperview()

        content.setFrame(canvas.frame)
        parent.addSubview(content)
    }

    internal companion object {
        private const val CANVAS_CLASS_MARKER = "CanvasView"

        fun create(): SecureContainer? {
            val field = UITextField()
            field.setSecureTextEntry(true)

            field.layoutIfNeeded()

            val canvas =
                field.subviews
                    .filterIsInstance<UIView>()
                    .firstOrNull { it.isCanvasLike() }
                    ?: run {
                        println(
                            "[ComposeShield] WARNING: Failed to locate secure CanvasView in UITextField subviews. " +
                                "SecureContainer protection is unavailable on this iOS runtime.",
                        )
                        return null
                    }

            canvas.removeFromSuperview()
            return SecureContainer(owner = field, canvas = canvas)
        }

        private fun UIView.isCanvasLike(): Boolean = this::class.simpleName?.contains(CANVAS_CLASS_MARKER) == true
    }
}
