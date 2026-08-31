package io.github.composeshield.internal

import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIApplicationUserDidTakeScreenshotNotification

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
