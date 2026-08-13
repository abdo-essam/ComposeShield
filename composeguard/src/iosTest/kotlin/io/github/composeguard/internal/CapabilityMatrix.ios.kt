package io.github.composeguard.internal

import io.github.composeguard.Capability
import io.github.composeguard.SupportLevel

/**
 * The iOS rows of `docs/capability-matrix.md`, transcribed.
 *
 * No version gates, unlike Android: every mechanism iOS offers here has been available since iOS 15,
 * which is the library's floor. `CaptureDetection` prefers the iOS 17 scene trait and falls back to
 * `UIScreen.capturedDidChangeNotification` below it, but both report `Supported` — the fallback is a
 * narrower reading, not a missing capability, so the matrix row does not change.
 *
 * The asymmetry with Android is the whole point of reporting support per capability: prevention here
 * is `RequiresOptIn` rather than `Supported` because Apple ships no prevention API and the omission
 * is deliberate. The mechanism exists and **does nothing** until an application accepts the App
 * Review Guideline 2.5.1 exposure explicitly.
 */
internal actual fun expectedSupport(capability: Capability): SupportLevel =
    when (capability) {
        Capability.ScreenshotPrevention, Capability.RecordingPrevention -> SupportLevel.RequiresOptIn
        Capability.CaptureDetection, Capability.ScreenshotEvents -> SupportLevel.Supported
        Capability.AppSwitcherProtection -> SupportLevel.Supported
    }
