package io.github.composeshield

/**
 * Receives diagnostic output from ComposeShield.
 *
 * Install one early in process startup — for example in `Application.onCreate` — so warnings and
 * errors emitted during library initialization reach your sink. The default is [ComposeShieldLoggers.None],
 * which keeps production silent; security-relevant failures are still delivered through
 * [ComposeShield.protectionFailures] and [SecureContent]'s `onProtectionFailure`.
 */
public interface ComposeShieldLogger {
    /**
     * @param level severity of the message.
     * @param tag a short, filterable identifier (typically `"ComposeShield"` or a sub-component).
     * @param message human-readable detail. Must not contain protected screen content.
     * @param throwable optional cause, when the message describes a failure.
     */
    public fun log(
        level: ComposeShieldLogLevel,
        tag: String,
        message: String,
        throwable: Throwable? = null,
    )
}

/** Built-in [ComposeShieldLogger] implementations and helpers. */
public object ComposeShieldLoggers {
    /** Discards all messages. The default until [ComposeShield.logger] is assigned. */
    public val None: ComposeShieldLogger =
        object : ComposeShieldLogger {
            override fun log(
                level: ComposeShieldLogLevel,
                tag: String,
                message: String,
                throwable: Throwable?,
            ) = Unit
        }

    /**
     * Forwards only messages at or above [minimumLevel] to [delegate].
     *
     * Use this to keep debug noise out of production while still wiring a single sink:
     * ```
     * ComposeShield.logger = ComposeShieldLoggers.filtering(ComposeShieldLogLevel.Warn, mySink)
     * ```
     */
    public fun filtering(
        minimumLevel: ComposeShieldLogLevel,
        delegate: ComposeShieldLogger,
    ): ComposeShieldLogger =
        object : ComposeShieldLogger {
            override fun log(
                level: ComposeShieldLogLevel,
                tag: String,
                message: String,
                throwable: Throwable?,
            ) {
                if (level.ordinal >= minimumLevel.ordinal) {
                    delegate.log(level, tag, message, throwable)
                }
            }
        }
}
