package io.github.composeshield

import android.app.Activity
import io.github.composeshield.internal.AndroidPlatformProtection
import io.github.composeshield.internal.ProtectionRegistry
import io.github.composeshield.internal.SupportResolver
import io.github.composeshield.internal.registerWindow
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ScreenshotConflictTest {
    private val platform = AndroidPlatformProtection()
    private val registry = ProtectionRegistry(platform)
    private val resolver = SupportResolver(platform)

    @Test
    fun `screenshot events are supported on API 34 with no prevention active`() {
        assertEquals(
            SupportLevel.Supported,
            resolver.resolve(Capability.ScreenshotEvents, registry.current),
        )
    }

    @Test
    fun `active prevention precludes screenshot events`() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val window = registerWindow(activity.window, activity)

        registry.acquire(window, setOf(Capability.ScreenshotPrevention))

        assertEquals(
            SupportLevel.Unsupported(SupportLevel.Unsupported.Reason.PrecludedByActiveCapability),
            resolver.resolve(Capability.ScreenshotEvents, registry.current),
            "the OS stops invoking the capture callback once FLAG_SECURE is set; reporting Supported " +
                "here would promise events that never arrive",
        )
    }

    @Test
    fun `releasing prevention restores screenshot events`() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val window = registerWindow(activity.window, activity)

        val request = registry.acquire(window, setOf(Capability.ScreenshotPrevention))
        registry.release(request)

        assertEquals(
            SupportLevel.Supported,
            resolver.resolve(Capability.ScreenshotEvents, registry.current),
            "the preclusion is transient — it must lift with the prevention that caused it, which " +
                "is why support is resolved per query and never cached",
        )
    }

    @Test
    fun `recording prevention alone does not preclude screenshot events`() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val window = registerWindow(activity.window, activity)

        registry.acquire(window, setOf(Capability.RecordingPrevention))

        assertEquals(
            SupportLevel.Supported,
            resolver.resolve(Capability.ScreenshotEvents, registry.current),
        )
    }

    @Test
    @Config(sdk = [33])
    fun `below API 34 the version floor is reported rather than the conflict`() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val window = registerWindow(activity.window, activity)

        registry.acquire(window, setOf(Capability.ScreenshotPrevention))

        assertEquals(
            SupportLevel.Unsupported(SupportLevel.Unsupported.Reason.OsVersionTooLow),
            resolver.resolve(Capability.ScreenshotEvents, registry.current),
        )
    }
}
