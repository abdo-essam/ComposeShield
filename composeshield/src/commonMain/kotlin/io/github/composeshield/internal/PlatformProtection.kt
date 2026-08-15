package io.github.composeshield.internal

import io.github.composeshield.Capability
import io.github.composeshield.SupportLevel
import kotlinx.coroutines.flow.Flow

/**
 * Everything the platform source sets are allowed to do.
 *
 * All state, policy, and lifecycle coordination lives in common code; the actuals get only the
 * irreducible platform call. Five operations, each translating a platform primitive into a common
 * type, none of them deciding anything. Duplicated logic across `androidMain` and `iosMain` is a
 * defect — duplicated logic drifts.
 *
 * An interface rather than top-level `expect` functions so the common state machine can be driven by
 * a fake in `commonTest`, making reference counting, posture resolution, and suppression provable
 * with no device.
 *
 * **No method may throw.** An unavailable capability reports itself unsupported; a failing one
 * reports [ProtectionOutcome.Failed].
 */
internal interface PlatformProtection {
    /**
     * Applies the platform's capture-prevention primitive to [window].
     *
     * Called only when the effective capability set for a window becomes non-empty. Implementations
     * marshal to the main thread themselves where the platform requires it.
     *
     * @return whether the primitive is in force. Only [ProtectionOutcome.Failed] may be reported to
     *   the consumer as a protection failure.
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
     * Emits the platform's reading, unfiltered. Cold-launch under-reporting, spurious transitions,
     * and the "inactive" versus "unknown" distinction are all resolved by [CaptureStateSource] — an
     * actual that debounced on its own behalf would be making policy.
     */
    fun observeCaptureState(): Flow<PlatformCaptureReading>

    /** Emits once per screenshot. Empty where the platform cannot report them — never an error. */
    fun observeScreenshotEvents(): Flow<Unit>

    /**
     * Applies or removes standalone app-switcher protection.
     *
     * Called only when the switcher needs protecting *independently* of capture prevention. Where
     * the prevention primitive already obscures the snapshot, common code suppresses this call
     * rather than double-applying.
     */
    fun applyAppSwitcherProtection(
        window: WindowKey,
        enabled: Boolean,
    )

    /**
     * This platform's intrinsic support for [capability] — platform and OS version only.
     *
     * Deliberately ignorant of anything dynamic. Preclusion, opt-in state, and mechanism failure are
     * layered on by [SupportResolver], so that policy lives in one place.
     */
    fun platformSupport(capability: Capability): SupportLevel

    /**
     * Emits each time the application returns to the foreground.
     *
     * Drives the re-poll in [CaptureStateSource.refresh]. Change notifications alone are insufficient
     * on both platforms: capture starting while backgrounded produces a transition nothing was alive
     * to observe. A signal, not a reading — what to do about it is common code's decision.
     */
    fun observeForegroundEvents(): Flow<Unit>

    /**
     * Whether active screenshot prevention suppresses screenshot events on this platform.
     *
     * Reported as data rather than branched on in common code: a `when (platform)` in the resolver
     * would be a second place the platform's identity is encoded, and the two would drift.
     *
     * `true` on Android, `false` on iOS. See `docs/platform-notes.md`.
     */
    val preventionPrecludesScreenshotEvents: Boolean
}

/** Creates the platform implementation for the current target. */
internal expect fun createPlatformProtection(): PlatformProtection

/**
 * Identifies the window a protection request applies to.
 *
 * Protection is applied at window level, so state is tracked per window rather than globally: a
 * dialog with its own window, or split-screen content, has independent protection state.
 *
 * Internal rather than public — a consumer never names a window, since the declarative boundary
 * resolves it from composition and the imperative API from the active window.
 *
 * Holds an opaque identity string rather than the platform window itself, so common code never holds
 * a strong reference to a window it might outlive.
 */
internal data class WindowKey(
    val id: String,
) {
    override fun toString(): String = "WindowKey($id)"

    internal companion object {
        /**
         * The window used when no host has been resolved yet.
         *
         * A request made before a rendering host exists is booked against this key and re-bound by
         * [ProtectionRegistry.bindWindow] when the real window arrives, rather than being dropped.
         */
        val Unbound: WindowKey = WindowKey("unbound")
    }
}

/**
 * What happened when the platform was asked to install its prevention primitive.
 *
 * Three outcomes rather than a boolean, because "not applied" covers two situations needing opposite
 * handling: [Deferred] is ordinary startup ordering, [Failed] is the dangerous case where the
 * application believes content is protected and it is not. Collapsing them into `false` makes the
 * two indistinguishable.
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
 * Distinct from the public [io.github.composeshield.CaptureState] because the platform's word is not
 * the library's answer: readings pass through cold-launch seeding and spurious-transition
 * suppression in [CaptureStateSource] first. Separate types mean the compiler rejects an unprocessed
 * reading reaching a consumer, rather than a reviewer having to catch it.
 */
internal enum class PlatformCaptureReading {
    /** The platform affirmatively reports capture in progress. */
    Capturing,

    /**
     * The platform affirmatively reports no capture.
     *
     * Not yet publishable: at cold launch both platforms report this while recording is already
     * running, and iOS emits it spuriously when a Live Activity expands.
     */
    NotCapturing,

    /** The platform cannot say. Must never be resolved to [NotCapturing]. */
    Indeterminate,
}
