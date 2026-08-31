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

/**
 * The Android prevention/detection conflict against the platform implementation.
 *
 * [io.github.composeshield.internal.ScreenshotEventsTest] proves the resolver applies the rule when a
 * platform reports the exclusion. This proves the *Android* platform reports it, and that the
 * resulting support level moves with the live registry rather than being fixed at startup.
 *
 * The conflict is real platform behaviour, not a library choice: AOSP `Activity.java` documents that
 * the capture callback "is not invoked if the activity window has FLAG_SECURE set". Prevention wins,
 * and the superseded capability must say so — a consumer left collecting `screenshotEvents` that will
 * never arrive has no way to tell that from "no screenshots were taken".
 */
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
