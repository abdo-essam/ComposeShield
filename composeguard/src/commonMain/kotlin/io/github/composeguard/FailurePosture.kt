package io.github.composeguard

/**
 * The application's declared answer to "what should happen if protection breaks?"
 *
 * Required at [ComposeGuard.optInToUnsanctionedCapability]. **The library assumes no default** and
 * the opt-in cannot be expressed without one — a default would mean the library choosing, on the
 * application's behalf, between showing sensitive content to a possible attacker and blanking a
 * screen the user is legitimately trying to read.
 *
 * Applies at the *moment of loss*, not only at installation: a mechanism that installs successfully
 * and stops working later triggers the posture just the same.
 *
 * **Conflict resolution**: where several outstanding requests on one window declare different
 * postures, [FailClosed] wins.
 *
 * **Thread-safety**: an immutable enum, safe to read from any thread.
 */
public enum class FailurePosture {
    /**
     * On failure, let the content render unprotected.
     *
     * The capability reports [SupportLevel.Unsupported] with
     * [SupportLevel.Unsupported.Reason.MechanismUnavailable], and the failure is delivered to
     * `onProtectionFailure` for the application to handle.
     *
     * Appropriate when an interrupted user experience costs more than the exposure does.
     */
    FailOpen,

    /**
     * On failure, obscure the protected content until protection is restored or released.
     *
     * The content is hidden from the on-device viewer too, since the library cannot distinguish a
     * legitimate viewer from a camera pointed at the screen. It stays obscured — it does not flash
     * back after a moment.
     *
     * Appropriate when exposure costs more than an interrupted experience.
     */
    FailClosed,
    ;

    internal companion object {
        /** The more protective of two postures, used to resolve per-window conflicts. */
        fun mostProtective(
            first: FailurePosture,
            second: FailurePosture,
        ): FailurePosture = if (first == FailClosed || second == FailClosed) FailClosed else FailOpen
    }
}
