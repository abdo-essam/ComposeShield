package io.github.composeguard.internal

import io.github.composeguard.Capability
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * US5 — the imperative path, and how it composes with the declarative one.
 *
 * The two paths deliberately count differently, and the asymmetry is the thing most likely to be
 * "fixed" by someone who has not read why. A boundary's lifetime is delimited by composition, so
 * counting each one is exact. An imperative caller has no such structure: a policy object acquiring
 * on every navigation and releasing once on teardown would leak protection permanently under
 * reference counting. These tests pin both halves so neither can drift into the other.
 */
class ImperativeApiTest {
    private val platform = FakePlatformProtection()
    private val registry = ProtectionRegistry(platform)
    private val window = WindowKey("imperative-window")
    private val prevention = setOf(Capability.ScreenshotPrevention)

    @Test
    fun `C4 - repeated imperative acquires collapse onto one claim`() {
        val first = registry.acquireShared(window, prevention)
        val second = registry.acquireShared(window, prevention)
        val third = registry.acquireShared(window, prevention)

        assertTrue(first === second && second === third)
        assertEquals(
            1,
            registry.current.requests.getValue(window).size,
            "three imperative acquires must not book three claims — a policy object calling acquire " +
                "on every navigation would otherwise leak protection permanently",
        )
    }

    @Test
    fun `C4 - a single release withdraws an imperative claim acquired repeatedly`() {
        // US5 scenario 2: the imperative surface reads as state, not as a counter.
        val handle = registry.acquireShared(window, prevention)
        registry.acquireShared(window, prevention)

        registry.release(handle)

        assertFalse(window in platform.protectedWindows)
    }

    @Test
    fun `imperative claims for different capability sets stay separate`() {
        val screenshots = registry.acquireShared(window, prevention)
        val recording = registry.acquireShared(window, setOf(Capability.RecordingPrevention))

        assertFalse(
            screenshots === recording,
            "collapsing across capability sets would silently merge two callers wanting different things",
        )

        registry.release(screenshots)

        assertContains(
            platform.protectedWindows,
            window,
            "releasing one capability set must leave the other's claim standing",
        )
    }

    @Test
    fun `releasing the imperative handle leaves declarative protection active`() {
        // US5 scenario 3 / FR-019: both paths reach one registry, so neither can unprotect the other.
        val imperative = registry.acquireShared(window, prevention)
        val declarative = registry.acquire(window, prevention)

        registry.release(imperative)

        assertContains(platform.protectedWindows, window)

        registry.release(declarative)
        assertFalse(window in platform.protectedWindows, "protection lifts only once every claim is gone")
    }

    @Test
    fun `releasing the declarative boundary leaves the imperative claim active`() {
        // The converse of the above, which is the direction that actually leaks in practice: a screen
        // navigating away must not unprotect what a background policy still demands.
        val declarative = registry.acquire(window, prevention)
        registry.acquireShared(window, prevention)

        registry.release(declarative)

        assertContains(platform.protectedWindows, window)
    }

    @Test
    fun `an imperative request made before a window exists is applied once one appears`() {
        registry.acquireShared(WindowKey.Unbound, prevention)
        assertFalse(window in platform.protectedWindows)

        registry.bindWindow(window)

        assertContains(
            platform.protectedWindows,
            window,
            "an imperative acquire from application startup must be held, never dropped",
        )
    }
}
