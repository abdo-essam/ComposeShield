package io.github.composeshield

/**
 * A named unit of protectable or observable behaviour.
 *
 * Every capability resolves a [SupportLevel] per platform — see [ComposeShield.supportLevel].
 *
 * Capabilities are of two kinds:
 *
 * - **Prevention** ([ScreenshotPrevention], [RecordingPrevention], [TaskSwitcherProtection]) stops a
 *   capture from succeeding.
 * - **Detection** ([CaptureDetection], [ScreenshotEvents]) reports that a capture happened.
 *
 * A closed set: adding a member is an additive ABI change, removing one is breaking.
 *
 * **Thread-safety**: an immutable enum, safe to read from any thread.
 */
public enum class Capability {
    /**
     * Prevents the window's content from appearing in screenshots.
     *
     * **Android**: `Supported` on all versions, via `FLAG_SECURE`.
     *
     * **iOS**: `Supported` via internal secure container reparenting. Not independently
     * controllable from [RecordingPrevention]; one mechanism covers both.
     */
    ScreenshotPrevention,

    /**
     * Prevents the window's content from appearing in screen recordings, mirroring, and casting.
     *
     * **Android**: `Supported` on all versions, via `FLAG_SECURE`. Covers MediaProjection-based
     * recorders and casting to non-secure external displays.
     *
     * **iOS**: `Supported`, sharing the single mechanism with [ScreenshotPrevention].
     */
    RecordingPrevention,

    /**
     * Observes whether the screen is being recorded, mirrored, or streamed — see
     * [ComposeShield.captureState].
     *
     * **Android**: requires API 35 for recording detection; external-display detection works
     * throughout. Below API 35 reports [SupportLevel.Unsupported.Reason.OsVersionTooLow], with no
     * heuristic fallback.
     *
     * **iOS**: `Supported` throughout.
     *
     * Neither platform's detection is a security guarantee — see [CaptureState.Inactive].
     */
    CaptureDetection,

    /**
     * Emits an event after a screenshot is taken — see [ComposeShield.screenshotEvents].
     *
     * Strictly after the fact; it cannot prevent the capture that triggered it.
     *
     * **Android**: requires API 34, and additionally reports
     * [SupportLevel.Unsupported.Reason.PrecludedByActiveCapability] while screenshot prevention is
     * active — a platform exclusion, not a library choice.
     *
     * **iOS**: `Supported` throughout.
     */
    ScreenshotEvents,

    /**
     * Obscures application content in the OS task switcher when the app is backgrounded.
     *
     * Active by default whenever any protection request is outstanding, and separately available
     * with no protection boundary at all — see [ComposeShield.taskSwitcherProtection].
     *
     * **Android**: implied by `FLAG_SECURE` while prevention is active; standalone use requires
     * API 33.
     *
     * **iOS**: `Supported` throughout, via an overlay installed when the scene resigns active.
     */
    TaskSwitcherProtection,
    ;

    /** Whether this capability prevents capture, as opposed to reporting it. */
    internal val isPrevention: Boolean
        get() =
            when (this) {
                ScreenshotPrevention, RecordingPrevention, TaskSwitcherProtection -> true
                CaptureDetection, ScreenshotEvents -> false
            }
}
