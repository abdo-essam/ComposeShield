package io.github.composeguard.internal

import io.github.composeguard.Capability
import io.github.composeguard.SupportLevel

/**
 * Answers "can this capability be relied on right now?" — at the moment it is asked.
 *
 * The platform actual reports only what is intrinsic: this OS version, this platform, this
 * mechanism. Three things that change *during a session* are layered on here:
 *
 * 1. **Mechanism failure.** A mechanism can install successfully and stop working later.
 * 2. **Preclusion by an active capability.** On Android, active prevention silently disables
 *    screenshot events. See `docs/platform-notes.md`.
 * 3. **Opt-in state.** An unsanctioned mechanism does nothing until the risk is accepted.
 *
 * **This is why support is never resolved once at startup.** A matrix computed at launch would keep
 * reporting [SupportLevel.Supported] for a capability that had since been precluded or had silently
 * broken.
 *
 * The order above is the order they are checked, and it is load-bearing: a broken mechanism is
 * unusable whatever else is true of it, and reporting it as merely "precluded" would suggest it
 * comes back when prevention releases.
 */
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

        // Preclusion only applies to something the platform would otherwise deliver. Reporting a
        // capability as precluded when the OS is too old for it anyway would imply that releasing
        // prevention brings it back, which is false and would send a consumer down a dead end.
        if (platformLevel == SupportLevel.Supported && isPrecludedByActivePrevention(capability, state)) {
            return SupportLevel.Unsupported(SupportLevel.Unsupported.Reason.PrecludedByActiveCapability)
        }

        // An unsanctioned mechanism does nothing until its risk is explicitly accepted. Once it is,
        // the capability is as supported as the platform allows.
        if (platformLevel == SupportLevel.RequiresOptIn && capability in state.optIns) {
            return SupportLevel.Supported
        }

        return platformLevel
    }

    /**
     * Whether active prevention currently excludes [capability] at the platform level.
     *
     * Scoped tightly on purpose: screenshot events are the only capability any supported platform
     * excludes this way, and a capability that reports unsupported while working is its own kind of
     * lie.
     *
     * Whether the exclusion applies at all is the platform's answer — see
     * [PlatformProtection.preventionPrecludesScreenshotEvents]. Prevention wins the conflict because
     * blocking a capture is a stronger guarantee than logging one.
     */
    private fun isPrecludedByActivePrevention(
        capability: Capability,
        state: RegistryState,
    ): Boolean =
        capability == Capability.ScreenshotEvents &&
            platform.preventionPrecludesScreenshotEvents &&
            state.isRequestedAnywhere(Capability.ScreenshotPrevention)
}
