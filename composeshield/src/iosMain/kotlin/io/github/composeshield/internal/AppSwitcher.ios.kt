package io.github.composeshield.internal

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIApplicationDidBecomeActiveNotification
import platform.UIKit.UIApplicationWillResignActiveNotification
import platform.UIKit.UIBlurEffect
import platform.UIKit.UIBlurEffectStyle
import platform.UIKit.UIViewAutoresizingFlexibleHeight
import platform.UIKit.UIViewAutoresizingFlexibleWidth
import platform.UIKit.UIVisualEffectView
import platform.darwin.NSObjectProtocol

/**
 * Covers the app's content with a blur while it is backgrounded, so the task-switcher snapshot
 * reveals nothing.
 *
 * Officially sanctioned on iOS — this is ordinary view work, with none of the App Review exposure
 * that iOS *prevention* carries — so it never requires the unsanctioned-mechanism opt-in.
 *
 * **Timing is the whole problem.** The system photographs the app for the switcher shortly after it
 * resigns active, so the overlay must be installed on `willResignActive` and not on any later
 * lifecycle callback. Installing it at "did enter background" loses the race and the snapshot is
 * captured unobscured — the failure looks like the feature simply not working, with nothing in the
 * logs to explain it.
 */
internal class AppSwitcher {
    private var overlay: UIVisualEffectView? = null
    private var resignObserver: NSObjectProtocol? = null
    private var activeObserver: NSObjectProtocol? = null

    /** Whether the application currently wants its switcher snapshot obscured. */
    private var enabled = false

    /**
     * Takes no window, unlike its Android counterpart.
     *
     * The switcher snapshot is of the whole application on iOS, not of one window, and the overlay
     * is installed on the scene's key window whichever window asked for it. Accepting a [WindowKey]
     * here would imply a per-window granularity the platform does not have.
     */
    fun apply(enabled: Boolean) {
        this.enabled = enabled

        if (enabled) {
            observeLifecycle()
        } else {
            stopObserving()
            hideOverlay()
        }
    }

    private fun observeLifecycle() {
        if (resignObserver != null) return

        val center = NSNotificationCenter.defaultCenter
        resignObserver =
            center.addObserverForName(
                name = UIApplicationWillResignActiveNotification,
                `object` = null,
                queue = NSOperationQueue.mainQueue,
            ) { _ -> showOverlay() }

        activeObserver =
            center.addObserverForName(
                name = UIApplicationDidBecomeActiveNotification,
                `object` = null,
                queue = NSOperationQueue.mainQueue,
            ) { _ -> hideOverlay() }
    }

    private fun stopObserving() {
        val center = NSNotificationCenter.defaultCenter
        resignObserver?.let { center.removeObserver(it) }
        activeObserver?.let { center.removeObserver(it) }
        resignObserver = null
        activeObserver = null
    }

    /**
     * Installs the blur, reusing any existing one to prevent accumulation across cycles.
     */
    @OptIn(ExperimentalForeignApi::class)
    private fun showOverlay() {
        if (!enabled || overlay != null) return
        val window = activeWindow() ?: return

        val style = UIBlurEffectStyle.UIBlurEffectStyleSystemMaterial
        val blur = UIVisualEffectView(effect = UIBlurEffect.effectWithStyle(style))
        blur.setFrame(window.bounds)
        blur.setAutoresizingMask(UIViewAutoresizingFlexibleWidth or UIViewAutoresizingFlexibleHeight)

        window.addSubview(blur)
        overlay = blur
    }

    /** Removes the blur, leaving no residual artifact on return to foreground. */
    private fun hideOverlay() {
        overlay?.removeFromSuperview()
        overlay = null
    }
}
