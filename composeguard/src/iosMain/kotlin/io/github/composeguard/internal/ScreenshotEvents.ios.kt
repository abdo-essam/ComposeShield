package io.github.composeguard.internal

import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIApplicationUserDidTakeScreenshotNotification

/**
 * Emits once per screenshot, via `userDidTakeScreenshotNotification`.
 *
 * Supported on every iOS version this library targets, and — unlike Android — it keeps working
 * while prevention is active, which is why `preventionPrecludesScreenshotEvents` is `false` here.
 *
 * Strictly after the fact: the notification arrives once the screenshot has already been written,
 * so it cannot prevent the capture that triggered it. It carries no payload and the library
 * adds none, since anything attached would risk conveying the content being protected.
 */
internal class ScreenshotEvents {
    fun events(): Flow<Unit> =
        callbackFlow {
            val observer =
                NSNotificationCenter.defaultCenter.addObserverForName(
                    name = UIApplicationUserDidTakeScreenshotNotification,
                    `object` = null,
                    queue = NSOperationQueue.mainQueue,
                ) { _ ->
                    trySend(Unit)
                }

            awaitClose { NSNotificationCenter.defaultCenter.removeObserver(observer) }
        }
}
