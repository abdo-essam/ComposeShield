package io.github.composeguard.internal

import io.github.composeguard.Capability
import io.github.composeguard.FailurePosture
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Contract test C5 — posture conflict resolution.
 *
 * Only one physical protection primitive exists per window, so two boundaries that disagree about
 * failure handling cannot both be honoured. The safer reading has to win, or an unrelated fail-open
 * screen sharing a window could silently downgrade a fail-closed one.
 */
class FailurePostureTest {
    private val window = WindowKey("test-window")

    @Test
    fun `C5 - the most protective posture wins on conflict`() {
        assertEquals(
            FailurePosture.FailClosed,
            FailurePosture.mostProtective(FailurePosture.FailOpen, FailurePosture.FailClosed),
        )
        assertEquals(
            FailurePosture.FailClosed,
            FailurePosture.mostProtective(FailurePosture.FailClosed, FailurePosture.FailOpen),
            "resolution must not depend on the order the requests happened to arrive",
        )
        assertEquals(
            FailurePosture.FailOpen,
            FailurePosture.mostProtective(FailurePosture.FailOpen, FailurePosture.FailOpen),
        )
    }

    @Test
    fun `a fail-closed request is not downgraded by a fail-open one sharing its window`() {
        val platform = FakePlatformProtection()
        val registry = ProtectionRegistry(platform)

        registry.grantOptIn(Capability.ScreenshotPrevention, FailurePosture.FailOpen)
        registry.grantOptIn(Capability.RecordingPrevention, FailurePosture.FailClosed)

        registry.acquire(window, setOf(Capability.ScreenshotPrevention, Capability.RecordingPrevention))

        assertEquals(FailurePosture.FailClosed, registry.current.effectiveFailurePosture(window))
    }

    @Test
    fun `content is obscured only when a fail-closed mechanism has actually failed`() {
        val platform = FakePlatformProtection()
        val registry = ProtectionRegistry(platform)
        registry.grantOptIn(Capability.ScreenshotPrevention, FailurePosture.FailClosed)

        registry.acquire(window, setOf(Capability.ScreenshotPrevention))
        assertFalse(
            registry.current.shouldObscureContent(window),
            "a working fail-closed mechanism must render normally — the posture is not a mode",
        )

        // FR-022b applies at the moment of loss, not only at installation: break the mechanism on a
        // boundary that installed successfully.
        platform.nextOutcome = ProtectionOutcome.Failed
        registry.acquire(window, setOf(Capability.ScreenshotPrevention))

        assertTrue(registry.current.shouldObscureContent(window))
    }

    @Test
    fun `a fail-open failure leaves content visible`() {
        val platform = FakePlatformProtection().apply { nextOutcome = ProtectionOutcome.Failed }
        val registry = ProtectionRegistry(platform)
        registry.grantOptIn(Capability.ScreenshotPrevention, FailurePosture.FailOpen)

        registry.acquire(window, setOf(Capability.ScreenshotPrevention))

        assertFalse(
            registry.current.shouldObscureContent(window),
            "fail-open means the application handles the failure itself, not that the library blanks it",
        )
    }
}
