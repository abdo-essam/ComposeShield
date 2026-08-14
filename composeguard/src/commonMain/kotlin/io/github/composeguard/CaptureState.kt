package io.github.composeguard

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
     * Capture is in progress.
     *
     * A transition **to** this value is never delayed or suppressed — a false negative in a security
     * library is far worse than a false positive.
     */
    Active,

    /**
     * **No evidence of capture — never a guarantee that capture is absent.**
     *
     * Read that literally. Android sees only MediaProjection-based recording, so `scrcpy`, ADB
     * `screenrecord`, HDMI capture, and OEM recorders are invisible; iOS reports scene-level capture
     * *participation*, not device-level recording. See `docs/capability-matrix.md`.
     *
     * Do not treat this as a security guarantee, and do not gate the display of sensitive content on
     * it alone. Use prevention for that.
     */
    Inactive,

    /**
     * The platform cannot currently determine whether capture is happening.
     *
     * Load-bearing, not defensive padding: both platforms under-report at cold launch, so reporting
     * [Inactive] there would tell a banking app it is safe while it is being recorded. **Never
     * coerced to [Inactive]** anywhere in the library. Treat it as "possibly being captured" rather
     * than as a safe default. See `docs/platform-notes.md`.
     */
    Unknown,
}
