package io.github.composeshield.internal

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSProcessInfo
import platform.UIKit.UISceneCaptureStateActive
import platform.UIKit.UISceneCaptureStateInactive
import platform.UIKit.UIScreen
import platform.UIKit.UIScreenCapturedDidChangeNotification

@OptIn(ExperimentalForeignApi::class)
internal class CaptureDetection {
    fun readings(): Flow<PlatformCaptureReading> =
        callbackFlow {
            trySend(PlatformCaptureReading.Indeterminate)

            val observer =
                NSNotificationCenter.defaultCenter.addObserverForName(
                    name = UIScreenCapturedDidChangeNotification,
                    `object` = null,
                    queue = NSOperationQueue.mainQueue,
                ) { _ ->
                    trySend(currentReading())
                }

            trySend(currentReading())

            awaitClose { NSNotificationCenter.defaultCenter.removeObserver(observer) }
        }

    private fun currentReading(): PlatformCaptureReading {
        val traits = activeWindowScene()?.traitCollection
        if (traits != null && supportsSceneCaptureState) {
            return when (traits.sceneCaptureState) {
                UISceneCaptureStateActive -> PlatformCaptureReading.Capturing
                UISceneCaptureStateInactive -> PlatformCaptureReading.NotCapturing
                else -> PlatformCaptureReading.Indeterminate
            }
        }

        return if (UIScreen.mainScreen.captured) {
            PlatformCaptureReading.Capturing
        } else {
            PlatformCaptureReading.NotCapturing
        }
    }

    private companion object {
        val supportsSceneCaptureState: Boolean
            get() = NSProcessInfo.processInfo.operatingSystemVersion.useContents { majorVersion >= 17 }
    }
}
