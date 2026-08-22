package io.github.composeshield.internal

import platform.Foundation.NSLock

/**
 * The JVM `synchronized` actual, approximated on Native with a process-wide [NSLock].
 *
 * Kotlin/Native has no per-object monitor, so the [lock] parameter exists only to keep the
 * expect/actual signature identical to the JVM side. Serialization here is process-wide rather
 * than per-lock-object; the fakes guard small bookkeeping lists, so the coarse grain is irrelevant
 * to the tests that use them, and it matters that concurrent mutations are genuinely serialized —
 * an earlier version of this actual was `= block()`, which left the fake's state unsynchronized on
 * exactly the tests meant to prove thread safety.
 */
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
