package io.github.composeshield.internal

import app.cash.turbine.turbineScope
import io.github.composeshield.CaptureState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class CaptureStateSourceTest {
    @Test
    fun `state begins Unknown and is never seeded to Inactive`() =
        runTest {
            val platform = FakePlatformProtection()
            val source = CaptureStateSource(platform, TestScope(testScheduler))

            assertEquals(
                CaptureState.Unknown,
                source.state.value,
                "both platforms under-report at cold launch; seeding Inactive would tell a banking " +
                    "app it is unobserved while it is being recorded",
            )
        }

    @Test
    fun `an Active reading is published immediately`() =
        runTest {
            val platform = FakePlatformProtection()
            val source = CaptureStateSource(platform, TestScope(testScheduler))
            source.start()

            platform.captureReadings.emit(PlatformCaptureReading.Capturing)
            advanceUntilIdle()

            assertEquals(CaptureState.Active, source.state.value)
        }

    @Test
    fun `C8 - a spurious Inactive is suppressed when Active follows quickly`() =
        runTest {
            val platform = FakePlatformProtection()
            val source = CaptureStateSource(platform, TestScope(testScheduler))
            source.start()

            platform.captureReadings.emit(PlatformCaptureReading.Capturing)
            advanceUntilIdle()

            platform.captureReadings.emit(PlatformCaptureReading.NotCapturing)
            advanceTimeBy(200)
            platform.captureReadings.emit(PlatformCaptureReading.Capturing)
            advanceUntilIdle()

            assertEquals(
                CaptureState.Active,
                source.state.value,
                "the transient Inactive must never reach a consumer — an app that hid its content on " +
                    "it would flicker every time a Live Activity expanded",
            )
        }

    @Test
    fun `a sustained Inactive is published once it survives the suppression window`() =
        runTest {
            val platform = FakePlatformProtection()
            val source = CaptureStateSource(platform, TestScope(testScheduler))
            source.start()

            platform.captureReadings.emit(PlatformCaptureReading.Capturing)
            advanceUntilIdle()

            platform.captureReadings.emit(PlatformCaptureReading.NotCapturing)
            advanceUntilIdle()

            assertEquals(
                CaptureState.Inactive,
                source.state.value,
                "suppression must delay a genuine transition, not swallow it",
            )
        }

    @Test
    fun `an Indeterminate reading does not retract a live Active`() =
        runTest {
            val platform = FakePlatformProtection()
            val source = CaptureStateSource(platform, TestScope(testScheduler))
            source.start()

            platform.captureReadings.emit(PlatformCaptureReading.Capturing)
            advanceUntilIdle()

            platform.captureReadings.emit(PlatformCaptureReading.Indeterminate)
            advanceUntilIdle()

            assertEquals(
                CaptureState.Active,
                source.state.value,
                "'I cannot tell' is a weaker claim than 'capture is happening' and must not override it",
            )
        }

    @Test
    fun `returning to the foreground re-polls rather than trusting the last reading`() =
        runTest {
            val platform = FakePlatformProtection()
            val source = CaptureStateSource(platform, TestScope(testScheduler))
            source.start()
            advanceUntilIdle()

            assertEquals(1, platform.captureSubscriptions, "start() subscribes once")

            platform.foregrounds.emit(Unit)
            advanceUntilIdle()

            assertEquals(
                2,
                platform.captureSubscriptions,
                "a foreground must force a fresh read: capture that started while backgrounded " +
                    "produces no transition, and without this the app reports 'not captured' for " +
                    "the rest of the session",
            )
        }

    @Test
    fun `a foreground re-poll does not blank a live Active reading`() =
        runTest {
            val platform = FakePlatformProtection()
            val source = CaptureStateSource(platform, TestScope(testScheduler))
            source.start()

            platform.captureReadings.emit(PlatformCaptureReading.Capturing)
            advanceUntilIdle()

            platform.foregrounds.emit(Unit)
            advanceUntilIdle()

            assertEquals(
                CaptureState.Active,
                source.state.value,
                "re-subscribing must not momentarily retract a known-Active state — a consumer " +
                    "hiding content on Active would flash it back on every foreground",
            )
        }

    @Test
    fun `every collector observes the same value as the current value`() =
        runTest {
            val platform = FakePlatformProtection()
            val source = CaptureStateSource(platform, TestScope(testScheduler))
            source.start()

            turbineScope {
                val first = source.state.testIn(backgroundScope)
                val second = source.state.testIn(backgroundScope)

                assertEquals(CaptureState.Unknown, first.awaitItem())
                assertEquals(CaptureState.Unknown, second.awaitItem())

                platform.captureReadings.emit(PlatformCaptureReading.Capturing)

                val firstActive = first.awaitItem()
                val secondActive = second.awaitItem()

                assertEquals(
                    firstActive,
                    secondActive,
                    "a single shared upstream means collectors cannot disagree",
                )
                assertEquals(
                    source.state.value,
                    firstActive,
                    ".value and emissions must agree",
                )

                first.cancel()
                second.cancel()
            }
        }
}
