package io.github.composeshield

import platform.Foundation.NSLog

/**
 * A [ComposeShieldLogger] backed by [NSLog], filtered from [minimumLevel] upward.
 *
 * ```
 * ComposeShield.logger = ComposeShieldLoggers.osLog(ComposeShieldLogLevel.Warn)
 * ```
 */
public fun ComposeShieldLoggers.osLog(
    minimumLevel: ComposeShieldLogLevel = ComposeShieldLogLevel.Warn,
): ComposeShieldLogger =
    filtering(
        minimumLevel,
        object : ComposeShieldLogger {
            override fun log(
                level: ComposeShieldLogLevel,
                tag: String,
                message: String,
                throwable: Throwable?,
            ) {
                val formatted =
                    if (throwable != null) {
                        "[$level] $tag: $message\n$throwable"
                    } else {
                        "[$level] $tag: $message"
                    }
                NSLog("%@", formatted)
            }
        },
    )
