package io.github.composeguard.internal

import kotlinx.cinterop.ExperimentalForeignApi
import platform.UIKit.UITextField
import platform.UIKit.UIView
import platform.UIKit.addSubview
import platform.UIKit.subviews

/**
 * Obtains a view whose contents the render server withholds from screen capture.
 *
 * **This is the unsanctioned mechanism the whole opt-in flow exists for.** Apple ships no prevention
 * API and the omission is deliberate — a Frameworks engineer has stated publicly that blocking
 * screenshots is unsupported, reasoning that a user can photograph the screen with a second device
 * regardless. This relies on behaviour Apple has not blessed, and Developer Technical Support has
 * said using a secure text field as a wrapping container "is not its intended purpose", invoking
 * App Review Guideline 2.5.1.
 *
 * ### How it works, and why the direction matters
 *
 * A `UITextField` with `isSecureTextEntry` set owns a private canvas subview that the render server
 * excludes from capture. The mechanism moves that canvas **out** of the text field and into the
 * app's own hierarchy, then reparents the window's content inside it.
 *
 * The direction is the opposite of the obvious reading, and getting it backwards was an assumption
 * this design had to correct. Content is not placed *under* a live text field — the canvas is lifted
 * out and adopted. Two consequences follow:
 *
 * - The text field is **never added to the hierarchy** but must be **retained anyway**, because it
 *   owns the layer mask that makes the canvas secure. Releasing it silently un-secures the
 *   container, producing content that looks protected and is not.
 * - Trait propagation survives, because the canvas ends up in the normal hierarchy rather than
 *   buried inside a detached view. This is why capture *detection* has no conflict with prevention
 *   (research.md R2) — the two subsystems are structurally independent.
 *
 * The secure behaviour is a layer attribute pushed to the render server, not a property of where the
 * view sits, which is what makes the move safe at all.
 *
 * ### Why every failure path returns null
 *
 * The canvas class name is undocumented and version-dependent, and iOS 17 broke index-based sublayer
 * lookups. Matching is by class-name **substring, never by index**, and every failure returns `null`
 * rather than throwing (FR-021). The caller reports that as
 * [io.github.composeguard.SupportLevel.Unsupported] with `MechanismUnavailable`, which triggers the
 * declared [io.github.composeguard.FailurePosture] — an honest "this stopped working" rather than a
 * crash or, far worse, a false claim of protection.
 *
 * Cannot be verified in the Simulator, which writes the emulated framebuffer directly and bypasses
 * the render-server path carrying the protection. Device-only (quickstart M1/M2).
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
     * above is undisturbed and layout is unchanged — FR-006 requires protected content to render
     * identically. Returns `false` if [content] has no superview to be substituted within, which
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
     * hierarchy across a protect/unprotect cycle would accumulate one per cycle (FR-016).
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
         * Matched as a substring because the exact name is undocumented and has changed across iOS
         * versions (`_UITextLayoutCanvasView` on iOS 15+, different on 12–14). Never matched by
         * index: iOS 17 reordered these subviews, and an index-based lookup would silently adopt a
         * view that is *not* secure rather than fail honestly.
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

            // Force the field to build its private view tree — before a layout pass the canvas does
            // not exist yet and the search below would find nothing.
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
         * `::class.simpleName` is the only introspection Kotlin/Native offers, and it reads a name
         * the compiler emitted rather than consulting a reflection runtime — no `kotlin-reflect`
         * involved, so this stays inside Principle V.
         */
        private fun UIView.isCanvasLike(): Boolean = this::class.simpleName?.contains(CANVAS_CLASS_MARKER) == true
    }
}
