package io.github.composeshield.internal

import android.os.Handler
import android.os.Looper

private val mainHandler = Handler(Looper.getMainLooper())

private val isOnMainThread: Boolean
    get() = Looper.myLooper() === Looper.getMainLooper()

internal inline fun <T> onMainThread(
    ifDeferred: T,
    crossinline block: () -> T,
): T {
    if (isOnMainThread) return block()
    mainHandler.post { block() }
    return ifDeferred
}
