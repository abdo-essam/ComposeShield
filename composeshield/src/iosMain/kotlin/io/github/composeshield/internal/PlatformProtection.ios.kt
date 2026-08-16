package io.github.composeshield.internal

import io.github.composeshield.Capability
import io.github.composeshield.SupportLevel
import io.github.composeshield.SupportLevel.Unsupported.Reason
import kotlinx.coroutines.flow.Flow
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.UIKit.UISceneDidDisconnectNotification
import platform.UIKit.UIWindowScene
import platform.darwin.NSObjectProtocol

/**
 * iOS's implementation of the platform boundary.
 *
 * Prevention uses internal secure container reparenting. Detection uses official UIKit
 * screen capture notifications.
 */
internal class IosPlatformProtection : PlatformProtection {
    private val detection = CaptureDetection()
    private val screenshots = ScreenshotEvents()
    private val appSwitcher = AppSwitcher()
    private val foreground = ForegroundEvents()

    /**
     * Retained observer token for scene teardown.
     *
     * The notification center does not keep the block-based observer's block alive on its own, so
     * this must be stored for the instance's lifetime or the observer silently stops firing.
     */
    @Suppress("unused", "UnusedPrivateMember")
    private val sceneDisconnectObserver: NSObjectProtocol

    init {
        sceneDisconnectObserver =
            NSNotificationCenter.defaultCenter.addObserverForName(
                name = UISceneDidDisconnectNotification,
                `object` = null,
                queue = NSOperationQueue.mainQueue,
            ) { note ->
                // For UIScene lifecycle notifications the posting scene is the notification's object.
                (note?.`object` as? UIWindowScene)?.let(::dismissScene)
            }
    }

    /** Live secure containers, one per protected window, so protection can be undone precisely. */
    private val containers = mutableMapOf<WindowKey, SecureContainer>()

    /**
     * `false` — unlike Android, the screenshot notification fires regardless of what the window is
     * doing, so prevention and screenshot events coexist here.
     */
    override val preventionPrecludesScreenshotEvents: Boolean = false

    override fun applyProtection(
        window: WindowKey,
        capabilities: Set<Capability>,
    ): ProtectionOutcome {
        if (containers.containsKey(window)) return ProtectionOutcome.Applied
        val target = windowFor(window) ?: return ProtectionOutcome.Deferred
        val content = target.rootViewController?.view ?: return ProtectionOutcome.Deferred
        val container = SecureContainer.create() ?: return ProtectionOutcome.Failed
        if (!container.enclose(content)) return ProtectionOutcome.Failed
        containers[window] = container
        return ProtectionOutcome.Applied
    }

    override fun clearProtection(window: WindowKey) {
        val container = containers.remove(window) ?: return
        val content = windowFor(window)?.rootViewController?.view ?: return
        container.release(content)
    }

    override fun observeCaptureState(): Flow<PlatformCaptureReading> = detection.readings()

    override fun observeScreenshotEvents(): Flow<Unit> = screenshots.events()

    override fun observeForegroundEvents(): Flow<Unit> = foreground.events()

    override fun applyAppSwitcherProtection(
        window: WindowKey,
        enabled: Boolean,
    ) {
        appSwitcher.apply(enabled)
    }

    override fun platformSupport(capability: Capability): SupportLevel =
        when (capability) {
            Capability.ScreenshotPrevention, Capability.RecordingPrevention -> SupportLevel.Supported
            Capability.CaptureDetection, Capability.ScreenshotEvents -> SupportLevel.Supported
            Capability.AppSwitcherProtection -> SupportLevel.Supported
        }
}

internal actual fun createPlatformProtection(): PlatformProtection = IosPlatformProtection()
