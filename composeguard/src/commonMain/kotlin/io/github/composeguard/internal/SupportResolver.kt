package io.github.composeguard.internal

import io.github.composeguard.Capability
import io.github.composeguard.SupportLevel

/**
 * Answers "can this capability be relied on right now?" — at the moment it is asked.
 *
 * The platform actual reports only what is intrinsic: this OS version, this platform, this
 * mechanism. Three things that change *during a session* are layered on here, in common code, where
 * they can be reasoned about and tested together:
 *
 * 1. **Mechanism failure.** A mechanism can install successfully and stop working later.
 * 2. **Preclusion by an active capability.** Android does not invoke the screenshot-capture callback
 *    on a window with `FLAG_SECURE` set, so activating prevention silently disables screenshot
 *    events. Platform behaviour, not a library choice (research.md R5).
 * 3. **Opt-in state.** An unsanctioned mechanism reports [SupportLevel.RequiresOptIn] and does
 *    nothing until the application accepts the risk explicitly.
 *
 * **This is why support is never resolved once at startup** (FR-020b). A matrix computed at launch
 * would keep reporting [SupportLevel.Supported] for a capability that had since been precluded or
 * had silently broken — precisely the "reports protected while capturable" failure the library
 * exists to prevent.
 *
 * The order above is the order they are checked, and it is load-bearing. Failure comes first,
 * because a broken mechanism is unusable whatever else is true of it, and reporting it as merely
 * "precluded" would suggest it comes back when prevention releases.
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
     * excludes this way. A broader rule would suppress capabilities the platform is perfectly
     * willing to deliver, and a capability that reports unsupported while working is its own kind
     * of lie.
     *
     * Whether the exclusion applies at all is the platform's answer, not this class's — see
     * [PlatformProtection.preventionPrecludesScreenshotEvents]. Prevention wins the conflict rather
     * than detection: blocking a capture is a stronger guarantee than logging one, and a security
     * library that silently traded blocking for logging would be a dangerous default (FR-020c).
     */
    private fun isPrecludedByActivePrevention(
        capability: Capability,
        state: RegistryState,
    ): Boolean =
        capability == Capability.ScreenshotEvents &&
            platform.preventionPrecludesScreenshotEvents &&
            state.isRequestedAnywhere(Capability.ScreenshotPrevention)
}
