package io.github.composeshield.internal

import android.os.Handler
import android.os.Looper

private val mainHandler = Handler(Looper.getMainLooper())

private val isOnMainThread: Boolean
    get() = Looper.myLooper() === Looper.getMainLooper()

/**
 * Runs [block] on the main thread, returning [ifDeferred] if it had to be posted.
 *
 * `Window.addFlags` routes to `ViewRootImpl.checkThread()`, which throws
 * `CalledFromWrongThreadException` off the main thread. The trap is that it throws only
 * *sometimes*: with no decor view attached yet the flags are merely stored and nothing complains.
 * That makes an off-main call an intermittent crash that passes testing and fails in the field, so
 * the "safe from any thread" guarantee has to be met by marshalling rather than by hoping.
 *
 * The return value is why this is not simply `post {}`. A caller on a background thread cannot be
 * given a synchronous answer without blocking it, and blocking a caller on the main thread is how
 * deadlocks are written. So the work is posted and reported as [ifDeferred] — which every caller
 * maps to [ProtectionOutcome.Deferred], meaning "requested, not yet confirmed". Reporting it as
 * failure would fire the failure posture on a call that is about to succeed a millisecond later.
 *
 * Calls already on the main thread — which is every call from composition, since `DisposableEffect`
 * runs on the applier thread — run inline and return the real result.
 */
internal inline fun <T> onMainThread(
    ifDeferred: T,
    crossinline block: () -> T,
): T {
    if (isOnMainThread) return block()
    mainHandler.post { block() }
    return ifDeferred
}
