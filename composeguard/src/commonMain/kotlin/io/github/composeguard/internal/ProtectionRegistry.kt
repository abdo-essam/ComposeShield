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
 * Constitution Principle II keeps state machines, policy, and lifecycle coordination in common code
 * and leaves the platform actuals with the irreducible platform call. This is that state machine.
 * It reference-counts requests, resolves the effective capability set and failure posture per
 * window, and drives [PlatformProtection] when — and only when — the outcome actually changes.
 *
 * **Reference counting is not an optimisation, it is the correctness requirement.** Only one
 * physical protection primitive exists per window. A departing screen that cleared it directly
 * would unprotect a still-visible screen underneath, and nothing would report the exposure. So
 * protection is withdrawn only when the last outstanding request on the window is gone (FR-004).
 *
 * **Thread-safety** (FR-018): mutations are a compare-and-set over an immutable [RegistryState]
 * snapshot, so no caller can observe a half-applied change and no lock is held across the platform
 * call. Platform application is marshalled to the main thread by the actuals themselves, because
 * `ViewRootImpl.checkThread()` throws off-main — and only *sometimes*, which makes it an
 * intermittent bug rather than an honest one (research.md R8).
 *
 * @param platform the platform primitive to delegate to.
 * @param onProtectionFailure invoked when a mechanism fails to install or stops working, so the
 *   failure is observable as it happens (FR-022c) rather than only by polling support state.
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
     * posture at the *moment* a mechanism breaks (FR-022b). Polling [current] on recomposition
     * would only notice a mid-session failure if something else happened to recompose, which for a
     * static protected screen may be never.
     */
    val snapshots: StateFlow<RegistryState> = _snapshots.asStateFlow()

    /** The current snapshot. Safe to read from any thread; never a partially-applied state. */
    val current: RegistryState get() = state.load()

    /**
     * Records a new request and applies protection if this changed the window's effective set.
     *
     * Pass [WindowKey.Unbound] when no host exists yet; the request is held and applied once
     * [bindWindow] reports one, rather than being dropped (the spec's "no host available" case).
     *
     * @return the request, to be passed back to [release]. Each call produces a distinct claim even
     *   when the capabilities are identical, so two boundaries are counted as two.
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
     * **Idempotent, deliberately not reference-counted** (US5 scenario 2, C4). Acquiring twice and
     * releasing once releases protection — the opposite of the declarative path, and the difference
     * is intentional. A boundary's lifetime is delimited by composition, so counting it is exact.
     * An imperative caller has no such structure: a policy object that calls `acquire()` on every
     * navigation and `release()` once on teardown would, under reference counting, leak protection
     * permanently. Collapsing makes the imperative surface state-like, which is how it reads.
     *
     * Distinct capability sets remain distinct claims, so this cannot merge two callers wanting
     * different things.
     */
    fun acquireShared(
        window: WindowKey,
        capabilities: Set<Capability>,
    ): ProtectionRequest {
        current.requests[window]
            ?.firstOrNull { it.isImperative && it.capabilities == capabilities }
            ?.let { return it }

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
        return current.requests[window]
            ?.firstOrNull { it.isImperative && it.capabilities == capabilities }
            ?: request
    }

    /**
     * Drops [request] and withdraws protection if it was the last claim on its window.
     *
     * **Idempotent.** Releasing an already-released request is a no-op rather than an error, and
     * critically it does not decrement anything else — a double release cannot strip protection
     * from a screen that still wants it (C3). Removal is by identity, so an identically-configured
     * sibling request is untouched.
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
     * Called when a rendering host first becomes available. Without this, a request made before
     * first composition — from application startup, or a navigation observer that runs ahead of the
     * UI — would sit unapplied forever while reporting itself active.
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
     * Releases every request on [window].
     *
     * Called when a window is destroyed. Without it, a window torn down without its boundaries
     * leaving composition cleanly would leave requests outstanding forever — a leak that shows up
     * as a permanently black screenshot on some unrelated screen (SC-007).
     */
    fun releaseWindow(window: WindowKey) {
        mutate { snapshot -> snapshot.copy(requests = snapshot.requests - window) }
        platform.clearProtection(window)
        reconcileAppSwitcher(window)
    }

    /** Records an opt-in and the posture that must accompany it (FR-023b). */
    fun grantOptIn(
        capability: Capability,
        posture: FailurePosture,
    ) {
        mutate { snapshot ->
            snapshot.copy(
                optIns = snapshot.optIns + (capability to posture),
                // A fresh opt-in deserves a fresh attempt: clear any earlier failure so a
                // capability that failed once is not written off for the rest of the session.
                failedMechanisms = snapshot.failedMechanisms - capability,
            )
        }
        // The opt-in may have unlocked a capability that outstanding requests already asked for,
        // so retry them rather than waiting for the next navigation to apply what was just granted.
        current.requests.keys.forEach(::reconcile)
    }

    /** Sets the app-switcher mode and applies the consequence immediately. */
    fun setAppSwitcherMode(mode: AppSwitcherProtection) {
        mutate { snapshot -> snapshot.copy(appSwitcherMode = mode) }
        val windows = current.requests.keys
        // Always is the one mode that must act with no request outstanding — with an empty request
        // map there is no window to iterate, so fall back to the unbound key (FR-015c).
        if (windows.isEmpty()) reconcileAppSwitcher(WindowKey.Unbound) else windows.forEach(::reconcileAppSwitcher)
    }

    /**
     * Brings the platform in line with the current snapshot for [window].
     *
     * Deliberately outside the compare-and-set loop: the loop body can retry under contention, and
     * a retried platform call would toggle the window's protection flag more than once. Toggling a
     * visible window tears down and recreates its surface, which the user sees as a flicker or a
     * black frame (research.md R8).
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
     * A failure describes a live attempt, not a permanent verdict on the mechanism. Keeping it past
     * the last request that provoked it would report `Unsupported(MechanismUnavailable)` for a
     * capability the library is no longer even trying to use, and would mean one transient failure
     * poisoned the capability for the rest of the session.
     *
     * Deliberately *not* scoped to the window being reconciled: the same capability may be demanded
     * by another window whose mechanism is still broken, and that window's failure must survive.
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
     * FR-022: a mechanism that did not install must never be reported as protection. Recording it
     * makes `supportLevel()` tell the truth; reporting it lets the application act at the moment of
     * failure rather than by polling. Only prevention capabilities are recorded — a detection
     * capability has no mechanism to fail in this path and no posture to govern it.
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
     * The suppression is what keeps behaviour consistent across platforms without double-applying
     * (FR-015d): on Android the prevention primitive obscures recents as an inseparable side
     * effect, so also calling the recents-only primitive would be redundant work with a visible
     * artifact to show for it.
     */
    private fun reconcileAppSwitcher(window: WindowKey) {
        val snapshot = current
        val coveredByPrevention = snapshot.effectiveCapabilities(window).any { it.coversAppSwitcher }
        platform.applyAppSwitcherProtection(window, snapshot.shouldProtectAppSwitcher() && !coveredByPrevention)
    }

    /**
     * Applies [transform] to the snapshot, retrying until it lands uncontended.
     *
     * A `transform` that returns its input unchanged signals "nothing to do" and exits without a
     * write, which is what makes the idempotent paths — releasing twice, binding with no pending
     * requests — allocation-free rather than merely harmless.
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
 * primitive on top of it is redundant (FR-015d, research.md R7).
 */
private val Capability.coversAppSwitcher: Boolean
    get() = this == Capability.ScreenshotPrevention || this == Capability.RecordingPrevention

/**
 * A complete, immutable snapshot of the registry.
 *
 * Immutability is what makes the registry thread-safe without locks: mutations build a new snapshot
 * and swap it in with a compare-and-set, so a reader either sees the whole previous state or the
 * whole next one, never a half-applied change. It also keeps the read path allocation-free, which
 * matters because [io.github.composeguard.SecureContent] is meant to be cheap enough to wrap every
 * sensitive screen (constitution Principle V).
 *
 * The trade is that writes allocate. That is the right way round: requests change on navigation,
 * reads happen on every support query and every recomposition.
 */
internal data class RegistryState(
    /**
     * Live requests per window. A window disappears from the map entirely when its last request is
     * released, so an empty entry can never be mistaken for an active-but-empty claim.
     */
    val requests: Map<WindowKey, List<ProtectionRequest>> = emptyMap(),
    /** Capabilities opted into via the unsanctioned-mechanism flow, with the posture declared. */
    val optIns: Map<Capability, FailurePosture> = emptyMap(),
    /** Capabilities whose mechanism was requested but failed to install, or has stopped working. */
    val failedMechanisms: Set<Capability> = emptySet(),
    /** The application's app-switcher preference. */
    val appSwitcherMode: AppSwitcherProtection = AppSwitcherProtection.Automatic,
) {
    /** Whether any request is outstanding on [window]. */
    fun isProtected(window: WindowKey): Boolean = requests[window]?.isNotEmpty() == true

    /** Whether any request is outstanding on any window. */
    fun isProtectedAnywhere(): Boolean = requests.values.any { it.isNotEmpty() }

    /**
     * The union of capabilities demanded on [window].
     *
     * A union rather than an intersection: capabilities are independently requestable (FR-005), so
     * a window where one boundary wants screenshot prevention and another wants recording
     * prevention needs both. Under-applying here would leave a boundary silently unprotected.
     */
    fun effectiveCapabilities(window: WindowKey): Set<Capability> {
        val windowRequests = requests[window] ?: return emptySet()
        return when (windowRequests.size) {
            0 -> emptySet()

            // The overwhelmingly common case: one boundary on the window. Reuse its set rather
            // than copying it, so the read path stays allocation-free (Principle V, SC-006).
            1 -> windowRequests[0].capabilities

            else -> buildSet { windowRequests.forEach { addAll(it.capabilities) } }
        }
    }

    /**
     * Whether any window anywhere has an outstanding request for [capability].
     *
     * Used to answer preclusion, which is a platform-wide question: Android suppresses the
     * screenshot callback for the activity whose window carries `FLAG_SECURE`, and a consumer
     * asking "can I rely on screenshot events" needs one answer, not one per window.
     */
    fun isRequestedAnywhere(capability: Capability): Boolean =
        requests.values.any { window -> window.any { capability in it.capabilities } }

    /**
     * The posture governing failures on [window]: the most protective any outstanding request
     * declared.
     *
     * Postures are declared at opt-in and therefore attach to capabilities, but they are *applied*
     * per window, because only one physical protection primitive exists per window. Two boundaries
     * on one window that disagree cannot both be honoured, so the safer reading wins — a
     * fail-closed request is never silently downgraded by an unrelated fail-open one sharing its
     * window (FR-022a).
     *
     * Returns `null` when no outstanding request on the window involves an opted-in capability, in
     * which case there is no failure to have a posture about.
     */
    fun effectiveFailurePosture(window: WindowKey): FailurePosture? {
        var resolved: FailurePosture? = null
        for (capability in effectiveCapabilities(window)) {
            val posture = optIns[capability] ?: continue
            resolved = resolved?.let { FailurePosture.mostProtective(it, posture) } ?: posture
        }
        return resolved
    }

    /**
     * Whether [window]'s content must be obscured because a mechanism it depends on failed.
     *
     * This is FR-022b at the moment of loss. It reads the *current* failure set rather than a flag
     * latched at installation, so a mechanism that installs successfully and stops working later
     * obscures the content just the same.
     */
    fun shouldObscureContent(window: WindowKey): Boolean {
        if (effectiveFailurePosture(window) != FailurePosture.FailClosed) return false
        return effectiveCapabilities(window).any { it in failedMechanisms }
    }

    /**
     * Whether app-switcher protection should be applied, given the mode and outstanding requests.
     *
     * [AppSwitcherProtection.Automatic] follows the requests — the common case needs no separate
     * opt-in (FR-015a) — while the other two modes ignore them entirely, which is what makes
     * standalone switcher protection possible with no boundary composed at all (FR-015c).
     */
    fun shouldProtectAppSwitcher(): Boolean =
        when (appSwitcherMode) {
            AppSwitcherProtection.Automatic -> isProtectedAnywhere()
            AppSwitcherProtection.Always -> true
            AppSwitcherProtection.Disabled -> false
        }
}

/**
 * One outstanding demand for protection on a window.
 *
 * Created when a declarative boundary enters composition or an imperative acquire is called;
 * discarded on exit or release. Protection is active on a window while at least one request
 * survives.
 *
 * **Identity is by instance, not by content.** Two boundaries requesting exactly the same
 * capabilities are two distinct claims, and releasing one must not release the other — so this is
 * deliberately not a `data class`. Structural equality would make a double-release of one boundary
 * silently strip the other's protection, which is the exact leak reference counting exists to
 * prevent.
 *
 * [window] is a `var` because a request may be made before any host window exists (the spec's "no
 * host available" edge case). It is booked against [WindowKey.Unbound] and re-pointed at the real
 * window by [ProtectionRegistry.bindWindow] once one appears.
 */
internal class ProtectionRequest(
    val capabilities: Set<Capability>,
    var window: WindowKey,
    /**
     * Whether this claim came from the imperative API rather than a composed boundary.
     *
     * Imperative claims for identical capabilities collapse onto one another; declarative ones
     * never do. See [ProtectionRegistry.acquireShared] for why the two paths differ.
     */
    val isImperative: Boolean = false,
)
