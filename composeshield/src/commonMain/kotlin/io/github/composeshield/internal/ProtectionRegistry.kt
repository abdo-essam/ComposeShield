package io.github.composeshield.internal

import io.github.composeshield.Capability
import io.github.composeshield.TaskSwitcherProtection
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

@OptIn(ExperimentalAtomicApi::class)
internal class ProtectionRegistry(
    private val platform: PlatformProtection,
    private val onProtectionFailure: (Capability) -> Unit = {},
) {
    private val reconcileLock = Any()

    private val state = AtomicReference(RegistryState())
    private val _snapshots = MutableStateFlow(RegistryState())

    val snapshots: StateFlow<RegistryState> = _snapshots.asStateFlow()

    val current: RegistryState get() = state.load()

    fun acquire(
        window: WindowKey,
        capabilities: Set<Capability>,
    ): ProtectionRequest {
        val request = ProtectionRequest(capabilities.toSet(), window)
        mutate { snapshot ->
            val existing = snapshot.requests[window].orEmpty()
            snapshot.copy(requests = snapshot.requests + (window to existing + request))
        }
        reconcile(window)
        return request
    }

    fun acquireShared(
        window: WindowKey,
        capabilities: Set<Capability>,
    ): ProtectionRequest {
        sharedRequest(window, capabilities)?.let { return it }

        val request = ProtectionRequest(capabilities.toSet(), window, isImperative = true)
        mutate { snapshot ->
            val existing = snapshot.requests[window].orEmpty()
            val matches = existing.any { it.isImperative && it.capabilities == capabilities }
            if (matches) return@mutate snapshot
            snapshot.copy(requests = snapshot.requests + (window to existing + request))
        }
        reconcile(window)

        return sharedRequest(window, capabilities) ?: request
    }

    private fun sharedRequest(
        window: WindowKey,
        capabilities: Set<Capability>,
    ): ProtectionRequest? =
        current.requests[window]
            ?.firstOrNull { it.isImperative && it.capabilities == capabilities }

    fun releaseShared(
        window: WindowKey,
        capabilities: Set<Capability>,
    ) {
        sharedRequest(window, capabilities)?.let(::release)
    }

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

    fun bindWindow(window: WindowKey) {
        if (window == WindowKey.Unbound) return

        mutate { snapshot ->
            val pending = snapshot.requests[WindowKey.Unbound] ?: return@mutate snapshot
            pending.forEach { it.window = window }
            snapshot.copy(
                requests =
                    snapshot.requests - WindowKey.Unbound +
                        (window to snapshot.requests[window].orEmpty() + pending),
                applied = snapshot.applied - WindowKey.Unbound,
            )
        }
        reconcile(window)
    }

    fun releaseWindow(window: WindowKey) {
        serialized(reconcileLock) {
            mutate { snapshot ->
                snapshot.copy(
                    requests = snapshot.requests - window,
                    applied = snapshot.applied - window,
                )
            }
            platform.clearProtection(window)
            reconcileTaskSwitcher(window)
        }
    }

    fun setTaskSwitcherMode(mode: TaskSwitcherProtection) {
        mutate { snapshot -> snapshot.copy(taskSwitcherMode = mode) }
        val windows = current.requests.keys
        if (windows.isEmpty()) reconcileTaskSwitcher(WindowKey.Unbound) else windows.forEach(::reconcileTaskSwitcher)
    }

    private fun reconcile(window: WindowKey) {
        serialized(reconcileLock) {
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
                        snapshot.copy(
                            applied =
                                if (capabilities.isEmpty()) {
                                    snapshot.applied - window
                                } else {
                                    snapshot.applied + (window to capabilities)
                                },
                        )
                    }
                }
            }
            pruneStaleFailures()
            reconcileTaskSwitcher(window)
        }
    }

    private fun pruneStaleFailures() {
        mutate { snapshot ->
            if (snapshot.failedMechanisms.isEmpty()) return@mutate snapshot
            val live = snapshot.failedMechanisms.filterTo(mutableSetOf(), snapshot::isRequestedAnywhere)
            if (live.size == snapshot.failedMechanisms.size) snapshot else snapshot.copy(failedMechanisms = live)
        }
    }

    private fun recordFailure(capabilities: Set<Capability>) {
        val prevention = capabilities.filterTo(mutableSetOf()) { it.isPrevention }
        if (prevention.isEmpty()) return

        mutate { it.copy(failedMechanisms = it.failedMechanisms + prevention) }

        prevention.forEach { capability ->
            println("[ComposeShield] WARNING: Protection mechanism failed for capability: $capability")
            runCatching { onProtectionFailure(capability) }
        }
    }

    private fun reconcileTaskSwitcher(window: WindowKey) {
        val snapshot = current
        val coveredByPrevention = snapshot.effectiveCapabilities(window).any { it.coversAppSwitcher }
        platform.applyTaskSwitcherProtection(window, snapshot.shouldProtectTaskSwitcher() && !coveredByPrevention)
    }

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

private val Capability.coversAppSwitcher: Boolean
    get() = this == Capability.ScreenshotPrevention || this == Capability.RecordingPrevention
