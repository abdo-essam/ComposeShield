package io.github.composeshield.internal

import io.github.composeshield.Capability
import io.github.composeshield.TaskSwitcherProtection
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/**
 * The single place all protection state lives.
 *
 * Reference-counts requests, resolves the effective capability set per window,
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

    /** The snapshot as an observable stream. */
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
            val matches = existing.any { it.isImperative && it.capabilities == capabilities }
            if (matches) return@mutate snapshot
            snapshot.copy(requests = snapshot.requests + (window to existing + request))
        }
        reconcile(window)

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
     * Releases any active imperative claim matching [capabilities] on [window].
     * Idempotent — no-op if no matching claim exists.
     */
    fun releaseShared(
        window: WindowKey,
        capabilities: Set<Capability>,
    ) {
        sharedRequest(window, capabilities)?.let(::release)
    }

    /**
     * Drops [request] and withdraws protection if it was the last claim on its window.
     *
     * **Idempotent.** Releasing an already-released request is a no-op and does not decrement
     * anything else — a double release cannot strip protection from a screen that still wants it.
     * Removal is by identity, so an identically-configured sibling is untouched.
     */
    fun release(request: ProtectionRequest) {
        val window = request.window
        mutate { snapshot ->
            val existing = snapshot.requests[window] ?: return@mutate snapshot
            val index = existing.indexOfFirst { it === request }
            if (index < 0) return@mutate snapshot

            val remaining = existing.filterIndexed { i, _ -> i != index }
            snapshot.copy(
                requests =
                    if (remaining.isEmpty()) {
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
            // The deferred apply parked under Unbound targeted whatever host resolved; once a real
            // window exists, force a fresh reconcile against it rather than trusting the stale
            // applied entry (idempotent, at worst one extra platform call).
            snapshot.copy(
                requests =
                    snapshot.requests - WindowKey.Unbound +
                        (window to snapshot.requests[window].orEmpty() + pending),
                applied = snapshot.applied - WindowKey.Unbound,
            )
        }
        reconcile(window)
    }

    /**
     * Releases every request on [window]. Called when a window is destroyed.
     *
     * Without it, a window torn down without its boundaries disposing cleanly would leave requests
     * outstanding forever — a leak that surfaces as a permanently black screenshot on some
     * unrelated screen.
     */
    fun releaseWindow(window: WindowKey) {
        mutate { snapshot ->
            snapshot.copy(
                requests = snapshot.requests - window,
                applied = snapshot.applied - window,
            )
        }
        platform.clearProtection(window)
        reconcileTaskSwitcher(window)
    }

    /** Sets the app-switcher mode and applies the consequence immediately. */
    fun setTaskSwitcherMode(mode: TaskSwitcherProtection) {
        mutate { snapshot -> snapshot.copy(taskSwitcherMode = mode) }
        val windows = current.requests.keys
        // Always must act even with no requests outstanding — fall back to the unbound key so there
        // is something to iterate over when the request map is empty.
        if (windows.isEmpty()) reconcileTaskSwitcher(WindowKey.Unbound) else windows.forEach(::reconcileTaskSwitcher)
    }

    /**
     * Brings the platform in line with the current snapshot for [window].
     *
     * Deliberately outside the compare-and-set loop: the loop body can retry under contention, and a
     * retried platform call would toggle the window's protection flag more than once. Toggling a
     * visible window tears down and recreates its surface, which the user sees as a black frame.
     *
     * Skipped when the effective capability set equals what the platform was last told to apply
     * (see [RegistryState.applied]) — a second concurrent request adding nothing new, or releasing
     * one of two identical requests, needs no main-thread round-trip. A [ProtectionOutcome.Failed]
     * mechanism is never recorded as applied, so the next reconcile retries the install rather than
     * trusting a mechanism that is not in force.
     */
    private fun reconcile(window: WindowKey) {
        val capabilities = current.effectiveCapabilities(window)

        if (capabilities != current.applied[window].orEmpty()) {
            val outcome =
                if (capabilities.isEmpty()) {
                    platform.clearProtection(window)
                    ProtectionOutcome.Applied
                } else {
                    platform.applyProtection(window, capabilities)
                }

            if (outcome == ProtectionOutcome.Failed) {
                recordFailure(capabilities)
            } else {
                mutate { snapshot ->
                    if (snapshot.applied[window] == capabilities) return@mutate snapshot
                    snapshot.copy(applied = snapshot.applied + (window to capabilities))
                }
            }
        }
        pruneStaleFailures()
        reconcileTaskSwitcher(window)
    }

    /**
     * Forgets failures for capabilities nothing currently requests.
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
     */
    private fun recordFailure(capabilities: Set<Capability>) {
        val prevention = capabilities.filterTo(mutableSetOf()) { it.isPrevention }
        if (prevention.isEmpty()) return

        mutate { it.copy(failedMechanisms = it.failedMechanisms + prevention) }

        prevention.forEach { capability ->
            // The callback runs mid-reconcile on the caller's thread: letting it throw would unwind
            // through this reconcile path and turn a reported failure into a caller-visible crash.
            // The swallow is deliberate — [SupportLevel] and [RegistryState.failedMechanisms] stay
            // truthful; only this best-effort notification channel is affected.
            runCatching { onProtectionFailure(capability) }
        }
    }

    /**
     * Applies standalone app-switcher protection, unless capture prevention already covers it.
     */
    private fun reconcileTaskSwitcher(window: WindowKey) {
        val snapshot = current
        val coveredByPrevention = snapshot.effectiveCapabilities(window).any { it.coversAppSwitcher }
        platform.applyTaskSwitcherProtection(window, snapshot.shouldProtectTaskSwitcher() && !coveredByPrevention)
    }

    /**
     * Applies [transform] to the snapshot, retrying until it lands uncontended.
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
