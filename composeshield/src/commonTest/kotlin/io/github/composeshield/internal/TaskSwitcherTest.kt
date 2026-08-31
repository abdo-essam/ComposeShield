package io.github.composeshield.internal

import io.github.composeshield.Capability
import io.github.composeshield.TaskSwitcherProtection
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** App-switcher protection and the no-double-application rule. */
class TaskSwitcherTest {
    private val platform = FakePlatformProtection()
    private val registry = ProtectionRegistry(platform)
    private val window = WindowKey("test-window")

    @Test
    fun `Automatic follows outstanding protection requests`() {
        assertFalse(registry.current.shouldProtectTaskSwitcher())

        val request = registry.acquire(window, setOf(Capability.CaptureDetection))
        assertTrue(registry.current.shouldProtectTaskSwitcher())

        registry.release(request)
        assertFalse(registry.current.shouldProtectTaskSwitcher())
    }

    @Test
    fun `Always protects the switcher with no boundary composed at all`() {
        registry.setTaskSwitcherMode(TaskSwitcherProtection.Always)

        assertTrue(registry.current.shouldProtectTaskSwitcher())
        assertContains(platform.appSwitcherProtectedWindows, WindowKey.Unbound)
    }

    @Test
    fun `Disabled overrides the default while leaving prevention active`() {
        registry.setTaskSwitcherMode(TaskSwitcherProtection.Disabled)
        registry.acquire(window, setOf(Capability.ScreenshotPrevention))

        assertFalse(registry.current.shouldProtectTaskSwitcher())
        assertContains(
            platform.protectedWindows,
            window,
            "Disabling switcher protection must leave capture prevention fully active",
        )
    }

    @Test
    fun `the recents primitive is suppressed where prevention already covers it`() {
        registry.acquire(window, setOf(Capability.ScreenshotPrevention))

        assertFalse(
            window in platform.appSwitcherProtectedWindows,
            "FLAG_SECURE obscures recents inseparably; applying the recents-only primitive on top " +
                "is redundant work with a visible artifact to show for it",
        )
    }

    @Test
    fun `the recents primitive is applied where prevention does not cover it`() {
        registry.acquire(window, setOf(Capability.CaptureDetection))

        assertContains(platform.appSwitcherProtectedWindows, window)
    }
}
