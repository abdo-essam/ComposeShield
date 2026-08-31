package io.github.composeshield.internal

import io.github.composeshield.Capability
import io.github.composeshield.TaskSwitcherProtection

internal data class RegistryState(
    val requests: Map<WindowKey, List<ProtectionRequest>> = emptyMap(),
    val failedMechanisms: Set<Capability> = emptySet(),
    val taskSwitcherMode: TaskSwitcherProtection = TaskSwitcherProtection.Automatic,
    val applied: Map<WindowKey, Set<Capability>> = emptyMap(),
) {
    fun isProtected(window: WindowKey): Boolean = requests[window]?.isNotEmpty() == true

    fun isProtectedAnywhere(): Boolean = requests.values.any { it.isNotEmpty() }

    fun effectiveCapabilities(window: WindowKey): Set<Capability> {
        val windowRequests = requests[window] ?: return emptySet()
        return when (windowRequests.size) {
            0 -> emptySet()
            1 -> windowRequests[0].capabilities
            else -> buildSet { windowRequests.forEach { addAll(it.capabilities) } }
        }
    }

    fun isRequestedAnywhere(capability: Capability): Boolean =
        requests.values.any { window -> window.any { capability in it.capabilities } }

    fun shouldProtectTaskSwitcher(): Boolean =
        when (taskSwitcherMode) {
            TaskSwitcherProtection.Automatic -> isProtectedAnywhere()
            TaskSwitcherProtection.Always -> true
            TaskSwitcherProtection.Disabled -> false
        }
}

internal class ProtectionRequest(
    val capabilities: Set<Capability>,
    var window: WindowKey,
    val isImperative: Boolean = false,
)
