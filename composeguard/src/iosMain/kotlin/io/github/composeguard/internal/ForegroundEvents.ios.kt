package io.github.composeguard.internal

import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIApplicationDidBecomeActiveNotification

/**
 * Emits each time the application becomes active.
 *
 * `didBecomeActive` rather than `willEnterForeground`: the re-poll it drives reads
 * `UIScreen.captured`, and that read is only trustworthy once the app is actually active. Reading it
 * mid-transition risks the same stale answer as the cold-launch defect the re-poll exists to correct
 * (research.md R3).
 *
 * Also fires after a transient interruption — a notification banner, the control centre — where no
 * capture state changed. That is the right trade: the re-poll is cheap and idempotent, whereas a
 * missed foreground leaves a stale reading standing for the rest of the session.
 */
internal class ForegroundEvents {
    fun events(): Flow<Unit> =
        callbackFlow {
            val observer =
                NSNotificationCenter.defaultCenter.addObserverForName(
                    name = UIApplicationDidBecomeActiveNotification,
                    `object` = null,
                    queue = NSOperationQueue.mainQueue,
                ) { _ ->
                    trySend(Unit)
                }

            awaitClose { NSNotificationCenter.defaultCenter.removeObserver(observer) }
        }
}
