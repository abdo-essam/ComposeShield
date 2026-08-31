package io.github.composeshield.internal

internal expect inline fun <R> serialized(
    lock: Any,
    block: () -> R,
): R
