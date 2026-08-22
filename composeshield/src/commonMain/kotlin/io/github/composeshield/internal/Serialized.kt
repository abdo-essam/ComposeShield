package io.github.composeshield.internal

/**
 * Runs [block] while holding the process-wide reconciliation lock.
 *
 * Expect/actual rather than `kotlin.synchronized` because Native has no per-object monitor. The
 * [lock] parameter keeps the signature symmetric with the JVM actual; on Native, serialization is
 * process-wide, which is irrelevant at this call site's frequency (navigation-rate events, with
 * platform effects that are posted or quick flag sets).
 *
 * Why reconcile needs mutual exclusion at all: the decide → platform-call → record sequence spans
 * three steps, and two threads reconciling the same window concurrently can otherwise interleave so
 * that one skips a needed apply on the strength of an `applied` entry the other is about to
 * invalidate — leaving an active request unprotected with nothing left to re-trigger the reconcile.
 * Holding the lock across the platform call revisits the class KDoc's older "no lock across the
 * platform call" note deliberately: that note guards the CAS retry loop against *re-executing*
 * platform calls under contention; a serialized reconcile executes each platform call exactly once,
 * so there is nothing to re-execute.
 */
internal expect inline fun <R> serialized(
    lock: Any,
    block: () -> R,
): R
