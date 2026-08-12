package io.github.composeguard.internal

import android.os.Build
import io.github.composeguard.Capability
import io.github.composeguard.SupportLevel
import io.github.composeguard.SupportLevel.Unsupported.Reason

/**
 * The Android rows of `docs/capability-matrix.md`, transcribed.
 *
 * The version gates are the interesting part and they are not uniform — 33 for standalone recents,
 * 34 for screenshot events, 35 for recording detection — because each rests on a different platform
 * API that arrived in a different release. Below its floor each reports `OsVersionTooLow` with no
 * fallback, which research.md R6 argues for at length: every candidate fallback would produce a
 * false "you are not being captured", and for a security library that is worse than an honest
 * "unsupported".
 *
 * Note this deliberately reports the *platform* verdict, before the registry layers preclusion on
 * top. `ScreenshotEvents` drops to `PrecludedByActiveCapability` while prevention is active, which is
 * covered by [ScreenshotEventsTest] and by C6 — asserting it here would require the matrix to encode
 * live registry state, which the published document does not and should not.
 */
internal actual fun expectedSupport(capability: Capability): SupportLevel =
    when (capability) {
        // FLAG_SECURE predates every version this library supports.
        Capability.ScreenshotPrevention, Capability.RecordingPrevention -> SupportLevel.Supported

        Capability.CaptureDetection -> atLeast(Build.VERSION_CODES.VANILLA_ICE_CREAM)

        Capability.ScreenshotEvents -> atLeast(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)

        Capability.AppSwitcherProtection -> atLeast(Build.VERSION_CODES.TIRAMISU)
    }

private fun atLeast(floor: Int): SupportLevel =
    if (Build.VERSION.SDK_INT >= floor) SupportLevel.Supported else SupportLevel.Unsupported(Reason.OsVersionTooLow)
