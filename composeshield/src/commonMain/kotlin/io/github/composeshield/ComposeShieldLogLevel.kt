package io.github.composeshield

/**
 * Severity of a [ComposeShieldLogger] message.
 *
 * Ordered from least to most severe so [ComposeShieldLoggers.filtering] can compare thresholds.
 */
public enum class ComposeShieldLogLevel {
    Debug,
    Info,
    Warn,
    Error,
}
