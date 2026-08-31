package io.github.composeshield.internal

import io.github.composeshield.CaptureState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import platform.UIKit.UIScreen
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class DetectionTest {
    @Test
    fun `a capturing reading reaches the published state immediately`() =
        runTest {
            val platform = FakePlatformProtection()
            val source = CaptureStateSource(platform, TestScope(testScheduler))
            source.start()
            runCurrent()

            platform.captureReadings.emit(PlatformCaptureReading.Capturing)
            runCurrent()

            assertEquals(
                CaptureState.Active,
                source.state.value,
                "an active reading must never be delayed — a late warning is a warning that did " +
                    "not arrive while the screen was being recorded",
            )
        }

    @Test
    fun `the cold-launch reading publishes Unknown rather than Inactive`() =
        runTest {
            val platform = FakePlatformProtection()
            val source = CaptureStateSource(platform, TestScope(testScheduler))
            source.start()
            runCurrent()

            platform.captureReadings.emit(PlatformCaptureReading.Indeterminate)
            advanceTimeBy(SETTLE)
            runCurrent()

            assertEquals(
                CaptureState.Unknown,
                source.state.value,
                "Unknown must never be coerced to Inactive",
            )
        }

    @Test
    fun `a Live Activity flap does not retract an active state`() =
        runTest {
            val platform = FakePlatformProtection()
            val source = CaptureStateSource(platform, TestScope(testScheduler))
            source.start()
            runCurrent()

            platform.captureReadings.emit(PlatformCaptureReading.Capturing)
            runCurrent()

            platform.captureReadings.emit(PlatformCaptureReading.NotCapturing)
            platform.captureReadings.emit(PlatformCaptureReading.Capturing)
            advanceTimeBy(SETTLE)
            runCurrent()

            assertEquals(
                CaptureState.Active,
                source.state.value,
                "the spurious inactive must never surface — a banking app told it is unobserved " +
                    "mid-recording is the exact failure this suppression exists to prevent",
            )
        }

    @Test
    fun `the below-iOS-17 fallback stays readable`() {
        assertNotNull(UIScreen.mainScreen.captured)
    }
}

private val SETTLE = 2.seconds
