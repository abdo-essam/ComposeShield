package io.github.composeshield.internal

import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Display
import android.view.WindowManager
import androidx.annotation.RequiresApi
import io.github.composeshield.SupportLevel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.util.function.Consumer

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
            displayManager?.registerDisplayListener(displayListener, Handler(Looper.getMainLooper()))

            val recordingCallback =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                    registerRecordingCallback(activity, ::publish) { isRecording -> recording = isRecording }
                } else {
                    null
                }

            publish()

            awaitClose {
                displayManager?.unregisterDisplayListener(displayListener)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM && recordingCallback != null) {
                    unregisterRecordingCallback(activity, recordingCallback)
                }
            }
        }

    private companion object {
        val supportsRecordingCallback: Boolean
            get() = sdkInt >= Build.VERSION_CODES.VANILLA_ICE_CREAM
    }

    /** Registers the API-35 recording callback and returns its initial state through [updateRecording]. */
    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    private fun registerRecordingCallback(
        activity: android.app.Activity,
        publish: () -> Unit,
        updateRecording: (Boolean) -> Unit,
    ): Consumer<Int> {
        val windowManager = activity.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val callback =
            Consumer<Int> { state ->
                updateRecording(state == WindowManager.SCREEN_RECORDING_STATE_VISIBLE)
                publish()
            }
        updateRecording(
            windowManager.addScreenRecordingCallback(activity.mainExecutor, callback) ==
                WindowManager.SCREEN_RECORDING_STATE_VISIBLE,
        )
        return callback
    }

    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    private fun unregisterRecordingCallback(
        activity: android.app.Activity,
        callback: Consumer<Int>,
    ) {
        val windowManager = activity.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        windowManager.removeScreenRecordingCallback(callback)
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
        val secure = display.flags and Display.FLAG_SECURE != 0
        display.displayId != Display.DEFAULT_DISPLAY && !secure
    } == true
