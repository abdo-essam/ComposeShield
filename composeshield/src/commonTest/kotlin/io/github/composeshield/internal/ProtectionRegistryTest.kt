package io.github.composeshield.internal

import io.github.composeshield.Capability
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProtectionRegistryTest {
    private val platform = FakePlatformProtection()
    private val registry = ProtectionRegistry(platform)
    private val window = WindowKey("test-window")

    @Test
    fun `acquiring applies protection and releasing withdraws it`() {
        val request = registry.acquire(window, setOf(Capability.ScreenshotPrevention))
        assertContains(platform.protectedWindows, window)

        registry.release(request)
        assertFalse(window in platform.protectedWindows)
    }

    @Test
    fun `nested requests release protection only on the last exit`() {
        val outer = registry.acquire(window, setOf(Capability.ScreenshotPrevention))
        val inner = registry.acquire(window, setOf(Capability.ScreenshotPrevention))

        registry.release(inner)
        assertContains(
            platform.protectedWindows,
            window,
            "releasing the inner boundary must not unprotect a window the outer one still claims",
        )

        registry.release(outer)
        assertFalse(window in platform.protectedWindows)
    }

    @Test
    fun `releasing twice does not decrement another request's claim`() {
        val first = registry.acquire(window, setOf(Capability.ScreenshotPrevention))
        val second = registry.acquire(window, setOf(Capability.ScreenshotPrevention))

        registry.release(first)
        registry.release(first)

        assertContains(
            platform.protectedWindows,
            window,
            "a double release must be a no-op, not a second decrement stripping the survivor's claim",
        )

        registry.release(second)
        assertFalse(window in platform.protectedWindows)
    }

    @Test
    fun `imperative acquire is idempotent rather than reference-counted`() {
        val capabilities = setOf(Capability.ScreenshotPrevention)

        val first = registry.acquireShared(window, capabilities)
        val second = registry.acquireShared(window, capabilities)

        assertTrue(first === second, "two imperative acquires for the same capabilities share one claim")

        registry.release(first)
        assertFalse(window in platform.protectedWindows)
    }

    @Test
    fun `imperative release leaves a declarative boundary's protection intact`() {
        val imperative = registry.acquireShared(window, setOf(Capability.ScreenshotPrevention))
        registry.acquire(window, setOf(Capability.ScreenshotPrevention))

        registry.release(imperative)

        assertContains(platform.protectedWindows, window)
    }

    @Test
    fun `requests union their capabilities rather than intersecting them`() {
        registry.acquire(window, setOf(Capability.ScreenshotPrevention))
        registry.acquire(window, setOf(Capability.RecordingPrevention))

        assertEquals(
            setOf(Capability.ScreenshotPrevention, Capability.RecordingPrevention),
            platform.lastRequestedCapabilities,
            "under-applying would leave one of the two boundaries silently unprotected",
        )
    }

    @Test
    fun `separate windows are protected independently`() {
        val other = WindowKey("other-window")
        val request = registry.acquire(window, setOf(Capability.ScreenshotPrevention))
        registry.acquire(other, setOf(Capability.ScreenshotPrevention))

        registry.release(request)

        assertFalse(window in platform.protectedWindows)
        assertContains(platform.protectedWindows, other, "one window's release must not affect another")
    }

    @Test
    fun `a request made before a window exists is applied once one appears`() {
        registry.acquire(WindowKey.Unbound, setOf(Capability.ScreenshotPrevention))
        assertFalse(window in platform.protectedWindows)

        registry.bindWindow(window)

        assertContains(platform.protectedWindows, window)
    }

    @Test
    fun `destroying a window releases every request on it`() {
        registry.acquire(window, setOf(Capability.ScreenshotPrevention))
        registry.acquire(window, setOf(Capability.RecordingPrevention))

        registry.releaseWindow(window)

        assertFalse(window in platform.protectedWindows)
        assertFalse(registry.current.isProtected(window), "no request may outlive its window")
    }

    @Test
    fun `a deferred application is not reported as a protection failure`() {
        val failures = mutableListOf<Capability>()
        val deferring = FakePlatformProtection().apply { nextOutcome = ProtectionOutcome.Deferred }
        val subject = ProtectionRegistry(deferring, onProtectionFailure = failures::add)

        subject.acquire(window, setOf(Capability.ScreenshotPrevention))

        assertTrue(
            failures.isEmpty(),
            "a request awaiting a host is ordinary startup ordering, not a mechanism failure — " +
                "reporting it would fire the fail-closed posture and blank the screen for nothing",
        )
    }

    @Test
    fun `a failed mechanism is reported and recorded`() {
        val failures = mutableListOf<Capability>()
        val failing = FakePlatformProtection().apply { nextOutcome = ProtectionOutcome.Failed }
        val subject = ProtectionRegistry(failing, onProtectionFailure = failures::add)

        subject.acquire(window, setOf(Capability.ScreenshotPrevention))

        assertEquals(listOf(Capability.ScreenshotPrevention), failures)
        assertContains(subject.current.failedMechanisms, Capability.ScreenshotPrevention)
    }

    @Test
    fun `a failure is forgotten once nothing requests the capability`() {
        val failing = FakePlatformProtection().apply { nextOutcome = ProtectionOutcome.Failed }
        val subject = ProtectionRegistry(failing)

        val request = subject.acquire(window, setOf(Capability.ScreenshotPrevention))
        subject.release(request)

        assertFalse(
            Capability.ScreenshotPrevention in subject.current.failedMechanisms,
            "a failure describes a live attempt; keeping it would poison the capability for the session",
        )
    }

    @Test
    fun `an unchanged effective capability set does not re-invoke the platform`() {
        val capabilities = setOf(Capability.ScreenshotPrevention)
        val first = registry.acquire(window, capabilities)
        val second = registry.acquire(window, capabilities)

        registry.release(first)

        assertEquals(
            listOf("apply:${window.id}"),
            platform.applyLog,
            "a second identical request adds nothing to the union — every redundant apply is a " +
                "main-thread round-trip, and on a real window a needless toggle tears down its surface",
        )
        assertContains(platform.protectedWindows, window)

        registry.release(second)

        assertEquals(
            listOf("apply:${window.id}", "clear:${window.id}"),
            platform.applyLog,
            "skipping redundant reconciles must not skip the genuine final release",
        )
    }

    @Test
    fun `a failed application is retried by the next reconcile rather than trusted`() {
        val failing = FakePlatformProtection().apply { nextOutcome = ProtectionOutcome.Failed }
        val subject = ProtectionRegistry(failing)

        subject.acquire(window, setOf(Capability.ScreenshotPrevention))
        assertEquals(1, failing.applyLog.size)

        subject.acquire(window, setOf(Capability.ScreenshotPrevention))

        assertEquals(
            2,
            failing.applyLog.count { it.startsWith("fail") },
            "caching must only trust outcomes that were actually applied; a failed install has to be retried",
        )
    }
}
