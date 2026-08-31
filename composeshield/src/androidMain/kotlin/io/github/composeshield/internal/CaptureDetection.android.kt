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

internal class CaptureDetection {
    fun support(): SupportLevel = supportedFromApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)

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

    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    private fun registerRecordingCallback(
        activity: android.app.Activity,
        publish: () -> Unit,
        updateRecording: (Boolean) -> Unit,
    ): java.util.function.Consumer<Int> {
        val windowManager = activity.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val callback =
            java.util.function.Consumer<Int> { state ->
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
        callback: java.util.function.Consumer<Int>,
    ) {
        val windowManager = activity.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        windowManager.removeScreenRecordingCallback(callback)
    }
}

private fun DisplayManager?.hasExternalDisplay(): Boolean =
    this?.displays?.any { display ->
        val secure = display.flags and Display.FLAG_SECURE != 0
        display.displayId != Display.DEFAULT_DISPLAY && !secure
    } == true
