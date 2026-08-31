package io.github.composeshield.internal

import io.github.composeshield.Capability
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The imperative API path and its composition with declarative boundaries.
 *
 * Imperative claims are state-based (idempotent), while declarative boundaries are counted by composition.
 */
class ImperativeApiTest {
    private val platform = FakePlatformProtection()
    private val registry = ProtectionRegistry(platform)
    private val window = WindowKey("imperative-window")
    private val prevention = setOf(Capability.ScreenshotPrevention)

    @Test
    fun `repeated imperative acquires collapse onto one claim`() {
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
    fun `a single release withdraws an imperative claim acquired repeatedly`() {
        val handle = registry.acquireShared(window, prevention)
        registry.acquireShared(window, prevention)

        registry.release(handle)

        assertFalse(window in platform.protectedWindows)
    }

    @Test
    fun `releaseShared withdraws active imperative claim directly`() {
        registry.acquireShared(window, prevention)
        assertTrue(window in platform.protectedWindows)

        registry.releaseShared(window, prevention)
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
        val imperative = registry.acquireShared(window, prevention)
        val declarative = registry.acquire(window, prevention)

        registry.release(imperative)

        assertContains(platform.protectedWindows, window)

        registry.release(declarative)
        assertFalse(window in platform.protectedWindows, "protection lifts only once every claim is gone")
    }

    @Test
    fun `releasing the declarative boundary leaves the imperative claim active`() {
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
