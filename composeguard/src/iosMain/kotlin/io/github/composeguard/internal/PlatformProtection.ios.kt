package io.github.composeguard.internal

import io.github.composeguard.Capability
import io.github.composeguard.SupportLevel
import io.github.composeguard.SupportLevel.Unsupported.Reason
import kotlinx.coroutines.flow.Flow

/**
 * iOS's implementation of the platform boundary.
 *
 * The support picture is the mirror image of Android's. **Detection is fully supported and
 * prevention is not sanctioned at all** — where on Android prevention is trivially available and
 * detection is gated behind recent API levels. Neither platform is uniformly better, which is
 * exactly why [SupportLevel] is reported per capability rather than as one overall verdict.
 *
 * Prevention here reports [SupportLevel.RequiresOptIn] and **does nothing** until the application
 * accepts the App Review exposure through
 * [io.github.composeguard.ComposeGuard.optInToUnsanctionedCapability]. That is the design working as
 * intended, not a gap: composing a boundary on iOS without opting in protects nothing, and
 * `supportLevel` says so plainly rather than letting the application assume otherwise.
 */
internal class IosPlatformProtection : PlatformProtection {
    private val detection = CaptureDetection()
    private val screenshots = ScreenshotEvents()
    private val appSwitcher = AppSwitcher()
    private val foreground = ForegroundEvents()

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
        // The root view is what gets enclosed; without one the window is not laid out yet and the
        // request should wait rather than be recorded as a failure.
        val content = target.rootViewController?.view ?: return ProtectionOutcome.Deferred

        // Every failure below is soft. The mechanism is undocumented and can disappear in any iOS
        // release, so a null here means "report MechanismUnavailable and let the declared posture
        // decide" — never an exception into consumer code.
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
            // Not sanctioned by Apple. Reports RequiresOptIn rather than Supported or Unsupported,
            // because the mechanism exists and works — the question is whether the application accepts
            // the Guideline 2.5.1 exposure, and that is not the library's decision to make.
            Capability.ScreenshotPrevention, Capability.RecordingPrevention -> SupportLevel.RequiresOptIn

            // Officially supported throughout, via a notification carrying no deprecation.
            Capability.CaptureDetection, Capability.ScreenshotEvents -> SupportLevel.Supported

            // Ordinary view work — sanctioned, and never subject to the opt-in.
            Capability.AppSwitcherProtection -> SupportLevel.Supported
        }
}

internal actual fun createPlatformProtection(): PlatformProtection = IosPlatformProtection()
