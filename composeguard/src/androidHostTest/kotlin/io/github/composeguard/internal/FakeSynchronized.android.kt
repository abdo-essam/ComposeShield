package io.github.composeguard.internal

internal actual inline fun <R> fakeSynchronized(
    lock: Any,
    block: () -> R,
): R = kotlin.synchronized(lock, block)
