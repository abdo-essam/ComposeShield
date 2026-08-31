package io.github.composeshield.internal

import android.os.Build
import android.view.WindowManager
import io.github.composeshield.Capability
import io.github.composeshield.SupportLevel
import io.github.composeshield.SupportLevel.Unsupported.Reason
import kotlinx.coroutines.flow.Flow

internal class AndroidPlatformProtection : PlatformProtection {
    private val detection = CaptureDetection()
    private val screenshots = ScreenshotEvents()
    private val appSwitcher = AppSwitcher()
    private val foreground = ForegroundEvents()

    override val preventionPrecludesScreenshotEvents: Boolean = true

    override fun applyProtection(
        window: WindowKey,
        capabilities: Set<Capability>,
    ): ProtectionOutcome {
        val target = windowFor(window) ?: return ProtectionOutcome.Deferred
        return onMainThread(ifDeferred = ProtectionOutcome.Deferred) {
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

    override fun applyTaskSwitcherProtection(
        window: WindowKey,
        enabled: Boolean,
    ) {
        appSwitcher.apply(window, enabled)
    }

    override fun platformSupport(capability: Capability): SupportLevel =
        when (capability) {
            Capability.ScreenshotPrevention, Capability.RecordingPrevention -> SupportLevel.Supported
            Capability.CaptureDetection -> detection.support()
            Capability.ScreenshotEvents -> screenshots.support()
            Capability.TaskSwitcherProtection -> appSwitcher.support()
        }
}

internal actual fun createPlatformProtection(): PlatformProtection = AndroidPlatformProtection()

internal val sdkInt: Int = Build.VERSION.SDK_INT

internal fun supportedFromApi(floor: Int): SupportLevel =
    if (sdkInt >= floor) SupportLevel.Supported else SupportLevel.Unsupported(Reason.OsVersionTooLow)
