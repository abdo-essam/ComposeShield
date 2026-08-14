package io.github.composeguard.internal

import io.github.composeguard.AppSwitcherProtection
import io.github.composeguard.Capability

/**
 * A complete, immutable snapshot of the registry.
 *
 * Immutability is what makes [ProtectionRegistry] thread-safe without locks: mutations build a new
 * snapshot and swap it in with a compare-and-set, so a reader sees either the whole previous state
 * or the whole next one. It also keeps the read path allocation-free, which matters because
 * [io.github.composeguard.SecureContent] is meant to be cheap enough to wrap every sensitive screen.
 *
 * The trade is that writes allocate — the right way round, since requests change on navigation while
 * reads happen on every support query and every recomposition.
 */
internal data class RegistryState(
    /**
     * Live requests per window. A window leaves the map entirely when its last request is released,
     * so an empty entry can never be mistaken for an active-but-empty claim.
     */
    val requests: Map<WindowKey, List<ProtectionRequest>> = emptyMap(),
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
     * A union rather than an intersection: capabilities are independently requestable, so a window
     * where one boundary wants screenshot prevention and another wants recording prevention needs
     * both. Under-applying would leave a boundary silently unprotected.
     */
    fun effectiveCapabilities(window: WindowKey): Set<Capability> {
        val windowRequests = requests[window] ?: return emptySet()
        return when (windowRequests.size) {
            0 -> emptySet()

            // The common case: one boundary on the window. Reuse its set rather than copying, so the
            // read path stays allocation-free.
            1 -> windowRequests[0].capabilities

            else -> buildSet { windowRequests.forEach { addAll(it.capabilities) } }
        }
    }

    /**
     * Whether any window anywhere has an outstanding request for [capability].
     *
     * Preclusion is a platform-wide question — a consumer asking "can I rely on screenshot events"
     * needs one answer, not one per window.
     */
    fun isRequestedAnywhere(capability: Capability): Boolean =
        requests.values.any { window -> window.any { capability in it.capabilities } }

    /**
     * Whether app-switcher protection should be applied, given the mode and outstanding requests.
     *
     * [AppSwitcherProtection.Automatic] follows the requests; the other two ignore them entirely,
     * which is what makes standalone switcher protection possible with no boundary composed at all.
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
 * **Identity is by instance, not by content** — deliberately not a `data class`. Two boundaries
 * requesting the same capabilities are two distinct claims, and structural equality would make a
 * double-release of one silently strip the other's protection.
 *
 * [window] is a `var` because a request may be made before any host window exists. It is booked
 * against [WindowKey.Unbound] and re-pointed by [ProtectionRegistry.bindWindow] once one appears.
 */
internal class ProtectionRequest(
    val capabilities: Set<Capability>,
    var window: WindowKey,
    /**
     * Whether this claim came from the imperative API rather than a composed boundary.
     *
     * Imperative claims for identical capabilities collapse onto one another; declarative ones never
     * do. See [ProtectionRegistry.acquireShared].
     */
    val isImperative: Boolean = false,
)
