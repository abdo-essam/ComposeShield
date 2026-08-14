package io.github.composeguard.internal

import io.github.composeguard.Capability
import io.github.composeguard.SupportLevel
import io.github.composeguard.SupportLevel.Unsupported.Reason
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests the failure reporting flow and contract.
 *
 * When a platform protection mechanism fails to install or stops working:
 * 1. The failure is recorded, causing `supportLevel` to report `Unsupported(MechanismUnavailable)`.
 * 2. The failure is delivered to `onProtectionFailure` / `protectionFailures` stream.
 * 3. The library does NOT automatically obscure the UI, preserving usability while notifying the app.
 * 4. When the request is released, the failure verdict is cleared.
 */
class ProtectionFailureTest {
    private val window = WindowKey("test-window")

    @Test
    fun `a failed mechanism reports MechanismUnavailable support level and emits failure event`() {
        val failures = mutableListOf<Capability>()
        val platform = FakePlatformProtection().apply { nextOutcome = ProtectionOutcome.Failed }
        val registry = ProtectionRegistry(platform, onProtectionFailure = failures::add)
        val resolver = SupportResolver(platform)

        registry.acquire(window, setOf(Capability.ScreenshotPrevention))

        assertEquals(listOf(Capability.ScreenshotPrevention), failures)
        assertEquals(
            SupportLevel.Unsupported(Reason.MechanismUnavailable),
            resolver.resolve(Capability.ScreenshotPrevention, registry.current),
        )
    }

    @Test
    fun `a failure is cleared once nothing requests the capability`() {
        val platform = FakePlatformProtection().apply { nextOutcome = ProtectionOutcome.Failed }
        val registry = ProtectionRegistry(platform)
        val resolver = SupportResolver(platform)

        val request = registry.acquire(window, setOf(Capability.ScreenshotPrevention))
        assertTrue(Capability.ScreenshotPrevention in registry.current.failedMechanisms)

        registry.release(request)

        assertFalse(Capability.ScreenshotPrevention in registry.current.failedMechanisms)
        assertEquals(
            SupportLevel.Supported,
            resolver.resolve(Capability.ScreenshotPrevention, registry.current),
        )
    }
}
