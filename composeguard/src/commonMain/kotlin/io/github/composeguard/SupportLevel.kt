package io.github.composeguard

/**
 * Whether a [Capability] can be relied upon **right now**.
 *
 * Query through [ComposeGuard.supportLevel] before depending on a capability, so the application can
 * choose its own fallback rather than guessing.
 *
 * **Evaluated at call time, never cached at startup.** Two of the four [Unsupported.Reason]s change
 * during a session: prevention activating on Android precludes screenshot events, and an opted-in
 * mechanism can fail after installing successfully. A matrix resolved once at launch would report
 * [Supported] for a capability that is silently delivering nothing.
 *
 * A sealed interface rather than an enum so [Unsupported] can carry its reason. Adding an
 * [Unsupported.Reason] member is an additive ABI change; adding a `SupportLevel` subtype is
 * breaking.
 *
 * **Thread-safety**: immutable, safe to read from any thread.
 */
public sealed interface SupportLevel {
    /** Available and officially sanctioned by the platform vendor. */
    public data object Supported : SupportLevel

    /**
     * Available, but resting on platform behaviour the vendor has not sanctioned.
     *
     * Reported until the application opts in via [ComposeGuard.optInToUnsanctionedCapability], after
     * which it becomes [Supported] — or [Unsupported] with
     * [Unsupported.Reason.MechanismUnavailable] if the mechanism fails to install.
     *
     * **The capability does nothing while in this state.** That is deliberate: the library will not
     * transfer an unevaluated app-store policy risk to a consumer silently. See
     * `docs/platform-notes.md`.
     */
    public data object RequiresOptIn : SupportLevel

    /**
     * Not available, with a [reason] distinguishing permanent from recoverable.
     *
     * The distinction drives different responses — there is no point retrying a capability the
     * platform does not implement, whereas one precluded by active prevention comes back on its own.
     */
    public data class Unsupported(
        public val reason: Reason,
    ) : SupportLevel {
        /** Why a [Capability] is unavailable. */
        public enum class Reason {
            /** The platform provides no mechanism at all. Permanent — design around it. */
            PlatformUnsupported,

            /**
             * The platform provides the mechanism, but only above this device's OS version.
             *
             * Permanent for this device; the same code on a newer one may report [Supported].
             */
            OsVersionTooLow,

            /**
             * Another currently-active capability excludes this one at the platform level.
             *
             * Transient and self-correcting. Arises on Android only, where screenshot prevention
             * suppresses screenshot events. See `docs/platform-notes.md`.
             */
            PrecludedByActiveCapability,

            /**
             * The mechanism was opted into but failed to install, or stopped working mid-session.
             *
             * The declared [FailurePosture] governs what happens to the protected content;
             * [SecureContent]'s `onProtectionFailure` reports the moment it happens.
             */
            MechanismUnavailable,
        }
    }
}
