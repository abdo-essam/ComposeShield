package io.github.composeshield.internal

import platform.Foundation.NSRecursiveLock

/**
 * The Native actual of [serialized]: a process-wide [NSRecursiveLock] — see the expect KDoc.
 *
 * Recursive rather than [platform.Foundation.NSLock] because consumer callbacks run inside the
 * guarded region (`recordFailure` reports failures mid-reconcile), and a callback that re-enters
 * the registry must not deadlock the thread holding the lock.
 */
private val reconcileNativeLock = NSRecursiveLock()

internal actual inline fun <R> serialized(
    lock: Any,
    block: () -> R,
): R {
    reconcileNativeLock.lock()
    try {
        return block()
    } finally {
        reconcileNativeLock.unlock()
    }
}
