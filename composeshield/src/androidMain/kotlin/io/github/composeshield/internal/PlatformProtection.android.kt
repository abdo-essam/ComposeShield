package io.github.composeshield.internal

import android.os.Build
import android.view.WindowManager
import io.github.composeshield.Capability
import io.github.composeshield.SupportLevel
import io.github.composeshield.SupportLevel.Unsupported.Reason
import kotlinx.coroutines.flow.Flow

/**
 * Android's implementation of the platform boundary.
 *
 * Prevention here is genuinely preventive and officially supported: `FLAG_SECURE` blocks system
 * screenshots, screen recording, MediaProjection (so third-party recorders and casting), the recents
 * thumbnail, non-secure external displays, and Assistant screen context. It does not block a camera
 * pointed at the screen, a rooted-device hook, or autofill — none of which any library can.
 *
 * **Android, not iOS, is the constrained platform for detection.** Prevention has been available
 * since API 1, but screenshot events need API 34, recording detection needs API 35, and active
 * prevention *silently disables* screenshot events. Everything below reports that honestly rather
 * than substituting a heuristic. See `docs/platform-notes.md`.
 */
internal class AndroidPlatformProtection : PlatformProtection {
    private val detection = CaptureDetection()
    private val screenshots = ScreenshotEvents()
    private val appSwitcher = AppSwitcher()
    private val foreground = ForegroundEvents()

    /**
     * `true`: AOSP `Activity.java` states the capture callback "is not invoked if the activity
     * window has FLAG_SECURE set". Platform behaviour the library reports rather than works around.
     */
    override val preventionPrecludesScreenshotEvents: Boolean = true

    override fun applyProtection(
        window: WindowKey,
        capabilities: Set<Capability>,
    ): ProtectionOutcome {
        // No window resolved yet is not a failure — the request stands and applies once one exists.
        val target = windowFor(window) ?: return ProtectionOutcome.Deferred

        return onMainThread(ifDeferred = ProtectionOutcome.Deferred) {
            // addFlags, never setFlags(flags, ALL): the latter clobbers every unrelated window flag
            // the host app set, which is someone else's bug to debug.
            target.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
            ProtectionOutcome.Applied
        }
    }

    override fun clearProtection(window: WindowKey) {
        val target = windowFor(window) ?: return
        onMainThread(ifDeferred = Unit) {
            target.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    override fun observeCaptureState(): Flow<PlatformCaptureReading> = detection.readings()

    override fun observeScreenshotEvents(): Flow<Unit> = screenshots.events()

    override fun observeForegroundEvents(): Flow<Unit> = foreground.events()

    override fun applyAppSwitcherProtection(
        window: WindowKey,
        enabled: Boolean,
    ) {
        appSwitcher.apply(window, enabled)
    }

    override fun platformSupport(capability: Capability): SupportLevel =
        when (capability) {
            // FLAG_SECURE predates every version this library supports.
            Capability.ScreenshotPrevention, Capability.RecordingPrevention -> SupportLevel.Supported

            Capability.CaptureDetection -> detection.support()

            Capability.ScreenshotEvents -> screenshots.support()

            Capability.AppSwitcherProtection -> appSwitcher.support()
        }
}

internal actual fun createPlatformProtection(): PlatformProtection = AndroidPlatformProtection()

/** The API level this device runs, read once. */
internal val sdkInt: Int = Build.VERSION.SDK_INT

/**
 * Reports [Reason.OsVersionTooLow] below [floor], or [SupportLevel.Supported] at or above it.
 *
 * Every Android capability except prevention is gated this way, and each deliberately has **no
 * fallback below its floor** — every candidate fallback would produce a false "you are not being
 * captured", which for a security library is worse than an honest "unsupported". See
 * `docs/platform-notes.md`.
 */
internal fun supportedFromApi(floor: Int): SupportLevel =
    if (sdkInt >= floor) SupportLevel.Supported else SupportLevel.Unsupported(Reason.OsVersionTooLow)
