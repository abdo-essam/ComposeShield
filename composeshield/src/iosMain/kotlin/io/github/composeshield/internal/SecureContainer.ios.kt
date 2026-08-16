package io.github.composeshield.internal

import kotlinx.cinterop.ExperimentalForeignApi
import platform.UIKit.UITextField
import platform.UIKit.UIView

/**
 * Obtains a view whose contents the render server withholds from screen capture.
 *
 * A `UITextField` with `isSecureTextEntry` set owns a private canvas subview the render server
 * excludes from capture. This lifts that canvas **out** of the text field into the app's own
 * hierarchy, then reparents the window's content inside it.
 *
 * Every failure path returns `null` rather than throwing. The caller reports that as
 * [io.github.composeshield.SupportLevel.Unsupported] with `MechanismUnavailable` — an honest
 * "this stopped working" rather than a false claim of protection.
 *
 * Cannot be verified in the Simulator, which bypasses the render-server path carrying the
 * protection. Device-only.
 */
internal class SecureContainer private constructor(
    /**
     * Retained deliberately, and never added to the hierarchy.
     *
     * This reference is the only thing keeping the field alive, and the field owns the layer mask
     * that makes [canvas] secure. Dropping it un-secures the container with no visible symptom,
     * which is why it is a stored property rather than a local in [create].
     */
    private val owner: UITextField,
    /** The adopted canvas view, now in the normal hierarchy. Content placed here is withheld. */
    private val canvas: UIView,
) {
    /**
     * Moves [content] inside the secure canvas, in place.
     *
     * The canvas takes [content]'s position in its superview and adopts it, so the view hierarchy
     * above is undisturbed and layout is unchanged, as protected content must render identically to
     * an unwrapped call. Returns `false` if [content] has no superview to substitute within, which
     * happens when protection is requested before the window is laid out.
     */
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

    /**
     * Returns [content] to where it was and dismantles the container.
     *
     * The canvas is removed rather than merely emptied: leaving a detached secure view in the
     * hierarchy across a protect/unprotect cycle would accumulate one per cycle.
     */
    @OptIn(ExperimentalForeignApi::class)
    fun release(content: UIView) {
        val parent = canvas.superview ?: return

        content.removeFromSuperview()
        canvas.removeFromSuperview()

        content.setFrame(canvas.frame)
        parent.addSubview(content)
    }

    internal companion object {
        /**
         * Substring identifying the secure canvas view.
         *
         * A substring because the exact name is undocumented and has changed across iOS versions
         * (`_UITextLayoutCanvasView` on iOS 15+). **Never matched by index**: iOS 17 reordered these
         * subviews, and an index-based lookup would silently adopt a view that is *not* secure.
         */
        private const val CANVAS_CLASS_MARKER = "CanvasView"

        /**
         * Builds a secure container, or returns `null` where the mechanism is unavailable.
         *
         * `null` is an expected outcome on an OS version whose internals have moved, not an error.
         */
        fun create(): SecureContainer? {
            val field = UITextField()
            field.setSecureTextEntry(true)

            field.layoutIfNeeded()

            val canvas =
                field.subviews
                    .filterIsInstance<UIView>()
                    .firstOrNull { it.isCanvasLike() }
                    ?: return null

            canvas.removeFromSuperview()
            return SecureContainer(owner = field, canvas = canvas)
        }

        /**
         * Matches by class-name substring — never by index, because iOS 17 reordered subviews
         * and an index-based lookup would silently adopt a non-secure view.
         */
        private fun UIView.isCanvasLike(): Boolean = this::class.simpleName?.contains(CANVAS_CLASS_MARKER) == true
    }
}
