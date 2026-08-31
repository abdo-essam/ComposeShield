package io.github.composeshield

import io.github.composeshield.internal.ProtectionRegistry
import io.github.composeshield.internal.ProtectionOutcome
import io.github.composeshield.internal.ShieldLog
import io.github.composeshield.internal.WindowKey
import io.github.composeshield.internal.FakePlatformProtection
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ComposeShieldLoggerTest {
    @AfterTest
    fun resetLogger() {
        ComposeShield.logger = ComposeShieldLoggers.None
    }

    @Test
    fun `None logger discards messages without throwing`() {
        ComposeShield.logger = ComposeShieldLoggers.None
        ShieldLog.warn(message = "quiet")
    }

    @Test
    fun `filtering drops messages below minimum level`() {
        val entries = mutableListOf<ComposeShieldLogLevel>()
        ComposeShield.logger =
            ComposeShieldLoggers.filtering(ComposeShieldLogLevel.Warn, object : ComposeShieldLogger {
                override fun log(
                    level: ComposeShieldLogLevel,
                    tag: String,
                    message: String,
                    throwable: Throwable?,
                ) {
                    entries += level
                }
            })

        ShieldLog.debug(message = "debug")
        ShieldLog.warn(message = "warn")

        assertEquals(listOf(ComposeShieldLogLevel.Warn), entries)
    }

    @Test
    fun `protection failure is logged when a logger is installed`() {
        val entries = mutableListOf<String>()
        ComposeShield.logger =
            object : ComposeShieldLogger {
                override fun log(
                    level: ComposeShieldLogLevel,
                    tag: String,
                    message: String,
                    throwable: Throwable?,
                ) {
                    if (level == ComposeShieldLogLevel.Warn) {
                        entries += "$tag:$message"
                    }
                }
            }

        val registry =
            ProtectionRegistry(
                FakePlatformProtection().apply { nextOutcome = ProtectionOutcome.Failed },
            )
        registry.acquire(WindowKey("log-test"), setOf(Capability.ScreenshotPrevention))

        assertEquals(1, entries.size)
        assertTrue(entries.single().contains("ScreenshotPrevention"))
    }

    @Test
    fun `a throwing logger does not crash the library`() {
        ComposeShield.logger =
            object : ComposeShieldLogger {
                override fun log(
                    level: ComposeShieldLogLevel,
                    tag: String,
                    message: String,
                    throwable: Throwable?,
                ) {
                    error("sink bug")
                }
            }

        val registry =
            ProtectionRegistry(
                FakePlatformProtection().apply { nextOutcome = ProtectionOutcome.Failed },
            )
        val request = registry.acquire(WindowKey("throwing-sink"), setOf(Capability.ScreenshotPrevention))

        assertTrue(Capability.ScreenshotPrevention in registry.current.failedMechanisms)
        registry.release(request)
    }
}
