package io.github.composeshield.internal

import io.github.composeshield.Capability
import io.github.composeshield.SupportLevel
import kotlinx.coroutines.flow.Flow

internal interface PlatformProtection {
    fun applyProtection(
        window: WindowKey,
        capabilities: Set<Capability>,
    ): ProtectionOutcome

    fun clearProtection(window: WindowKey)

    fun observeCaptureState(): Flow<PlatformCaptureReading>

    fun observeScreenshotEvents(): Flow<Unit>

    fun applyTaskSwitcherProtection(
        window: WindowKey,
        enabled: Boolean,
    )

    fun platformSupport(capability: Capability): SupportLevel

    fun observeForegroundEvents(): Flow<Unit>

    val preventionPrecludesScreenshotEvents: Boolean
}

internal expect fun createPlatformProtection(): PlatformProtection

internal data class WindowKey(
    val id: String,
) {
    override fun toString(): String = "WindowKey($id)"

    internal companion object {
        val Unbound: WindowKey = WindowKey("unbound")
    }
}

internal enum class ProtectionOutcome {
    Applied,

    Deferred,

    Failed,
}

internal enum class PlatformCaptureReading {
    Capturing,

    NotCapturing,

    Indeterminate,
}
