package io.github.composeshield.internal

import io.github.composeshield.ComposeShieldLogLevel
import io.github.composeshield.ComposeShieldLogger
import io.github.composeshield.ComposeShieldLoggers
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/**
 * Process-wide logger holder and internal call sites.
 *
 * Reads are lock-free so hot paths can log without synchronizing; assignment is atomic so a
 * logger installed during startup is visible to every thread immediately.
 */
@OptIn(ExperimentalAtomicApi::class)
internal object ShieldLog {
    private const val DEFAULT_TAG = "ComposeShield"

    private val holder = AtomicReference(ComposeShieldLoggers.None)

    var logger: ComposeShieldLogger
        get() = holder.load()
        set(value) {
            holder.store(value)
        }

    fun debug(
        tag: String = DEFAULT_TAG,
        message: String,
    ) {
        emit(ComposeShieldLogLevel.Debug, tag, message)
    }

    fun warn(
        tag: String = DEFAULT_TAG,
        message: String,
        throwable: Throwable? = null,
    ) {
        emit(ComposeShieldLogLevel.Warn, tag, message, throwable)
    }

    fun error(
        tag: String = DEFAULT_TAG,
        message: String,
        throwable: Throwable? = null,
    ) {
        emit(ComposeShieldLogLevel.Error, tag, message, throwable)
    }

    private fun emit(
        level: ComposeShieldLogLevel,
        tag: String,
        message: String,
        throwable: Throwable? = null,
    ) {
        runCatching { holder.load().log(level, tag, message, throwable) }
    }
}
