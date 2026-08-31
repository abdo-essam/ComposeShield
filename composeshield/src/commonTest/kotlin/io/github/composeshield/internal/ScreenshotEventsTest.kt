package io.github.composeshield.internal

import app.cash.turbine.test
import io.github.composeshield.Capability
import io.github.composeshield.SupportLevel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.count
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class ScreenshotEventsTest {
    @Test
    fun `each screenshot is delivered exactly once`() =
        runTest {
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
            val platform = FakePlatformProtection()

            platform.observeScreenshotEvents().test {
                platform.screenshots.emit(Unit)
                assertEquals(Unit, awaitItem())
                cancel()
            }
        }

    private companion object {
        const val SCREENSHOT_BURST = 5
    }
}
