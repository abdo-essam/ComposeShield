package io.github.composeshield.internal

import android.os.Build
import io.github.composeshield.Capability
import io.github.composeshield.SupportLevel
import io.github.composeshield.SupportLevel.Unsupported.Reason

internal actual fun expectedSupport(capability: Capability): SupportLevel =
    when (capability) {
        Capability.ScreenshotPrevention, Capability.RecordingPrevention -> SupportLevel.Supported
        Capability.CaptureDetection -> atLeast(Build.VERSION_CODES.VANILLA_ICE_CREAM)
        Capability.ScreenshotEvents -> atLeast(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
        Capability.TaskSwitcherProtection -> atLeast(Build.VERSION_CODES.TIRAMISU)
    }

private fun atLeast(floor: Int): SupportLevel =
    if (Build.VERSION.SDK_INT >= floor) SupportLevel.Supported else SupportLevel.Unsupported(Reason.OsVersionTooLow)
