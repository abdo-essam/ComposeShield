package io.github.composeshield.internal

import io.github.composeshield.Capability
import io.github.composeshield.SupportLevel
import io.github.composeshield.SupportLevel.Unsupported.Reason
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Contract tests C5 and C6 — query-time support resolution.
 *
 * The property under test is that support is *re-derived on every call*. A matrix resolved once at
 * startup would keep reporting `Supported` for a capability that had since been precluded or had
 * silently broken, which is the precise failure the library exists to prevent.
 */
class SupportResolverTest {
    private val window = WindowKey("test-window")

    @Test
    fun `C6 - activating prevention precludes screenshot events and releasing restores them`() {
        val platform = FakePlatformProtection(preventionPrecludesScreenshotEvents = true)
        val registry = ProtectionRegistry(platform)
        val resolver = SupportResolver(platform)

        assertEquals(
            SupportLevel.Supported,
            resolver.resolve(Capability.ScreenshotEvents, registry.current),
        )

        val request = registry.acquire(window, setOf(Capability.ScreenshotPrevention))

        assertEquals(
            SupportLevel.Unsupported(Reason.PrecludedByActiveCapability),
            resolver.resolve(Capability.ScreenshotEvents, registry.current),
            "Android does not invoke the capture callback on a FLAG_SECURE window — reporting " +
                "Supported here would promise events that never arrive",
        )

        registry.release(request)

        assertEquals(
            SupportLevel.Supported,
            resolver.resolve(Capability.ScreenshotEvents, registry.current),
            "preclusion is transient and must correct itself, unlike an OS-version limit",
        )
    }

    @Test
    fun `preclusion does not apply on a platform that does not exclude the two`() {
        val platform = FakePlatformProtection(preventionPrecludesScreenshotEvents = false)
        val registry = ProtectionRegistry(platform)
        val resolver = SupportResolver(platform)

        registry.acquire(window, setOf(Capability.ScreenshotPrevention))

        assertEquals(
            SupportLevel.Supported,
            resolver.resolve(Capability.ScreenshotEvents, registry.current),
            "iOS fires the screenshot notification regardless of what the window is doing",
        )
    }

    @Test
    fun `an unsupported capability is not relabelled as precluded`() {
        // Reporting "precluded" for something the OS cannot do anyway would imply that releasing
        // prevention brings it back, sending a consumer down a dead end.
        val platform =
            FakePlatformProtection(
                support = mapOf(Capability.ScreenshotEvents to SupportLevel.Unsupported(Reason.OsVersionTooLow)),
            )
        val registry = ProtectionRegistry(platform)
        val resolver = SupportResolver(platform)

        registry.acquire(window, setOf(Capability.ScreenshotPrevention))

        assertEquals(
            SupportLevel.Unsupported(Reason.OsVersionTooLow),
            resolver.resolve(Capability.ScreenshotEvents, registry.current),
        )
    }

    @Test
    fun `a failed mechanism outranks every other verdict`() {
        // Order matters: a broken mechanism is unusable whatever else is true of it, and reporting
        // it as merely "precluded" would suggest it returns when prevention releases.
        val platform = FakePlatformProtection().apply { nextOutcome = ProtectionOutcome.Failed }
        val registry = ProtectionRegistry(platform)
        val resolver = SupportResolver(platform)

        registry.acquire(window, setOf(Capability.ScreenshotPrevention))

        assertEquals(
            SupportLevel.Unsupported(Reason.MechanismUnavailable),
            resolver.resolve(Capability.ScreenshotPrevention, registry.current),
            "FR-022: a mechanism that did not install must never be reported as protection",
        )
    }
}
