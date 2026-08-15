package io.github.composeshield.internal

import app.cash.turbine.test
import io.github.composeshield.Capability
import io.github.composeshield.SupportLevel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.count
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * US3 — screenshot events, and the conflict that makes them conditional.
 *
 * The event itself is deliberately thin: a bare `Unit`, carrying no payload, because any payload
 * would risk conveying the very content the library exists to protect (FR-025). What is worth
 * testing is therefore not the event's shape but its *delivery guarantees* — exactly one per
 * screenshot, an empty stream rather than an error where unsupported, and the Android rule where
 * active prevention silently precludes the capability entirely.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ScreenshotEventsTest {
    @Test
    fun `each screenshot is delivered exactly once`() =
        runTest {
            // US3 scenario 1. A duplicate delivery would double-count in an audit log, which for a
            // consumer writing compliance records is a wrong answer rather than a noisy one.
            val platform = FakePlatformProtection()

            platform.observeScreenshotEvents().test {
                platform.screenshots.emit(Unit)
                awaitItem()

                platform.screenshots.emit(Unit)
                awaitItem()

                expectNoEvents()
                cancel()
            }
        }

    @Test
    fun `every screenshot in a burst is delivered`() =
        runTest {
            val platform = FakePlatformProtection()

            platform.observeScreenshotEvents().test {
                repeat(SCREENSHOT_BURST) { platform.screenshots.emit(Unit) }

                repeat(SCREENSHOT_BURST) { awaitItem() }
                expectNoEvents()
                cancel()
            }
        }

    @Test
    fun `an unsupporting platform yields an empty stream rather than an error`() =
        runTest {
            // FR-014. A consumer collecting this on an OS too old for the capability must simply see
            // nothing — throwing would crash the host app for a capability it merely asked about.
            val platform = FakePlatformProtection().apply { screenshotEventsAvailable = false }

            assertEquals(0, platform.observeScreenshotEvents().count())
        }

    @Test
    fun `no event is emitted when no screenshot is taken`() =
        runTest {
            val platform = FakePlatformProtection()

            platform.observeScreenshotEvents().test {
                expectNoEvents()
                cancel()
            }
        }

    @Test
    fun `events remain available on a platform that does not preclude them`() =
        runTest {
            // iOS: the screenshot notification fires regardless of what the window is doing, so
            // prevention and screenshot events coexist. Asserted here so the Android-specific
            // preclusion below is never generalised into a rule for every platform.
            val platform = FakePlatformProtection(preventionPrecludesScreenshotEvents = false)
            val registry = ProtectionRegistry(platform)
            val resolver = SupportResolver(platform)

            registry.acquire(WindowKey("ios-window"), setOf(Capability.ScreenshotPrevention))

            assertEquals(
                SupportLevel.Supported,
                resolver.resolve(Capability.ScreenshotEvents, registry.current),
            )
        }

    @Test
    fun `active prevention precludes events and releasing restores them`() =
        runTest {
            // US3 scenario 4 / FR-020c. On Android the platform does not invoke the capture callback
            // on a window with FLAG_SECURE set (AOSP Activity.java:9940), so the capability must
            // report itself precluded rather than sit silently delivering nothing.
            val platform = FakePlatformProtection()
            val registry = ProtectionRegistry(platform)
            val resolver = SupportResolver(platform)
            val window = WindowKey("android-window")

            assertEquals(
                SupportLevel.Supported,
                resolver.resolve(Capability.ScreenshotEvents, registry.current),
            )

            val request = registry.acquire(window, setOf(Capability.ScreenshotPrevention))

            assertEquals(
                SupportLevel.Unsupported(SupportLevel.Unsupported.Reason.PrecludedByActiveCapability),
                resolver.resolve(Capability.ScreenshotEvents, registry.current),
                "prevention wins the conflict; the superseded capability must say so rather than " +
                    "leave a consumer waiting for events that will never arrive",
            )

            registry.release(request)

            assertEquals(
                SupportLevel.Supported,
                resolver.resolve(Capability.ScreenshotEvents, registry.current),
                "the preclusion is transient — it must lift with the prevention that caused it",
            )
        }

    @Test
    fun `the event carries no payload`() =
        runTest {
            // FR-025, pinned as a test because it is the kind of API that attracts "helpful"
            // additions — a timestamp, a window id, a thumbnail — each of which would widen the
            // library's exposure to the content it protects.
            val platform = FakePlatformProtection()

            platform.observeScreenshotEvents().test {
                platform.screenshots.emit(Unit)
                assertEquals(Unit, awaitItem())
                cancel()
            }
        }

    private companion object {
        /** Enough to catch a conflated or dropped emission without depending on buffer sizing. */
        const val SCREENSHOT_BURST = 5
    }
}
