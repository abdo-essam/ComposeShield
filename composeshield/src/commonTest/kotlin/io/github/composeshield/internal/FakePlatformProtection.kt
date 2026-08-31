package io.github.composeshield.internal

import io.github.composeshield.Capability
import io.github.composeshield.SupportLevel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.onSubscription

internal class FakePlatformProtection(
    private val support: Map<Capability, SupportLevel> =
        Capability.entries.associateWith { SupportLevel.Supported },
    override val preventionPrecludesScreenshotEvents: Boolean = true,
) : PlatformProtection {
    private val lock = Any()

    val protectedWindows: MutableSet<WindowKey> = mutableSetOf()

    val appSwitcherProtectedWindows: MutableSet<WindowKey> = mutableSetOf()

    val applyLog: MutableList<String> = mutableListOf()

    var lastRequestedCapabilities: Set<Capability> = emptySet()
        private set

    var nextOutcome: ProtectionOutcome = ProtectionOutcome.Applied

    val captureReadings: MutableSharedFlow<PlatformCaptureReading> = MutableSharedFlow(replay = 1)
    val screenshots: MutableSharedFlow<Unit> = MutableSharedFlow()
    val foregrounds: MutableSharedFlow<Unit> = MutableSharedFlow()

    var captureSubscriptions: Int = 0
        private set

    var screenshotEventsAvailable: Boolean = true

    override fun applyProtection(
        window: WindowKey,
        capabilities: Set<Capability>,
    ): ProtectionOutcome =
        fakeSynchronized(lock) {
            lastRequestedCapabilities = capabilities

            when (nextOutcome) {
                ProtectionOutcome.Applied -> {
                    protectedWindows += window
                    applyLog += "apply:${window.id}"
                }

                ProtectionOutcome.Deferred -> {
                    applyLog += "defer:${window.id}"
                }

                ProtectionOutcome.Failed -> {
                    applyLog += "fail:${window.id}"
                }
            }
            nextOutcome
        }

    override fun clearProtection(window: WindowKey): Unit =
        fakeSynchronized(lock) {
            protectedWindows -= window
            applyLog += "clear:${window.id}"
        }

    override fun observeCaptureState(): Flow<PlatformCaptureReading> =
        captureReadings.onSubscription { captureSubscriptions++ }

    override fun observeForegroundEvents(): Flow<Unit> = foregrounds

    override fun observeScreenshotEvents(): Flow<Unit> = if (screenshotEventsAvailable) screenshots else emptyFlow()

    override fun applyTaskSwitcherProtection(
        window: WindowKey,
        enabled: Boolean,
    ): Unit =
        fakeSynchronized(lock) {
            if (enabled) appSwitcherProtectedWindows += window else appSwitcherProtectedWindows -= window
        }

    override fun platformSupport(capability: Capability): SupportLevel =
        support[capability] ?: SupportLevel.Unsupported(SupportLevel.Unsupported.Reason.PlatformUnsupported)
}

internal expect inline fun <R> fakeSynchronized(
    lock: Any,
    block: () -> R,
): R
