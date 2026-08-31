package io.github.composeshield.internal

import platform.Foundation.NSRecursiveLock

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
