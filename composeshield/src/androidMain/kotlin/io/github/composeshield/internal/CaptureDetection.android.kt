package io.github.composeshield.internal

import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Display
import android.view.WindowManager
import io.github.composeshield.SupportLevel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Reports whether the screen is being recorded or mirrored to an external display.
 *
 * Two independent signals, unioned — either one alone means capture is happening:
 *
 * - **Recording** via `WindowManager.addScreenRecordingCallback` (API 35+). Detects only
 *   MediaProjection-based recording, so `scrcpy`, ADB `screenrecord`, HDMI capture, and OEM
 *   recorders that bypass MediaProjection are invisible to it. This is the blind spot that makes
 *   [io.github.composeshield.CaptureState.Inactive] mean "no evidence", never a guarantee.
 * - **External displays** via `DisplayManager` (API 24+), which covers casting to a non-secure
 *   display even where recording detection is unavailable.
 *
 * Below API 35 the recording half reports unsupported with no fallback.
 */
internal class CaptureDetection {
    fun support(): SupportLevel = supportedFromApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)

    /**
     * The current reading, re-emitted whenever either signal changes.
     *
     * Emits its first value immediately on collection rather than waiting for a change — that is
     * what makes [io.github.composeshield.internal.CaptureStateSource.refresh] a genuine re-read.
     */
    fun readings(): Flow<PlatformCaptureReading> =
        callbackFlow {
            val activity = anyRegisteredActivity()
            val displayManager = activity?.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager

            if (activity == null) {
                // With no window we genuinely do not know — report Indeterminate, never NotCapturing.
                trySend(PlatformCaptureReading.Indeterminate)
                awaitClose { }
                return@callbackFlow
            }

            var recording = false
            var mirroring = displayManager.hasExternalDisplay()

            fun publish() {
                trySend(
                    when {
                        recording || mirroring -> PlatformCaptureReading.Capturing

                        // Below API 35, the absence of an external display says nothing about
                        // whether a recorder is running.
                        supportsRecordingCallback -> PlatformCaptureReading.NotCapturing

                        else -> PlatformCaptureReading.Indeterminate
                    },
                )
            }

            val displayListener =
                object : DisplayManager.DisplayListener {
                    override fun onDisplayAdded(displayId: Int) = refreshMirroring()

                    override fun onDisplayRemoved(displayId: Int) = refreshMirroring()

                    override fun onDisplayChanged(displayId: Int) = refreshMirroring()

                    fun refreshMirroring() {
                        mirroring = displayManager.hasExternalDisplay()
                        publish()
                    }
                }
            // The Handler overload (not single-arg): the latter needs API 33 and this path goes back to 24.
            displayManager?.registerDisplayListener(displayListener, Handler(Looper.getMainLooper()))

            var recordingCallback: java.util.function.Consumer<Int>? = null
            if (supportsRecordingCallback) {
                val windowManager = activity.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                val callback =
                    java.util.function.Consumer<Int> { state ->
                        recording = state == WindowManager.SCREEN_RECORDING_STATE_VISIBLE
                        publish()
                    }
                recordingCallback = callback

                // SECURITY-CRITICAL: this returns the *current* state, and discarding it is a real
                // vulnerability rather than an untidiness. An attacker who starts recording before the
                // app launches produces no transition, so a callback-only implementation would report
                // "not recording" for the entire session.
                val initial = windowManager.addScreenRecordingCallback(activity.mainExecutor, callback)
                recording = initial == WindowManager.SCREEN_RECORDING_STATE_VISIBLE
            }

            publish()

            awaitClose {
                displayManager?.unregisterDisplayListener(displayListener)
                if (supportsRecordingCallback && recordingCallback != null) {
                    val windowManager = activity.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                    windowManager.removeScreenRecordingCallback(recordingCallback)
                }
            }
        }

    private companion object {
        val supportsRecordingCallback: Boolean
            get() = sdkInt >= Build.VERSION_CODES.VANILLA_ICE_CREAM
    }
}

/**
 * Whether a display other than the built-in one is presenting content that is not capture-secure.
 *
 * `FLAG_SECURE` content is already withheld from non-secure displays by the OS, so a *secure*
 * external display is not a leak and does not count as capture.
 */
private fun DisplayManager?.hasExternalDisplay(): Boolean =
    this?.displays?.any { display ->
        // `Display.isSecure` is hidden API; the FLAG_SECURE bit is the public equivalent and needs
        // no reflection to read.
        val secure = display.flags and Display.FLAG_SECURE != 0
        display.displayId != Display.DEFAULT_DISPLAY && !secure
    } == true
