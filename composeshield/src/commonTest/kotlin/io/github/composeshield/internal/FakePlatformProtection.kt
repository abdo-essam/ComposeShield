package io.github.composeshield.internal

import io.github.composeshield.Capability
import io.github.composeshield.SupportLevel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.onSubscription

/**
 * A scriptable stand-in for the platform layer.
 *
 * The registry's reference counting, posture resolution, and support layering are pure logic, so
 * they should be provable without a device — Principle II's common-first split exists partly so that
 * this is possible. This fake records what the platform was *asked* to do and lets a test dictate
 * what the platform says back, including the awkward answers real platforms give: a mechanism that
 * refuses to install, a capability the OS is too old for, an indeterminate capture reading.
 */
internal class FakePlatformProtection(
    private val support: Map<Capability, SupportLevel> =
        Capability.entries.associateWith { SupportLevel.Supported },
    override val preventionPrecludesScreenshotEvents: Boolean = true,
) : PlatformProtection {
    /** Guards [protectedWindows], [appSwitcherProtectedWindows], and [applyLog] from concurrent mutation. */
    private val lock = Any()

    /** Windows currently carrying the prevention primitive. */
    val protectedWindows: MutableSet<WindowKey> = mutableSetOf()

    /** Windows carrying standalone app-switcher protection. */
    val appSwitcherProtectedWindows: MutableSet<WindowKey> = mutableSetOf()

    /**
     * Every apply/clear in order.
     *
     * Kept as an ordered log rather than a count so tests can assert on *redundant toggling*, not
     * just the end state — on a real window each toggle is a surface teardown the user sees, so
     * "ended up correct" is not the same as "behaved correctly" (research.md R8).
     */
    val applyLog: MutableList<String> = mutableListOf()

    /** The capability set most recently requested, for asserting the union is what gets applied. */
    var lastRequestedCapabilities: Set<Capability> = emptySet()
        private set

    /** What [applyProtection] should report. Set to a failure to exercise the posture paths. */
    var nextOutcome: ProtectionOutcome = ProtectionOutcome.Applied

    val captureReadings: MutableSharedFlow<PlatformCaptureReading> = MutableSharedFlow(replay = 1)
    val screenshots: MutableSharedFlow<Unit> = MutableSharedFlow()
    val foregrounds: MutableSharedFlow<Unit> = MutableSharedFlow()

    /** How many times the capture-state flow has been collected, to assert a re-poll re-subscribed. */
    var captureSubscriptions: Int = 0
        private set

    /** When false, [observeScreenshotEvents] is empty, as an unsupporting platform must be. */
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

    override fun applyAppSwitcherProtection(
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
