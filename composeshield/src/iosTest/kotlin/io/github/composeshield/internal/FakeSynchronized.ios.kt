package io.github.composeshield.internal

import platform.Foundation.NSLock

private val nativeFakeLock = NSLock()

internal actual inline fun <R> fakeSynchronized(
    lock: Any,
    block: () -> R,
): R {
    nativeFakeLock.lock()
    try {
        return block()
    } finally {
        nativeFakeLock.unlock()
    }
}
