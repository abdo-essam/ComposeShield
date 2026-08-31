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

/**
 * Reports whether the screen is being recorded, mirrored, or streamed.
 *
 * **Change signal and state read are deliberately different APIs**, because the best choice differs
 * for each:
 *
 * - *When to re-read* comes from `UIScreen.capturedDidChangeNotification`, which — contrary to a
 *   widely repeated claim — carries **no deprecation** and works uniformly from iOS 15 up. It is
 *   also hierarchy-independent, so prevention's reparenting cannot disturb it.
 * - *What the state is* comes from `UIWindowScene.sceneCaptureState` on iOS 17+, falling back to
 *   `UIScreen.isCaptured` below it. `isCaptured` is deprecated at 27.0, and the scene answer is
 *   narrower in the right way — this scene's capture, not the whole device's.
 *
 * Two platform defects are visible here and neither is hidden: cold-launch under-reporting (hence
 * the [PlatformCaptureReading.Indeterminate] seed) and an iOS 26.2 Live Activity false negative
 * (absorbed by [CaptureStateSource], not here — an actual that debounced on its own behalf would be
 * making policy). Both affect `isCaptured` and `sceneCaptureState` identically. See
 * `docs/platform-notes.md`.
 */
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

    /**
     * The present reading, from the scene where iOS offers one.
     *
     * `UIWindowScene.sceneCaptureState` (iOS 17+) is preferred over the 27.0-deprecated
     * `UIScreen.isCaptured`, and is the more accurate answer for a windowed app: it reports whether
     * *this scene's* content is being captured, where `isCaptured` would flag an app merely
     * alongside a recorded one.
     *
     * `Unspecified` maps to [PlatformCaptureReading.Indeterminate], never to "not capturing" —
     * resolving "I don't know" to the reassuring answer is the one inference this library must never
     * make.
     */
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
