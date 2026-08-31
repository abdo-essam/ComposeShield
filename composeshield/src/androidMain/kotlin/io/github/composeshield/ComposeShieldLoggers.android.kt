package io.github.composeshield

import android.util.Log

/**
 * A [ComposeShieldLogger] backed by [Log], filtered from [minimumLevel] upward.
 *
 * ```
 * ComposeShield.logger = ComposeShieldLoggers.androidLogcat(ComposeShieldLogLevel.Warn)
 * ```
 */
public fun ComposeShieldLoggers.androidLogcat(
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
                val priority =
                    when (level) {
                        ComposeShieldLogLevel.Debug -> Log.DEBUG
                        ComposeShieldLogLevel.Info -> Log.INFO
                        ComposeShieldLogLevel.Warn -> Log.WARN
                        ComposeShieldLogLevel.Error -> Log.ERROR
                    }
                if (throwable != null) {
                    Log.println(priority, tag, "$message\n${Log.getStackTraceString(throwable)}")
                } else {
                    Log.println(priority, tag, message)
                }
            }
        },
    )
