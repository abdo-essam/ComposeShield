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

/**
 * Asserts that a hosted controller is fully contained in the UIKit hierarchy.
 *
 * Adding a child controller's view without the `addChild` / `didMove(toParent:)` calls around it
 * causes UIKit lifecycle issues: appearance callbacks never fire, the responder chain skips the child,
 * and safe-area insets do not propagate.
 *
 * Identity is compared with `==` rather than `===`. Objective-C interop hands out a fresh Kotlin
 * wrapper per property read, so referential identity is not preserved across a `parentViewController`
 * access even when the underlying object is the same one. `==` bridges to `isEqual:`, which is.
 */
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
        // The interaction the test exists for: enclosing moves the *view*, and a view move must not
        // disturb controller containment. If it did, protecting a screen would break the keyboard on
        // it — an iOS-only regression no Android test would catch.
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

/**
 * The full containment sequence: `addChild` → `addSubview` → layout → `didMove(toParent:)`.
 *
 * Declared here rather than reused from `iosMain` because the production path hosts a
 * `ComposeUIViewController`, which cannot be constructed in a unit test. The sequence itself is what
 * is under test.
 */
@OptIn(ExperimentalForeignApi::class)
private fun UIViewController.contain(child: UIViewController) {
    addChildViewController(child)
    child.view.setFrame(CGRectMake(0.0, 0.0, WIDTH, HEIGHT))
    view.addSubview(child.view)
    child.didMoveToParentViewController(this)
}

private const val WIDTH = 320.0
private const val HEIGHT = 480.0
