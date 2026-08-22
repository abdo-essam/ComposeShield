package io.github.composeshield.internal

internal actual inline fun <R> serialized(
    lock: Any,
    block: () -> R,
): R = kotlin.synchronized(lock, block)
