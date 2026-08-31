package io.github.composeshield.internal

import kotlinx.cinterop.ExperimentalForeignApi
import platform.CoreGraphics.CGRectMake
import platform.UIKit.UIViewController
import platform.UIKit.addChildViewController
import platform.UIKit.childViewControllers
import platform.UIKit.didMoveToParentViewController
import platform.UIKit.removeFromParentViewController
import platform.UIKit.willMoveToParentViewController
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalForeignApi::class)
class ContainmentTest {
    @Test
    fun `a contained controller reports its parent`() {
        val parent = UIViewController(nibName = null, bundle = null)
        val child = UIViewController(nibName = null, bundle = null)

        parent.contain(child)

        assertNotNull(
            child.parentViewController,
            "a null parent means appearance callbacks, the responder chain, and safe-area insets " +
                "are all silently broken while the view still renders",
        )
        assertEquals(parent, child.parentViewController)
    }

    @Test
    fun `a contained controller's view joins the parent's hierarchy`() {
        val parent = UIViewController(nibName = null, bundle = null)
        val child = UIViewController(nibName = null, bundle = null)

        parent.contain(child)

        assertTrue(parent.view.subviews.any { it == child.view })
        assertTrue(parent.childViewControllers.any { it == child })
    }

    @Test
    fun `content reparented into a secure container keeps its containment`() {
        val parent = UIViewController(nibName = null, bundle = null)
        val child = UIViewController(nibName = null, bundle = null)
        parent.contain(child)

        val container = SecureContainer.create() ?: return
        container.enclose(child.view)

        assertEquals(
            parent,
            child.parentViewController,
            "reparenting the view must not sever the controller relationship",
        )
    }

    @Test
    fun `removing containment detaches the controller cleanly`() {
        val parent = UIViewController(nibName = null, bundle = null)
        val child = UIViewController(nibName = null, bundle = null)
        parent.contain(child)

        child.willMoveToParentViewController(null)
        child.view.removeFromSuperview()
        child.removeFromParentViewController()

        assertNull(child.parentViewController)
        assertTrue(parent.childViewControllers.none { it == child })
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun UIViewController.contain(child: UIViewController) {
    addChildViewController(child)
    child.view.setFrame(CGRectMake(0.0, 0.0, WIDTH, HEIGHT))
    view.addSubview(child.view)
    child.didMoveToParentViewController(this)
}

private const val WIDTH = 320.0
private const val HEIGHT = 480.0
