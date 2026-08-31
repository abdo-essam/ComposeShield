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

internal class AppSwitcher {
    private var overlay: UIVisualEffectView? = null
    private var resignObserver: NSObjectProtocol? = null
    private var activeObserver: NSObjectProtocol? = null

    private var enabled = false

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

    private fun hideOverlay() {
        overlay?.removeFromSuperview()
        overlay = null
    }
}
