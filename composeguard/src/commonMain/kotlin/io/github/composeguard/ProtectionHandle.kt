package io.github.composeguard

/**
 * A claim on protection, held for as long as the caller needs it.
 *
 * Returned by [ComposeGuard.acquire]. Protection stays active on the window while **any** handle
 * remains unreleased, so releasing one never unprotects content another still claims.
 *
 * That is why the imperative API hands back a handle rather than offering `enable()`/`disable()`.
 * A global disable invites the leak this design forbids: a screen tearing down would unprotect a
 * still-visible one, and the resulting exposure is invisible until someone screenshots it.
 *
 * **Thread-safety**: [release] is safe to call from any thread, and the platform effect is
 * marshalled to the main thread internally.
 */
public interface ProtectionHandle {
    /**
     * Releases this claim.
     *
     * **Idempotent** — calling it repeatedly is harmless and never decrements another handle's
     * claim. Protection is only withdrawn from the window once every outstanding claim, both
     * imperative and declarative, has been released.
     *
     * Never throws, including when protection was never actually applied because the capability is
     * unsupported.
     */
    public fun release()
}

/**
 * A written record that an application accepts the risk of an unsanctioned platform mechanism.
 *
 * Required by [ComposeGuard.optInToUnsanctionedCapability]. Its only job is to make that call
 * impossible to write by accident, and to put the developer in front of the exposure before they
 * take it on.
 *
 * **This type is deliberately inconvenient.** A `Boolean` parameter would let the opt-in be enabled
 * by autocomplete; naming the capability twice and stating the accepted risk explicitly cannot be
 * done without meaning it. That friction is the feature — the library will not transfer an
 * unevaluated app-store policy risk to a consumer silently (FR-023).
 *
 * The risk being accepted, for the one capability this applies to in this release:
 *
 * - **iOS screenshot and recording prevention.** No official API exists; Apple's omission is
 *   deliberate. The mechanism calls no private API, so it does not trip static private-symbol
 *   scanners, but Apple Developer Technical Support has stated that using a secure text field as a
 *   wrapping container "is not its intended purpose", invoking **App Review Guideline 2.5.1**.
 *   Rejection is possible. The mechanism also rests on an undocumented view-class name and can stop
 *   working in any iOS release — at which point the [FailurePosture] declared here decides what
 *   happens to the content.
 *
 * See `docs/capability-matrix.md` for the per-platform detail.
 *
 * @property capability the capability whose risk is being accepted. Must match the capability
 *   passed to [ComposeGuard.optInToUnsanctionedCapability]; a mismatch is ignored rather than
 *   throwing (FR-021), so an acknowledgement cannot be reused to enable something it never named.
 * @property acceptedPolicyRisk must be `true`. `false` is a no-op, so an acknowledgement cannot be
 *   left half-constructed and still take effect.
 */
public class UnsanctionedMechanismAcknowledgement(
    public val capability: Capability,
    public val acceptedPolicyRisk: Boolean,
)
