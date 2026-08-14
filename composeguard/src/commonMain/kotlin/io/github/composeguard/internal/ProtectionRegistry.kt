package io.github.composeguard.internal

import io.github.composeguard.AppSwitcherProtection
import io.github.composeguard.Capability
import io.github.composeguard.FailurePosture
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/**
 * The single place all protection state lives.
 *
 * Reference-counts requests, resolves the effective capability set and failure posture per window,
 * and drives [PlatformProtection] when — and only when — the outcome actually changes.
 *
 * **Reference counting is a correctness requirement, not an optimisation.** Only one physical
 * protection primitive exists per window, so a departing screen that cleared it directly would
 * unprotect a still-visible screen underneath, with nothing to report the exposure.
 *
 * **Thread-safety**: mutations are a compare-and-set over an immutable [RegistryState] snapshot, so
 * no caller observes a half-applied change and no lock is held across the platform call. Platform
 * application is marshalled to the main thread by the actuals themselves.
 *
 * @param platform the platform primitive to delegate to.
 * @param onProtectionFailure invoked when a mechanism fails to install or stops working, so the
 *   failure is observable as it happens rather than only by polling support state.
 */
@OptIn(ExperimentalAtomicApi::class)
internal class ProtectionRegistry(
    private val platform: PlatformProtection,
    private val onProtectionFailure: (Capability) -> Unit = {},
) {
    private val state = AtomicReference(RegistryState())

    private val _snapshots = MutableStateFlow(RegistryState())

    /**
     * The snapshot as an observable stream.
     *
     * [SecureContent][io.github.composeguard.SecureContent] collects this to honour a fail-closed
     * posture at the *moment* a mechanism breaks. Polling [current] on recomposition would only
     * notice a mid-session failure if something else happened to recompose — for a static protected
     * screen, possibly never.
     */
    val snapshots: StateFlow<RegistryState> = _snapshots.asStateFlow()

    /** The current snapshot. Safe to read from any thread; never a partially-applied state. */
    val current: RegistryState get() = state.load()

    /**
     * Records a new request and applies protection if this changed the window's effective set.
     *
     * Pass [WindowKey.Unbound] when no host exists yet; the request is held and applied once
     * [bindWindow] reports one, rather than being dropped.
     *
     * @return the request, to be passed back to [release]. Each call produces a distinct claim even
     *   when the capabilities are identical, so two boundaries count as two.
     */
    fun acquire(
        window: WindowKey,
        capabilities: Set<Capability>,
    ): ProtectionRequest {
        val request = ProtectionRequest(capabilities, window)
        mutate { snapshot ->
            val existing = snapshot.requests[window].orEmpty()
            snapshot.copy(requests = snapshot.requests + (window to existing + request))
        }
        reconcile(window)
        return request
    }

    /**
     * Records an imperative request, collapsing onto the existing one for the same capabilities.
     *
     * **Idempotent, deliberately not reference-counted.** Acquiring twice and releasing once releases
     * protection — the opposite of the declarative path. A boundary's lifetime is delimited by
     * composition so counting it is exact; an imperative caller has no such structure, and a policy
     * object calling `acquire()` on every navigation would leak protection permanently under
     * reference counting.
     *
     * Distinct capability sets remain distinct claims, so this cannot merge two callers wanting
     * different things.
     */
    fun acquireShared(
        window: WindowKey,
        capabilities: Set<Capability>,
    ): ProtectionRequest {
        sharedRequest(window, capabilities)?.let { return it }

        val request = ProtectionRequest(capabilities, window, isImperative = true)
        mutate { snapshot ->
            val existing = snapshot.requests[window].orEmpty()
            // Re-check inside the loop: a concurrent acquire may have installed the shared request
            // between the read above and this compare-and-set.
            if (existing.any { it.isImperative && it.capabilities == capabilities }) return@mutate snapshot
            snapshot.copy(requests = snapshot.requests + (window to existing + request))
        }
        reconcile(window)

        // Return whichever request actually landed, so two racing callers share one claim.
        return sharedRequest(window, capabilities) ?: request
    }

    /** The existing imperative claim for exactly [capabilities] on [window], if any. */
    private fun sharedRequest(
        window: WindowKey,
        capabilities: Set<Capability>,
    ): ProtectionRequest? =
        current.requests[window]
            ?.firstOrNull { it.isImperative && it.capabilities == capabilities }

    /**
     * Drops [request] and withdraws protection if it was the last claim on its window.
     *
     * **Idempotent.** Releasing an already-released request is a no-op and, critically, does not
     * decrement anything else — a double release cannot strip protection from a screen that still
     * wants it. Removal is by identity, so an identically-configured sibling is untouched.
     */
    fun release(request: ProtectionRequest) {
        val window = request.window
        mutate { snapshot ->
            val existing = snapshot.requests[window] ?: return@mutate snapshot
            // Identity, not equality: two requests for the same capabilities are separate claims.
            val index = existing.indexOfFirst { it === request }
            if (index < 0) return@mutate snapshot

            val remaining = existing.filterIndexed { i, _ -> i != index }
            snapshot.copy(
                requests =
                    if (remaining.isEmpty()) {
                        // Drop the key entirely so "no entry" and "empty entry" cannot diverge.
                        snapshot.requests - window
                    } else {
                        snapshot.requests + (window to remaining)
                    },
            )
        }
        reconcile(window)
    }

    /**
     * Re-points requests held against [WindowKey.Unbound] at [window], now that a host exists.
     *
     * Without this, a request made before first composition — from application startup, or a
     * navigation observer running ahead of the UI — would sit unapplied forever while reporting
     * itself active.
     */
    fun bindWindow(window: WindowKey) {
        if (window == WindowKey.Unbound) return

        mutate { snapshot ->
            val pending = snapshot.requests[WindowKey.Unbound] ?: return@mutate snapshot
            pending.forEach { it.window = window }
            snapshot.copy(
                requests =
                    snapshot.requests - WindowKey.Unbound +
                        (window to snapshot.requests[window].orEmpty() + pending),
            )
        }
        reconcile(window)
    }

    /**
     * Releases every request on [window]. Called when a window is destroyed.
     *
     * Without it, a window torn down without its boundaries leaving composition cleanly would leave
     * requests outstanding forever — a leak that surfaces as a permanently black screenshot on some
     * unrelated screen.
     */
    fun releaseWindow(window: WindowKey) {
        mutate { snapshot -> snapshot.copy(requests = snapshot.requests - window) }
        platform.clearProtection(window)
        reconcileAppSwitcher(window)
    }

    /** Records an opt-in and the posture that must accompany it. */
    fun grantOptIn(
        capability: Capability,
        posture: FailurePosture,
    ) {
        mutate { snapshot ->
            snapshot.copy(
                optIns = snapshot.optIns + (capability to posture),
                // A fresh opt-in deserves a fresh attempt: clear any earlier failure so a capability
                // that failed once is not written off for the rest of the session.
                failedMechanisms = snapshot.failedMechanisms - capability,
            )
        }
        // The opt-in may have unlocked a capability outstanding requests already asked for, so retry
        // them rather than waiting for the next navigation.
        current.requests.keys.forEach(::reconcile)
    }

    /** Sets the app-switcher mode and applies the consequence immediately. */
    fun setAppSwitcherMode(mode: AppSwitcherProtection) {
        mutate { snapshot -> snapshot.copy(appSwitcherMode = mode) }
        val windows = current.requests.keys
        // Always is the one mode that must act with no request outstanding — with an empty request
        // map there is no window to iterate, so fall back to the unbound key.
        if (windows.isEmpty()) reconcileAppSwitcher(WindowKey.Unbound) else windows.forEach(::reconcileAppSwitcher)
    }

    /**
     * Brings the platform in line with the current snapshot for [window].
     *
     * Deliberately outside the compare-and-set loop: the loop body can retry under contention, and a
     * retried platform call would toggle the window's protection flag more than once. Toggling a
     * visible window tears down and recreates its surface, which the user sees as a black frame.
     */
    private fun reconcile(window: WindowKey) {
        val capabilities = current.effectiveCapabilities(window)

        if (capabilities.isEmpty()) {
            platform.clearProtection(window)
        } else {
            when (platform.applyProtection(window, capabilities)) {
                ProtectionOutcome.Applied -> Unit

                // No host yet. The request stands; bindWindow() will apply it. Reporting a failure
                // here would fire the posture on ordinary startup ordering.
                ProtectionOutcome.Deferred -> Unit

                ProtectionOutcome.Failed -> recordFailure(capabilities)
            }
        }
        pruneStaleFailures()
        reconcileAppSwitcher(window)
    }

    /**
     * Forgets failures for capabilities nothing currently requests.
     *
     * A failure describes a live attempt, not a permanent verdict. Keeping it past the last request
     * that provoked it would report `MechanismUnavailable` for a capability the library is no longer
     * even trying to use.
     *
     * Deliberately *not* scoped to the window being reconciled: the same capability may be demanded
     * by another window whose mechanism is still broken, and that failure must survive.
     */
    private fun pruneStaleFailures() {
        mutate { snapshot ->
            if (snapshot.failedMechanisms.isEmpty()) return@mutate snapshot
            val live = snapshot.failedMechanisms.filterTo(mutableSetOf(), snapshot::isRequestedAnywhere)
            if (live.size == snapshot.failedMechanisms.size) snapshot else snapshot.copy(failedMechanisms = live)
        }
    }

    /**
     * Records a mechanism failure and reports it.
     *
     * A mechanism that did not install must never be reported as protection. Recording it makes
     * `supportLevel()` tell the truth; reporting it lets the application act at the moment of failure
     * rather than by polling. Only prevention capabilities are recorded — a detection capability has
     * no mechanism to fail in this path and no posture to govern it.
     */
    private fun recordFailure(capabilities: Set<Capability>) {
        val prevention = capabilities.filterTo(mutableSetOf()) { it.isPrevention }
        if (prevention.isEmpty()) return

        mutate { it.copy(failedMechanisms = it.failedMechanisms + prevention) }
        prevention.forEach(onProtectionFailure)
    }

    /**
     * Applies standalone app-switcher protection, unless capture prevention already covers it.
     *
     * On Android the prevention primitive obscures recents as an inseparable side effect, so calling
     * the recents-only primitive on top would be redundant work with a visible artifact to show
     * for it.
     */
    private fun reconcileAppSwitcher(window: WindowKey) {
        val snapshot = current
        val coveredByPrevention = snapshot.effectiveCapabilities(window).any { it.coversAppSwitcher }
        platform.applyAppSwitcherProtection(window, snapshot.shouldProtectAppSwitcher() && !coveredByPrevention)
    }

    /**
     * Applies [transform] to the snapshot, retrying until it lands uncontended.
     *
     * A `transform` returning its input unchanged signals "nothing to do" and exits without a write,
     * which is what makes the idempotent paths allocation-free rather than merely harmless.
     */
    private inline fun mutate(transform: (RegistryState) -> RegistryState) {
        while (true) {
            val snapshot = state.load()
            val updated = transform(snapshot)
            if (updated === snapshot) return
            if (state.compareAndSet(snapshot, updated)) {
                _snapshots.value = updated
                return
            }
        }
    }
}

/**
 * Whether this capability's platform primitive obscures the app-switcher snapshot as a side effect.
 *
 * Android's `FLAG_SECURE` hides the recents thumbnail inseparably, so applying the recents-only
 * primitive on top of it is redundant.
 */
private val Capability.coversAppSwitcher: Boolean
    get() = this == Capability.ScreenshotPrevention || this == Capability.RecordingPrevention
