package io.github.composeguard

/**
 * A named unit of protectable or observable behaviour.
 *
 * Every capability resolves a [SupportLevel] on every supported platform — see
 * [ComposeGuard.supportLevel]. Support is genuinely asymmetric between Android and iOS, and the
 * API deliberately does not paper over that: an application attesting to a security standard on
 * the strength of this library would otherwise be misled.
 *
 * Capabilities divide into two kinds, and the distinction is load-bearing:
 *
 * - **Prevention** ([ScreenshotPrevention], [RecordingPrevention], [AppSwitcherProtection]) stops
 *   a capture from succeeding. Subject to a [FailurePosture], because a prevention mechanism that
 *   silently stops working is the most dangerous failure this library has.
 * - **Detection** ([CaptureDetection], [ScreenshotEvents]) reports that a capture happened or is
 *   happening. Never subject to a failure posture — there is nothing to obscure.
 *
 * This is a closed set. Adding a member is an additive ABI change; removing one is breaking
 * (constitution Principle VI).
 *
 * **Thread-safety**: an immutable enum, safe to read from any thread.
 */
public enum class Capability {
    /**
     * Prevents the window's content from appearing in screenshots.
     *
     * **Android**: `Supported` on all supported versions, via `FLAG_SECURE`.
     *
     * **iOS**: `RequiresOptIn`. No official prevention API exists and Apple's omission is
     * deliberate. See [ComposeGuard.optInToUnsanctionedCapability]. On iOS this capability and
     * [RecordingPrevention] are not independently controllable — one mechanism covers both, so
     * requesting either grants both.
     */
    ScreenshotPrevention,

    /**
     * Prevents the window's content from appearing in screen recordings, mirroring, and casting.
     *
     * **Android**: `Supported` on all supported versions, via `FLAG_SECURE`. Covers
     * MediaProjection-based recorders and casting to non-secure external displays.
     *
     * **iOS**: `RequiresOptIn`, sharing the single mechanism with [ScreenshotPrevention].
     */
    RecordingPrevention,

    /**
     * Observes whether the screen is being recorded, mirrored, or streamed — see
     * [ComposeGuard.captureState].
     *
     * **Android**: requires API 35 for recording detection; external-display detection works
     * throughout. Below API 35, recording detection reports
     * [SupportLevel.Unsupported.Reason.OsVersionTooLow] — there is no heuristic fallback, because
     * a false "not recording" signal is worse than an honest "unsupported".
     *
     * **iOS**: `Supported` throughout; trait-based on iOS 17+, notification-based below.
     *
     * Neither platform's detection is a security guarantee. See [CaptureState.Inactive].
     */
    CaptureDetection,

    /**
     * Emits an event after a screenshot is taken — see [ComposeGuard.screenshotEvents].
     *
     * Strictly after the fact. It cannot prevent the capture that triggered it.
     *
     * **Android**: requires API 34. Additionally reports
     * [SupportLevel.Unsupported.Reason.PrecludedByActiveCapability] whenever screenshot prevention
     * is active — the platform does not invoke the capture callback on a window with `FLAG_SECURE`
     * set. That exclusion is a platform behaviour, not a library design choice.
     *
     * **iOS**: `Supported` throughout.
     */
    ScreenshotEvents,

    /**
     * Obscures application content in the OS task switcher when the app is backgrounded.
     *
     * Active by default whenever any protection request is outstanding, and separately available
     * with no protection boundary at all — see [ComposeGuard.appSwitcherProtection].
     *
     * **Android**: implied by `FLAG_SECURE` whenever prevention is active; standalone use requires
     * API 33.
     *
     * **iOS**: `Supported` throughout, via an overlay installed when the scene resigns active.
     *
     * Uses only officially sanctioned mechanisms on both platforms and never requires the opt-in
     * of [ComposeGuard.optInToUnsanctionedCapability].
     */
    AppSwitcherProtection,
    ;

    /**
     * Whether this capability prevents capture, as opposed to reporting it.
     *
     * Only prevention capabilities carry a [FailurePosture] — the posture answers "what should
     * happen to the content if this stops working", which is meaningless for detection.
     */
    internal val isPrevention: Boolean
        get() =
            when (this) {
                ScreenshotPrevention, RecordingPrevention, AppSwitcherProtection -> true
                CaptureDetection, ScreenshotEvents -> false
            }
}

/**
 * Whether a [Capability] can be relied upon **right now**.
 *
 * Query this through [ComposeGuard.supportLevel] before depending on a capability, so the
 * application can choose its own fallback rather than guessing.
 *
 * **Support is evaluated at call time, never cached at startup.** Two of the four
 * [Unsupported.Reason]s change during a session: prevention activating on Android precludes
 * screenshot events, and an opted-in mechanism can fail after installing successfully. A matrix
 * resolved once at launch would report [Supported] for a capability that is silently delivering
 * nothing — exactly the failure this library exists to make impossible.
 *
 * A sealed interface rather than an enum so [Unsupported] can carry its reason. Adding a
 * [Unsupported.Reason] member is an additive ABI change; adding a `SupportLevel` subtype is
 * breaking (constitution Principle VI).
 *
 * **Thread-safety**: immutable, safe to read from any thread.
 */
public sealed interface SupportLevel {
    /**
     * Available and officially sanctioned by the platform vendor.
     *
     * The capability will do what it says on this device, in this OS version, given the
     * capabilities currently active.
     */
    public data object Supported : SupportLevel

    /**
     * Available, but resting on platform behaviour the vendor has not sanctioned.
     *
     * Reported until the application opts in via [ComposeGuard.optInToUnsanctionedCapability],
     * after which it becomes [Supported] — or [Unsupported] with
     * [Unsupported.Reason.MechanismUnavailable] if the mechanism fails to install.
     *
     * The capability does nothing while in this state. That is the point: the library will not
     * transfer an unevaluated app-store policy risk to a consumer silently.
     */
    public data object RequiresOptIn : SupportLevel

    /**
     * Not available, with a [reason] distinguishing permanent from recoverable.
     *
     * The distinction drives genuinely different responses — there is no point retrying a
     * capability the platform does not implement, whereas one precluded by active prevention will
     * come back on its own.
     */
    public data class Unsupported(
        public val reason: Reason,
    ) : SupportLevel {
        /** Why a [Capability] is unavailable. */
        public enum class Reason {
            /**
             * The platform provides no mechanism for this capability at all.
             *
             * Permanent. Design around it.
             */
            PlatformUnsupported,

            /**
             * The platform provides the mechanism, but only above this device's OS version.
             *
             * Permanent for this device; the same code on a newer device may report [Supported].
             */
            OsVersionTooLow,

            /**
             * Another currently-active capability excludes this one at the platform level.
             *
             * Transient and self-correcting. In this release this arises on Android only:
             * screenshot prevention suppresses screenshot events, because the platform does not
             * invoke the capture callback on a window with `FLAG_SECURE` set. Prevention wins —
             * blocking a capture beats logging one — and the superseded capability reports
             * unsupported for the duration rather than silently delivering nothing.
             */
            PrecludedByActiveCapability,

            /**
             * The mechanism was opted into but failed to install, or stopped working mid-session.
             *
             * Transient in principle, and may indicate the underlying platform behaviour has
             * changed. The declared [FailurePosture] governs what happens to the protected
             * content; [SecureContent]'s `onProtectionFailure` reports the moment it happens.
             */
            MechanismUnavailable,
        }
    }
}

/**
 * Whether the screen is being recorded, mirrored, or streamed to an external display.
 *
 * Observe through [ComposeGuard.captureState]. Detection works independently of whether any
 * prevention capability is enabled or even supported.
 *
 * **Thread-safety**: an immutable enum, safe to read from any thread.
 */
public enum class CaptureState {
    /**
     * Capture is in progress: the screen is being recorded, mirrored, or streamed.
     *
     * A transition **to** this value is never delayed or suppressed. A false negative in a
     * security library is far worse than a false positive, so [Active] is published the moment the
     * platform reports it.
     */
    Active,

    /**
     * **No evidence of capture — never a guarantee that capture is absent.**
     *
     * Read that literally. Both platforms have documented blind spots:
     *
     * - **Android** sees only MediaProjection-based recording. `scrcpy`, ADB `screenrecord`, HDMI
     *   capture, and OEM recorders that bypass MediaProjection are all invisible.
     * - **iOS** reports scene-level capture *participation*, not device-level recording.
     *
     * An application must not treat this value as a security guarantee, and must not gate the
     * display of sensitive content on it alone. Use prevention for that.
     */
    Inactive,

    /**
     * The platform cannot currently determine whether capture is happening.
     *
     * This value is load-bearing, not defensive padding. Both platforms under-report at cold
     * launch, for unrelated reasons: iOS has a platform bug where the first read returns inactive
     * while recording is already running, and Android reports state only through a callback whose
     * initial value is easy to discard. If recording began before the app launched, no transition
     * ever occurs.
     *
     * Reporting [Inactive] in that window would tell a banking app it is safe while it is being
     * recorded. So the library reports [Unknown] instead, and **never coerces it to [Inactive]**.
     * Consumers should treat it as "possibly being captured" rather than as a safe default.
     */
    Unknown,
}

/**
 * The application's declared answer to "what should happen if protection breaks?"
 *
 * Required at [ComposeGuard.optInToUnsanctionedCapability]. **The library assumes no default**, and
 * the opt-in cannot be expressed without one — the type system enforces it. A default would mean
 * the library choosing, on the application's behalf, between showing sensitive content to a
 * possible attacker and blanking a screen the user is legitimately trying to read. That decision
 * belongs to the application.
 *
 * This exists because "never crash" (constitution Principle IV) and "never falsely report
 * protection" would otherwise conflict. A prevention mechanism that fails mid-session leaves the
 * library holding content the application believes is protected; the posture says what to do with
 * it.
 *
 * **Conflict resolution**: where several outstanding requests on the same window declare different
 * postures, [FailClosed] wins. The more protective posture always does.
 *
 * Applies at the *moment of loss*, not only at installation — a mechanism that installs
 * successfully and stops working later triggers the posture just the same.
 *
 * **Thread-safety**: an immutable enum, safe to read from any thread.
 */
public enum class FailurePosture {
    /**
     * On failure, let the content render unprotected.
     *
     * The capability reports [SupportLevel.Unsupported] with
     * [SupportLevel.Unsupported.Reason.MechanismUnavailable], and the failure is delivered to
     * `onProtectionFailure`. The application decides what to do — navigate away, show a warning,
     * write an audit entry.
     *
     * Appropriate when an interrupted user experience costs more than the exposure does, and when
     * the application intends to handle the failure itself rather than delegate it.
     */
    FailOpen,

    /**
     * On failure, obscure the protected content until protection is restored or released.
     *
     * The content is hidden from the on-device viewer as well, since the library cannot
     * distinguish a legitimate viewer from a camera pointed at the screen. It stays obscured — it
     * does not flash back after a moment.
     *
     * Appropriate when exposure costs more than an interrupted experience: the screen goes blank
     * rather than leaking, and the user notices something is wrong.
     */
    FailClosed,
    ;

    internal companion object {
        /**
         * The more protective of two postures, used to resolve conflicts between outstanding
         * requests on the same window.
         */
        fun mostProtective(
            first: FailurePosture,
            second: FailurePosture,
        ): FailurePosture = if (first == FailClosed || second == FailClosed) FailClosed else FailOpen
    }
}

/**
 * How the OS task-switcher snapshot should be treated.
 *
 * Set through [ComposeGuard.appSwitcherProtection]. The switcher is the most frequently
 * encountered real-world leak vector — the system photographs the app on every backgrounding,
 * whether or not anyone deliberately captured anything — and it is officially supported on both
 * platforms, so it needs no unsanctioned-mechanism opt-in.
 *
 * **Thread-safety**: an immutable enum, safe to read from any thread.
 */
public enum class AppSwitcherProtection {
    /**
     * Protect the switcher whenever any protection request is outstanding. **The default.**
     *
     * An application that protects a screen almost always wants that screen absent from the
     * switcher too, so requiring a second opt-in for it would mostly produce leaks from
     * forgetfulness.
     */
    Automatic,

    /**
     * Always protect the switcher, whether or not any protection request exists.
     *
     * Lets an application obscure its switcher snapshot without preventing capture at all — useful
     * where the concern is a shoulder-surfer in the task list rather than deliberate exfiltration.
     * On Android this maps to the recents-only primitive, not to capture prevention.
     */
    Always,

    /**
     * Never protect the switcher, even while protection is active.
     *
     * An explicit override. Capture prevention stays fully active; only the switcher snapshot is
     * left alone. Note that on Android the prevention primitive obscures recents as an inseparable
     * side effect, so this cannot reveal a snapshot the platform itself has already hidden.
     */
    Disabled,
}
