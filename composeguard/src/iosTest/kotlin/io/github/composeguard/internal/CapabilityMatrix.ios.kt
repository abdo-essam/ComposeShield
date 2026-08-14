package io.github.composeguard.internal

import io.github.composeguard.Capability
import io.github.composeguard.SupportLevel

/**
 * The iOS rows of `docs/capability-matrix.md`, transcribed.
 *
 * All five capabilities are supported on iOS.
 */
internal actual fun expectedSupport(capability: Capability): SupportLevel =
    when (capability) {
        Capability.ScreenshotPrevention, Capability.RecordingPrevention -> SupportLevel.Supported
        Capability.CaptureDetection, Capability.ScreenshotEvents -> SupportLevel.Supported
        Capability.AppSwitcherProtection -> SupportLevel.Supported
    }
