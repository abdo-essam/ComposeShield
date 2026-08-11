package io.github.composeguard.internal

import io.github.composeguard.Capability
import io.github.composeguard.SupportLevel
import kotlinx.coroutines.flow.Flow

/**
 * Everything the platform source sets are allowed to do.
 *
 * Constitution Principle II puts all state, policy, and lifecycle coordination in common code and
 * leaves the actuals with only the irreducible platform call. This interface is where that line is
 * drawn: five operations, each translating a platform primitive into a common type, none of them
 * deciding anything. Duplicated logic across `androidMain` and `iosMain` is a defect, because
 * duplicated logic drifts and drift is exactly the maintenance cost this library exists to remove.
 *
 * Expressed as an interface rather than as top-level `expect` functions so the common state machine
 * can be driven by a fake in `commonTest`. The registry's reference counting, posture resolution,
 * and suppression logic are then provable with no device and no platform code at all, which is most
 * of what needs proving.
 *
 * **No method may throw** (FR-021). An unavailable capability reports itself unsupported; a failing
 * one reports [ProtectionOutcome.Failed]. Neither propagates a platform exception to the consumer.
 */
internal interface PlatformProtection {
    /**
     * Applies the platform's capture-prevention primitive to [window].
     *
     * Called only when the effective capability set for a window becomes non-empty. Implementations
     * marshal to the main thread themselves where the platform requires it (research.md R8).
     *
     * @return whether the primitive is in force — see [ProtectionOutcome] for why the "not applied"
     *   case is split in two. Only [ProtectionOutcome.Failed] may be reported to the consumer as a
     *   protection failure.
     */
    fun applyProtection(
        window: WindowKey,
        capabilities: Set<Capability>,
    ): ProtectionOutcome

    /** Removes the prevention primitive from [window]. Safe to call when nothing is applied. */
    fun clearProtection(window: WindowKey)

    /**
     * Whether the OS is currently reporting screen capture.
     *
     * Emits the platform's current reading, unfiltered. Cold-launch under-reporting, spurious
     * transitions, and the distinction between "inactive" and "unknown" are all resolved in common
     * code by [io.github.composeguard.internal.CaptureStateSource] — an actual that debounced or
     * defaulted on its own behalf would be making policy, which Principle II forbids.
     */
    fun observeCaptureState(): Flow<PlatformCaptureReading>

    /** Emits once per screenshot. Empty where the platform cannot report them — never an error. */
    fun observeScreenshotEvents(): Flow<Unit>

    /**
     * Applies or removes standalone app-switcher protection.
     *
     * Called only when the switcher needs protecting *independently* of capture prevention. Where
     * the prevention primitive already obscures the switcher snapshot, common code suppresses this
     * call rather than double-applying (FR-015d).
     */
    fun applyAppSwitcherProtection(
        window: WindowKey,
        enabled: Boolean,
    )

    /**
     * This platform's intrinsic support for [capability] — platform and OS version only.
     *
     * Deliberately ignorant of anything dynamic. Preclusion by an active capability, opt-in state,
     * and mechanism failure are layered on top by [io.github.composeguard.internal.SupportResolver],
     * so that policy lives in one place rather than being partly re-derived in each actual.
     */
    fun platformSupport(capability: Capability): SupportLevel

    /**
     * Emits each time the application returns to the foreground.
     *
     * Drives the re-poll in [io.github.composeguard.internal.CaptureStateSource.refresh] (FR-009).
     * Change notifications alone are insufficient on both platforms: capture that starts while the
     * app is backgrounded produces a transition no one was alive to observe, and the app then reports
     * "not being captured" for the rest of the session — a false negative in the one direction this
     * library must never get wrong (research.md R3, R6).
     *
     * A signal rather than a reading. What to do about a foreground event is common code's decision;
     * the actual only reports that one happened.
     */
    fun observeForegroundEvents(): Flow<Unit>

    /**
     * Whether active screenshot prevention suppresses screenshot events on this platform.
     *
     * Reported as data rather than branched on in common code, because Principle II forbids
     * modelling a platform difference as divergent control flow — a `when (platform)` in the
     * resolver would be a second place the platform's identity is encoded, and the two would drift.
     *
     * `true` on Android, where the OS does not invoke the capture callback on a window with
     * `FLAG_SECURE` set (AOSP `Activity.java:9940`). `false` on iOS, where the screenshot
     * notification fires regardless of what the window is doing.
     */
    val preventionPrecludesScreenshotEvents: Boolean
}

/** Creates the platform implementation for the current target. */
internal expect fun createPlatformProtection(): PlatformProtection

/**
 * Identifies the window a protection request applies to.
 *
 * Protection is applied at window level, so state is tracked per window rather than globally. An
 * application showing content in a dialog with its own window, or in split-screen, has independent
 * protection state for each — protecting one window must never be assumed to protect another.
 *
 * Internal rather than public: a consumer never names a window, because the declarative boundary
 * resolves it from composition and the imperative API resolves it from the active window. Exposing
 * it would put a concept in the public API that nobody outside the library needs (Principle I),
 * and Principle VI makes anything published expensive to take back.
 *
 * Modelled now rather than later even though this release is effectively single-window:
 * retrofitting window scoping onto global state would be a breaking change, and split-screen
 * content would be silently mis-scoped in the meantime.
 *
 * Holds an opaque identity string rather than the platform window itself, so common code never
 * holds a strong reference to a window it might outlive.
 */
internal data class WindowKey(
    val id: String,
) {
    override fun toString(): String = "WindowKey($id)"

    internal companion object {
        /**
         * The window used when no host has been resolved yet.
         *
         * The spec's "no host available" edge case requires a request made before a rendering host
         * exists to be recorded and applied once one appears, rather than dropped or thrown. Such
         * requests are booked against this key and re-bound by [io.github.composeguard.internal.ProtectionRegistry.bindWindow]
         * when the real window arrives.
         */
        val Unbound: WindowKey = WindowKey("unbound")
    }
}

/**
 * What happened when the platform was asked to install its prevention primitive.
 *
 * Three outcomes rather than a boolean, because "it did not get applied" covers two situations
 * that must be handled in opposite ways:
 *
 * - [Deferred] — there is no window to apply it to yet. The request is still perfectly valid and
 *   will take effect once a host appears (the spec's "no host available" edge case). Treating this
 *   as failure would fire `onProtectionFailure` on every request made before first composition and
 *   would push a fail-closed consumer into blanking a screen for no reason.
 * - [Failed] — there *is* a window and the mechanism would not install. This is the dangerous case
 *   FR-022 is about: the application believes content is protected and it is not.
 *
 * Collapsing the two into `false`, as an earlier iteration did, makes the common startup ordering
 * indistinguishable from a genuine security failure.
 */
internal enum class ProtectionOutcome {
    /** The primitive is installed and in force. */
    Applied,

    /** No host window is available yet; the request stands and will be applied when one appears. */
    Deferred,

    /** A host window exists but the mechanism did not install. Triggers the declared posture. */
    Failed,
}

/**
 * A single unfiltered reading of the platform's capture state.
 *
 * Distinct from the public [io.github.composeguard.CaptureState] because the platform's word is not
 * the library's answer: readings pass through cold-launch seeding and spurious-transition
 * suppression in [io.github.composeguard.internal.CaptureStateSource] before being published.
 * Keeping the two types separate stops an unprocessed reading from reaching a consumer by accident
 * — the compiler rejects it rather than a reviewer having to catch it.
 */
internal enum class PlatformCaptureReading {
    /** The platform affirmatively reports capture in progress. */
    Capturing,

    /**
     * The platform affirmatively reports no capture.
     *
     * Not yet publishable: at cold launch both platforms report this while recording is already
     * running (research.md R3, R6), and iOS emits it spuriously when a Live Activity expands.
     */
    NotCapturing,

    /** The platform cannot say. Must never be resolved to [NotCapturing]. */
    Indeterminate,
}
