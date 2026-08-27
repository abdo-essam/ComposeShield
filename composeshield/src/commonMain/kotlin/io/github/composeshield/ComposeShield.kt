package io.github.composeshield

import io.github.composeshield.internal.ProtectionRequest
import io.github.composeshield.internal.resolveCurrentWindowKey
import io.github.composeshield.internal.shieldCore
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
public object ComposeShield {
    /**
     * Requests protection until the returned handle is unprotected or closed.
     *
     * Prefer [SecureContent] where the protected content is a Composable; a handle held manually can
     * outlive the screen that wanted it.
     *
     * Safe to call before any window exists — the request is recorded and applied as soon as one
     * appears.
     *
     * **Idempotent, not reference-counted.** Two calls with the same [capabilities] share one claim,
     * and unprotecting either releases it. This differs from [SecureContent], where each boundary is
     * counted separately.
     *
     * A declarative boundary's claim is unaffected: unprotecting this handle never unprotects a
     * window a composed boundary still wants.
     *
     * @param capabilities which preventions to request. Independently requestable.
     *   Defaults to `{ScreenshotPrevention, RecordingPrevention}`.
     * @return a handle whose [ProtectionHandle.unprotect] withdraws the imperative claim for
     *   exactly this capability set.
     */
    public fun protect(capabilities: Set<Capability> = DefaultPreventionCapabilities): ProtectionHandle =
        RegistryHandle(shieldCore.registry.acquireShared(resolveCurrentWindowKey(), capabilities))

    /**
     * Relinquishes any active imperative protection claim matching [capabilities].
     *
     * **Idempotent** — safe to call repeatedly or when no imperative claim is active.
     * Releasing an imperative claim never withdraws protection if a [SecureContent] composable
     * or another claim is still active on the window.
     *
     * @param capabilities which preventions to unprotect.
     *   Defaults to `{ScreenshotPrevention, RecordingPrevention}`.
     */
    public fun unprotect(capabilities: Set<Capability> = DefaultPreventionCapabilities) {
        shieldCore.registry.releaseShared(resolveCurrentWindowKey(), capabilities)
    }

    /** Whether any protection request is currently outstanding, from any source. */
    public fun isProtectionActive(): Boolean = shieldCore.registry.current.isProtectedAnywhere()

    /**
     * Whether [capability] can be relied on **right now**.
     *
     * Evaluated at call time, never cached. The same call can return [SupportLevel.Supported] and
     * later [SupportLevel.Unsupported] within one session — most notably on Android, where
     * activating screenshot prevention precludes [Capability.ScreenshotEvents] while it is active.
     */
    public fun supportLevel(capability: Capability): SupportLevel =
        shieldCore.supportResolver.resolve(capability, shieldCore.registry.current)

    /**
     * Whether the screen is being recorded, mirrored, or streamed.
     *
     * A hot [StateFlow] with a single shared upstream, so every collector and every read of
     * [StateFlow.value] observe the same value by construction.
     *
     * Starts at [CaptureState.Unknown] and is **never seeded to [CaptureState.Inactive]** — read
     * that type's documentation before branching on it, because `Inactive` means "no evidence of
     * capture", not "not being captured".
     *
     * Observation begins when the library first initializes, not on this property's first read — a
     * property read must not start work.
     */
    public val captureState: StateFlow<CaptureState>
        get() = shieldCore.captureStates.state

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
    public val screenshotEvents: Flow<Unit> get() = shieldCore.screenshotEvents

    /**
     * Emits when a prevention mechanism fails to install or stops working.
     *
     * The application-wide counterpart to `SecureContent`'s `onProtectionFailure`, for consumers
     * using the imperative path. The most recent failure is replayed to a collector that attaches
     * after it was emitted — a security-relevant signal must not be lost because nobody was
     * listening yet.
     */
    public val protectionFailures: Flow<Capability> get() = shieldCore.protectionFailures

    /**
     * How the OS task-switcher snapshot should be treated. Defaults to
     * [TaskSwitcherProtection.Automatic].
     *
     * Assigning takes effect immediately, including with no protection boundary composed at all —
     * which is what makes [TaskSwitcherProtection.Always] usable on its own.
     */
    public var taskSwitcherProtection: TaskSwitcherProtection
        get() = shieldCore.registry.current.taskSwitcherMode
        set(value) = shieldCore.registry.setTaskSwitcherMode(value)
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
    override fun unprotect() {
        shieldCore.registry.release(request)
    }
}
