package io.github.composeshield.internal

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGRectMake
import platform.UIKit.UITextField
import platform.UIKit.UIView
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Contract test C14 & CI compatibility test — the secure container is obtained by detecting
 * UIKit's internal `CanvasView` in the real rendering hierarchy.
 *
 * The mechanism relies on an undocumented view that Apple can rename or restructure in any release.
 * In CI running on an iOS Simulator, this must be a hard failure: if Apple renames or removes
 * `CanvasView`, the test fails fast with an actionable message ("CanvasView was not found") before
 * releasing broken protection into production.
 *
 * Whether the render server then withholds the content cannot be checked here — the Simulator writes
 * the framebuffer directly and bypasses that path entirely (quickstart M1/M2, device only).
 */
@OptIn(ExperimentalForeignApi::class)
class SecureContainerTest {
    @Test
    fun `C14 - CanvasView detection succeeds on CI iOS Simulator runtime`() {
        val field = UITextField()
        field.setSecureTextEntry(true)
        field.layoutIfNeeded()

        val subviews = field.subviews.filterIsInstance<UIView>()
        val subviewNames = subviews.map { it::class.simpleName ?: it.description ?: "unknown" }

        val canvas = SecureContainer.findCanvasView(field)
        assertNotNull(
            canvas,
            "CanvasView was not found in UITextField subviews (found: $subviewNames). " +
                "Apple may have renamed or removed UIKit internal CanvasView in this iOS release/SDK.",
        )

        val container =
            assertNotNull(
                SecureContainer.create(),
                "CanvasView was not found: SecureContainer.create() returned null on iOS Simulator.",
            )

        val parent = UIView(frame = CGRectMake(0.0, 0.0, WIDTH, HEIGHT))
        val content = UIView(frame = CGRectMake(0.0, 0.0, WIDTH, HEIGHT))
        parent.addSubview(content)

        assertTrue(container.enclose(content), "a laid-out view with a superview must be enclosable")
    }

    @Test
    fun `when CanvasView is absent findCanvasView returns null`() {
        val emptyField = UITextField()
        emptyField.subviews.filterIsInstance<UIView>().forEach { it.removeFromSuperview() }

        val canvas = SecureContainer.findCanvasView(emptyField)
        assertNull(canvas, "When CanvasView is absent, findCanvasView must return null")
    }

    @Test
    fun `enclosing reparents content under the canvas without disturbing the view above`() {
        val container =
            assertNotNull(
                SecureContainer.create(),
                "CanvasView was not found: SecureContainer.create() returned null",
            )

        val parent = UIView(frame = CGRectMake(0.0, 0.0, WIDTH, HEIGHT))
        val content = UIView(frame = CGRectMake(0.0, 0.0, WIDTH, HEIGHT))
        parent.addSubview(content)

        container.enclose(content)

        assertEquals(1, parent.subviews.size, "the canvas substitutes for content, it does not join it")
        assertFalse(
            parent.subviews.any { it == content },
            "content must sit inside the canvas — left as a sibling it would render unprotected " +
                "while every test still passed",
        )

        val canvas = parent.subviews.first() as UIView
        assertTrue(canvas.subviews.any { it == content })
    }

    @Test
    fun `enclosing preserves the content frame so layout is unchanged`() {
        val container =
            assertNotNull(
                SecureContainer.create(),
                "CanvasView was not found: SecureContainer.create() returned null",
            )

        val parent = UIView(frame = CGRectMake(0.0, 0.0, WIDTH, HEIGHT))
        val content = UIView(frame = CGRectMake(0.0, 0.0, WIDTH, HEIGHT))
        parent.addSubview(content)

        container.enclose(content)

        val canvas = parent.subviews.first() as UIView
        canvas.frame.useContents {
            assertEquals(WIDTH, size.width, "the canvas takes the frame content occupied")
            assertEquals(HEIGHT, size.height)
        }
    }

    @Test
    fun `releasing restores content to its original parent`() {
        val container =
            assertNotNull(
                SecureContainer.create(),
                "CanvasView was not found: SecureContainer.create() returned null",
            )

        val parent = UIView(frame = CGRectMake(0.0, 0.0, WIDTH, HEIGHT))
        val content = UIView(frame = CGRectMake(0.0, 0.0, WIDTH, HEIGHT))
        parent.addSubview(content)

        container.enclose(content)
        container.release(content)

        assertEquals(1, parent.subviews.size)
        assertEquals(content, parent.subviews.first(), "content must return to where it started")
    }

    @Test
    fun `repeated protect and release cycles leave no residual views`() {
        val parent = UIView(frame = CGRectMake(0.0, 0.0, WIDTH, HEIGHT))
        val content = UIView(frame = CGRectMake(0.0, 0.0, WIDTH, HEIGHT))
        parent.addSubview(content)

        repeat(CYCLES) {
            val container =
                assertNotNull(
                    SecureContainer.create(),
                    "CanvasView was not found: SecureContainer.create() returned null",
                )
            container.enclose(content)
            container.release(content)
        }

        assertEquals(1, parent.subviews.size, "each cycle must remove the canvas it added")
        assertEquals(content, parent.subviews.first())
    }

    @Test
    fun `enclosing a view with no superview fails softly`() {
        val container =
            assertNotNull(
                SecureContainer.create(),
                "CanvasView was not found: SecureContainer.create() returned null",
            )

        assertFalse(
            container.enclose(UIView(frame = CGRectMake(0.0, 0.0, WIDTH, HEIGHT))),
            "protection requested before layout must report false, never throw",
        )
    }

    private companion object {
        const val WIDTH = 320.0
        const val HEIGHT = 480.0
        const val CYCLES = 10
    }
}
