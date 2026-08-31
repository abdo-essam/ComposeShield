package io.github.composeshield.internal

import io.github.composeshield.Capability
import io.github.composeshield.SupportLevel

internal class SupportResolver(
    private val platform: PlatformProtection,
) {
    fun resolve(
        capability: Capability,
        state: RegistryState,
    ): SupportLevel {
        if (capability in state.failedMechanisms) {
            return SupportLevel.Unsupported(SupportLevel.Unsupported.Reason.MechanismUnavailable)
        }

        val platformLevel = platform.platformSupport(capability)
        if (platformLevel == SupportLevel.Supported && isPrecludedByActivePrevention(capability, state)) {
            return SupportLevel.Unsupported(SupportLevel.Unsupported.Reason.PrecludedByActiveCapability)
        }

        return platformLevel
    }

    private fun isPrecludedByActivePrevention(
        capability: Capability,
        state: RegistryState,
    ): Boolean =
        capability == Capability.ScreenshotEvents &&
            platform.preventionPrecludesScreenshotEvents &&
            state.isRequestedAnywhere(Capability.ScreenshotPrevention)
}
