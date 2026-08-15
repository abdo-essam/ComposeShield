package io.github.composeshield.internal

import io.github.composeshield.Capability
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * FR-018 / US5 scenario 4 — the registry is correct under concurrent mutation.
 *
 * The registry is a compare-and-set loop over an immutable snapshot, and that shape has one specific
 * failure mode worth testing: a lost update, where two threads read the same snapshot and the second
 * write erases the first request. In production that surfaces as a screen that believes it is
 * protected and is not — silent, and only visible once someone captures the screen.
 *
 * `Dispatchers.Default` is used deliberately rather than the test dispatcher. A single-threaded
 * dispatcher would serialise every acquire and the contention these tests exist to provoke would
 * never happen.
 */
class ThreadSafetyTest {
    private val platform = FakePlatformProtection()
    private val registry = ProtectionRegistry(platform)
    private val window = WindowKey("concurrent-window")
    private val prevention = setOf(Capability.ScreenshotPrevention)

    @Test
    fun `concurrent acquires from many threads all land`() =
        runTest {
            val requests =
                withContext(Dispatchers.Default) {
                    List(CONCURRENT_CALLERS) { async { registry.acquire(window, prevention) } }.awaitAll()
                }

            assertEquals(
                CONCURRENT_CALLERS,
                registry.current.requests.getValue(window).size,
                "a lost update here is a screen that reports itself protected while it is not",
            )
            assertContains(platform.protectedWindows, window)

            requests.forEach(registry::release)
            assertFalse(window in platform.protectedWindows)
        }

    @Test
    fun `concurrent acquire and release settle on the claims that remain`() =
        runTest {
            val held = registry.acquire(window, prevention)

            withContext(Dispatchers.Default) {
                List(CONCURRENT_CALLERS) {
                    async {
                        val request = registry.acquire(window, prevention)
                        registry.release(request)
                    }
                }.awaitAll()
            }

            assertEquals(
                listOf(held),
                registry.current.requests[window],
                "interleaved release must remove exactly its own claim and never a bystander's",
            )
            assertContains(platform.protectedWindows, window)
        }

    @Test
    fun `concurrent imperative acquires still collapse onto one claim`() =
        runTest {
            // The compare-and-set body re-checks for an existing shared request, because a racing
            // caller can install one between the initial read and the swap. Without that re-check
            // this produces one claim per thread and the imperative API stops being idempotent.
            val handles =
                withContext(Dispatchers.Default) {
                    List(CONCURRENT_CALLERS) { async { registry.acquireShared(window, prevention) } }.awaitAll()
                }

            assertEquals(1, registry.current.requests.getValue(window).size)
            assertTrue(handles.all { it === handles.first() }, "every racing caller must receive the one claim")
        }

    @Test
    fun `concurrent mutation across separate windows does not interfere`() =
        runTest {
            val windows = List(CONCURRENT_CALLERS) { WindowKey("window-$it") }

            withContext(Dispatchers.Default) {
                windows.map { async { registry.acquire(it, prevention) } }.awaitAll()
            }

            assertEquals(
                windows.toSet(),
                platform.protectedWindows.toSet(),
                "each window's state is independent; a shared snapshot must not drop one window's entry",
            )
        }

    private companion object {
        /** Enough callers to provoke compare-and-set retries without making the test slow. */
        const val CONCURRENT_CALLERS = 64
    }
}
