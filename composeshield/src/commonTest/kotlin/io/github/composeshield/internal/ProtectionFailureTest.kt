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

    @Test
    fun `a failed mechanism logs an error even when no consumer callback is supplied`() {
        val loggedErrors = mutableListOf<String>()
        io.github.composeshield.ComposeShield.logger =
            object : io.github.composeshield.ComposeShieldLogger {
                override fun log(
                    level: io.github.composeshield.ComposeShieldLogLevel,
                    tag: String,
                    message: String,
                    throwable: Throwable?,
                ) {
                    if (level == io.github.composeshield.ComposeShieldLogLevel.Warn ||
                        level == io.github.composeshield.ComposeShieldLogLevel.Error
                    ) {
                        loggedErrors.add(message)
                    }
                }
            }

        try {
            val failing = FakePlatformProtection().apply { nextOutcome = ProtectionOutcome.Failed }
            val subject = ProtectionRegistry(failing)

            subject.acquire(window, setOf(Capability.ScreenshotPrevention))

            assertTrue(loggedErrors.isNotEmpty(), "Failure must be logged when no callback is supplied")
            assertTrue(loggedErrors.any { it.contains("ScreenshotPrevention") })
        } finally {
            io.github.composeshield.ComposeShield.logger = io.github.composeshield.ComposeShieldLoggers.None
        }
    }

    private companion object {
        const val REPLAY_WAIT_MS = 5_000L
    }
}
