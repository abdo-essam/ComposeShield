package io.github.composeguard

import io.github.composeguard.internal.ProtectionRequest
import io.github.composeguard.internal.WindowKey
import io.github.composeguard.internal.guardCore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * The imperative entry point, and the place capture state and screenshot events are observed.
 *
 * [SecureContent] is the recommended way to request protection — it ties the lifetime to
 * composition, which is where the mistakes otherwise happen. This object exists for the
 * architectures that boundary does not serve: protection driven from a navigation observer, a
 * background policy, or any code that is not a Composable. Requests made here and by a boundary
 * compose through the same reference counting, so the two can be mixed freely.
 *
 * **No operation on this object throws.** An unsupported capability, an absent window, and a
 * mechanism that will not install are all reported through [supportLevel] and [protectionFailures]
 * rather than as exceptions.
 *
 * **Thread-safety**: every member is safe to use from any thread. Platform effects are marshalled to
 * the main thread internally.
 */
public object ComposeGuard {
    /**
     * Requests protection until the returned handle is released.
     *
     * Prefer [SecureContent] where the protected content is a Composable; a handle held by hand can
     * outlive the screen that wanted it, and nothing will notice.
     *
     * Safe to call before any window exists — the request is recorded and applied as soon as one
     * appears, rather than being dropped or throwing.
     *
     * **Idempotent, not reference-counted.** Two calls with the same [capabilities] share one claim,
     * and releasing either releases it. This differs from [SecureContent], where each boundary is
     * counted separately — an imperative caller has no composition lifetime to make counting exact,
     * and a policy object acquiring on every navigation would otherwise leak protection permanently.
     *
     * A declarative boundary's claim is unaffected either way: releasing this handle never
     * unprotects a window a composed boundary still wants.
     *
     * @param capabilities which preventions to request. Independently requestable.
     * @return a handle whose [ProtectionHandle.release] withdraws the imperative claim for exactly
     *   this capability set.
     */
    public fun acquire(capabilities: Set<Capability> = DefaultPreventionCapabilities): ProtectionHandle =
        RegistryHandle(guardCore.registry.acquireShared(currentWindow(), capabilities))

    /** Whether any protection request is currently outstanding, from any source. */
    public fun isProtectionActive(): Boolean = guardCore.registry.current.isProtectedAnywhere()

    /**
     * Whether [capability] can be relied on **right now**.
     *
     * Evaluated at call time, never cached. The same call can return [SupportLevel.Supported] and
     * later [SupportLevel.Unsupported] within one session — most notably on Android, where
     * activating screenshot prevention precludes [Capability.ScreenshotEvents] while it is active.
     */
    public fun supportLevel(capability: Capability): SupportLevel =
        guardCore.supportResolver.resolve(capability, guardCore.registry.current)

    /**
     * Whether the screen is being recorded, mirrored, or streamed.
     *
     * A hot [StateFlow] with a single shared upstream, so every collector and every read of
     * [StateFlow.value] observe the same value by construction.
     *
     * Starts at [CaptureState.Unknown] and is **never seeded to [CaptureState.Inactive]** — read
     * that type's documentation before branching on it, because `Inactive` means "no evidence of
     * capture", not "not being captured".
     */
    public val captureState: StateFlow<CaptureState>
        get() = guardCore.captureStates.also { it.start() }.state

    /**
     * Emits once per screenshot, after the fact.
     *
     * Cannot prevent the capture that triggered it — use [Capability.ScreenshotPrevention] for that.
     * Carries no payload, since any payload would risk conveying the very content the library exists
     * to protect.
     *
     * An empty stream where unsupported, never an error. Check [supportLevel] for
     * [Capability.ScreenshotEvents] to distinguish "no screenshots taken" from "this platform cannot
     * tell you".
     */
    public val screenshotEvents: Flow<Unit> get() = guardCore.screenshotEvents

    /**
     * Emits when a prevention mechanism fails to install or stops working.
     *
     * The application-wide counterpart to `SecureContent`'s `onProtectionFailure`, for consumers
     * using the imperative path. Observable independently of the declared [FailurePosture]: the
     * posture decides what happens to the *content*, this reports the fact.
     */
    public val protectionFailures: Flow<Capability> get() = guardCore.protectionFailures

    /**
     * How the OS task-switcher snapshot should be treated. Defaults to
     * [AppSwitcherProtection.Automatic].
     *
     * Assigning takes effect immediately, including with no protection boundary composed at all —
     * which is what makes [AppSwitcherProtection.Always] usable on its own.
     */
    public var appSwitcherProtection: AppSwitcherProtection
        get() = guardCore.registry.current.appSwitcherMode
        set(value) = guardCore.registry.setAppSwitcherMode(value)

    /**
     * Accepts the risk of a capability whose mechanism the platform vendor has not sanctioned.
     *
     * Until this is called for a capability reporting [SupportLevel.RequiresOptIn], that capability
     * **does nothing**. That is deliberate: the library will not transfer an unevaluated app-store
     * policy risk to a consumer silently.
     *
     * Consent is **per capability**. There is no switch that enables all of them, and consent granted
     * here never extends to capabilities added in a later version — a library upgrade cannot broaden
     * what an application agreed to.
     *
     * [failurePosture] is required at this same call, not defaulted, because a mechanism that can
     * vanish mid-session must have an answer to "what happens to the content then?" before it is
     * switched on.
     *
     * A no-op, not an error, when [capability] needs no opt-in, when [acknowledgement] names a
     * different capability, or when it does not accept the risk.
     *
     * @see UnsanctionedMechanismAcknowledgement for what is being accepted.
     */
    public fun optInToUnsanctionedCapability(
        capability: Capability,
        failurePosture: FailurePosture,
        acknowledgement: UnsanctionedMechanismAcknowledgement,
    ) {
        if (!acknowledgement.acceptedPolicyRisk) return
        // An acknowledgement names the capability it was written for, so one cannot be constructed
        // for a well-understood capability and then passed to enable a different, riskier one.
        if (acknowledgement.capability != capability) return
        if (supportLevel(capability) != SupportLevel.RequiresOptIn) return

        guardCore.registry.grantOptIn(capability, failurePosture)
    }

    /**
     * Which unsanctioned capabilities have been opted into.
     *
     * For audit: an application subject to a security review can report what it enabled without
     * tracking the consent itself.
     */
    public fun grantedOptIns(): Set<Capability> = guardCore.registry.current.optIns.keys

    /**
     * The window imperative requests are booked against.
     *
     * [WindowKey.Unbound] until a host appears, at which point the boundary binds it and the
     * outstanding requests are re-pointed and applied. Requests made before there is anything to
     * apply them to are held, never dropped.
     */
    private fun currentWindow(): WindowKey =
        guardCore.registry.current.requests.keys
            .firstOrNull { it != WindowKey.Unbound } ?: WindowKey.Unbound
}

/**
 * The default capability set: prevent screenshots and recording.
 *
 * A top-level `val` rather than an inline `setOf(...)` default, so the declarative boundary's default
 * argument is a compile-time constant reference instead of a fresh allocation per recomposition.
 */
internal val DefaultPreventionCapabilities: Set<Capability> =
    setOf(Capability.ScreenshotPrevention, Capability.RecordingPrevention)

/** Adapts an internal [ProtectionRequest] to the public handle, keeping the request type internal. */
private class RegistryHandle(
    private val request: ProtectionRequest,
) : ProtectionHandle {
    override fun release() {
        guardCore.registry.release(request)
    }
}
