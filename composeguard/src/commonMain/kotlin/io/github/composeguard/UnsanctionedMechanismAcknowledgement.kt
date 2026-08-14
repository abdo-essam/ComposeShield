package io.github.composeguard

/**
 * A written record that an application accepts the risk of an unsanctioned platform mechanism.
 *
 * Required by [ComposeGuard.optInToUnsanctionedCapability]. Its only job is to make that call
 * impossible to write by accident, and to put the developer in front of the exposure first.
 *
 * **This type is deliberately inconvenient.** A `Boolean` parameter would let the opt-in be enabled
 * by autocomplete; naming the capability and stating the accepted risk explicitly cannot be done
 * without meaning it.
 *
 * The risk applies to one capability in this release: **iOS screenshot and recording prevention**,
 * which rests on undocumented UIKit behaviour, may draw App Review Guideline 2.5.1 scrutiny, and can
 * stop working in any iOS release. Read `docs/platform-notes.md` before constructing this.
 *
 * @property capability the capability whose risk is being accepted. Must match the capability passed
 *   to [ComposeGuard.optInToUnsanctionedCapability]; a mismatch is ignored rather than throwing, so
 *   an acknowledgement cannot be reused to enable something it never named.
 * @property acceptedPolicyRisk must be `true`. `false` is a no-op, so an acknowledgement cannot be
 *   left half-constructed and still take effect.
 */
public class UnsanctionedMechanismAcknowledgement(
    public val capability: Capability,
    public val acceptedPolicyRisk: Boolean,
)
