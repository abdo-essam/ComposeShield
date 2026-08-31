package io.github.composeshield.internal

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGRectMake
import platform.UIKit.UIView
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalForeignApi::class)
class SecureContainerTest {
    @Test
    fun `C14 - a container is obtained or the mechanism reports itself unavailable`() {
        val container = SecureContainer.create()

        if (container == null) {
            return
        }

        val parent = UIView(frame = CGRectMake(0.0, 0.0, WIDTH, HEIGHT))
        val content = UIView(frame = CGRectMake(0.0, 0.0, WIDTH, HEIGHT))
        parent.addSubview(content)

        assertTrue(container.enclose(content), "a laid-out view with a superview must be enclosable")
    }

    @Test
    fun `enclosing reparents content under the canvas without disturbing the view above`() {
        val container = SecureContainer.create() ?: return

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
        val container = SecureContainer.create() ?: return

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
        val container = SecureContainer.create() ?: return

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
            val container = SecureContainer.create() ?: return
            container.enclose(content)
            container.release(content)
        }

        assertEquals(1, parent.subviews.size, "each cycle must remove the canvas it added")
        assertEquals(content, parent.subviews.first())
    }

    @Test
    fun `enclosing a view with no superview fails softly`() {
        val container = SecureContainer.create() ?: return

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
