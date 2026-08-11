package io.github.composeguard.internal

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
 * **Change signal and state read are deliberately different APIs**, because the best available
 * choice differs for each:
 *
 * - *When to re-read* comes from `UIScreen.capturedDidChangeNotification`, which — contrary to a
 *   widely repeated claim — carries **no deprecation** and works uniformly from iOS 15 up. Using it
 *   for every version avoids branching for a difference that would not change the answer, and it is
 *   hierarchy-independent, so prevention's reparenting cannot disturb it (research.md R2).
 * - *What the state is* comes from `UIWindowScene.sceneCaptureState` on iOS 17+, falling back to
 *   `UIScreen.isCaptured` below it. `isCaptured` is deprecated at 27.0, and the scene answer is
 *   narrower in the right way — this scene's capture, not the whole device's.
 *
 * ### Two platform defects this cannot fix, and does not hide
 *
 * - **Cold-launch under-report** (FB14607048): if recording is already running at launch, the first
 *   read returns inactive. The *change* path is correct; only the initial read is wrong. This is
 *   why the first emission below is [PlatformCaptureReading.Indeterminate] rather than a reading —
 *   common code publishes `Unknown` rather than a false "you are not being recorded".
 * - **Scene-scoped false negative** (iOS 26.2): expanding a Live Activity from the Dynamic Island
 *   flips the state to inactive while recording continues. Absorbed by the suppression window in
 *   [io.github.composeguard.internal.CaptureStateSource], not here — an actual that debounced on its
 *   own behalf would be making policy, which Principle II forbids.
 *
 * Both affect `isCaptured` and `sceneCaptureState` identically, so neither API avoids them.
 */
@OptIn(ExperimentalForeignApi::class)
internal class CaptureDetection {
    fun readings(): Flow<PlatformCaptureReading> =
        callbackFlow {
            // Seed indeterminate, never a reading. At cold launch iOS reports "not captured" while
            // recording is already in progress, and publishing that would be a false negative in the
            // one direction a security library must never get wrong (FR-009).
            trySend(PlatformCaptureReading.Indeterminate)

            val observer =
                NSNotificationCenter.defaultCenter.addObserverForName(
                    name = UIScreenCapturedDidChangeNotification,
                    `object` = null,
                    queue = NSOperationQueue.mainQueue,
                ) { _ ->
                    trySend(currentReading())
                }

            // A change notification may already have been missed — one fires while the app was
            // backgrounded and nothing was listening — so read once now that the observer is attached.
            trySend(currentReading())

            awaitClose { NSNotificationCenter.defaultCenter.removeObserver(observer) }
        }

    /**
     * The present reading, from the scene where iOS offers one.
     *
     * `UIWindowScene.sceneCaptureState` (iOS 17+) is preferred over `UIScreen.isCaptured`, which is
     * deprecated at 27.0. The scene answer is also the more accurate one for a windowed app: it
     * reports whether *this scene's* content is being captured, where `isCaptured` reports the whole
     * device and would flag an app that is merely alongside a recorded one.
     *
     * `Unspecified` maps to [PlatformCaptureReading.Indeterminate] rather than to "not capturing" —
     * the scene is telling us it does not know, and resolving that to the reassuring answer is the
     * one inference this library must never make.
     *
     * Falls back to `isCaptured` below iOS 17, where no scene trait exists. Both carry the
     * cold-launch and Live Activity defects identically, so the fallback is a narrower reading, not
     * a less trustworthy one.
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
        /**
         * `sceneCaptureState` arrived in iOS 17; below that the trait collection does not carry it.
         *
         * Compared against the major version only. `NSProcessInfo`'s structured comparison takes a
         * `CValue<NSOperatingSystemVersion>`, which needs a memory scope to build — more machinery
         * than a single integer comparison warrants.
         */
        val supportsSceneCaptureState: Boolean
            get() = NSProcessInfo.processInfo.operatingSystemVersion.useContents { majorVersion >= 17 }
    }
}
