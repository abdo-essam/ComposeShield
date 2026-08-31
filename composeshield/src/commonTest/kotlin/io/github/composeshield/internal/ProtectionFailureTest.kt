package io.github.composeshield.internal

import io.github.composeshield.Capability
import io.github.composeshield.SupportLevel
import io.github.composeshield.SupportLevel.Unsupported.Reason
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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

    @Test
    fun `a failure emitted before any collector attaches is replayed to a late collector`() {
        val core =
            ShieldCore(
                FakePlatformProtection().apply { nextOutcome = ProtectionOutcome.Failed },
            )
        core.registry.acquire(WindowKey("unobserved"), setOf(Capability.ScreenshotPrevention))

        runTest {
            assertEquals(
                Capability.ScreenshotPrevention,
                withTimeout(REPLAY_WAIT_MS) { core.protectionFailures.first() },
                "a security-relevant signal must survive the window before a collector attaches",
            )
        }
    }

    @Test
    fun `a throwing consumer callback does not crash the caller`() {
        val failing = FakePlatformProtection().apply { nextOutcome = ProtectionOutcome.Failed }
        val subject = ProtectionRegistry(failing, onProtectionFailure = { error("consumer bug") })

        val request = subject.acquire(WindowKey("callback-crash"), setOf(Capability.ScreenshotPrevention))

        assertTrue(
            Capability.ScreenshotPrevention in subject.current.failedMechanisms,
            "the durable record survives a throwing callback — only the notification channel is best-effort",
        )
        subject.release(request)
    }

    private companion object {
        const val REPLAY_WAIT_MS = 5_000L
    }
}
