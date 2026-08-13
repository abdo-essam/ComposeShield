package io.github.composeguard.internal

import io.github.composeguard.CaptureState
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import platform.UIKit.UIScreen
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.time.Duration.Companion.seconds

/**
 * Contract test C15 — an iOS capture reading reaches the published state without being softened.
 *
 * ### What this can and cannot cover
 *
 * The test binary is headless. There is no `UIApplication`, so anything reaching
 * `UIApplication.sharedApplication.connectedScenes` **segfaults the process** rather than returning
 * nil — which rules out driving [CaptureDetection.readings] directly, since its post-attach read
 * resolves the active scene. That is a property of the host, not a defect: on a device the
 * application object always exists.
 *
 * So the scene-attached read is verified on a device (quickstart M7), which is also the only place a
 * recording can actually be started. What is checked here is the part that carries the security
 * meaning and runs identically on both platforms: that an iOS reading published into
 * [CaptureStateSource] produces the right [CaptureState], and specifically that the cold-launch and
 * Live Activity readings never resolve to a false negative.
 */
class DetectionTest {
    @Test
    fun `C15 - a capturing reading reaches the published state immediately`() =
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
            // FB14607048: at cold launch iOS reports "not captured" while recording is already
            // running, so the detection actual seeds Indeterminate. This asserts the consequence —
            // Unknown reaches the consumer, never the reassuring answer.
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
                "C9: Unknown must never be coerced to Inactive",
            )
        }

    @Test
    fun `a Live Activity flap does not retract an active state`() =
        runTest {
            // iOS 26.2: expanding a Live Activity from the Dynamic Island reports inactive while
            // recording continues. FR-010 suppression must absorb it.
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
                    "mid-recording is the exact failure FR-010 exists to prevent",
            )
        }

    @Test
    fun `the below-iOS-17 fallback stays readable`() {
        // UIScreen.isCaptured is what detection falls back to where no scene trait exists. It is
        // deprecated at 27.0 but not removed; were it to stop being readable, the fallback tier
        // would lose detection silently.
        assertNotNull(UIScreen.mainScreen.captured)
    }
}

/** Comfortably past the suppression window, so a held transition has resolved either way. */
private val SETTLE = 2.seconds
