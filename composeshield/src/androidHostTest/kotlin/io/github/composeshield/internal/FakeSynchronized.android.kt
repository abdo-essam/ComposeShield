package io.github.composeshield.internal

internal actual inline fun <R> fakeSynchronized(
    lock: Any,
    block: () -> R,
): R = kotlin.synchronized(lock, block)
