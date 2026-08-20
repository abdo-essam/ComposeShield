package io.github.composeshield.internal

import io.github.composeshield.TaskSwitcherProtection
import io.github.composeshield.Capability
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** US4 — app-switcher protection, including the no-double-application rule (FR-015d). */
class TaskSwitcherTest {
    private val platform = FakePlatformProtection()
    private val registry = ProtectionRegistry(platform)
    private val window = WindowKey("test-window")

    @Test
    fun `Automatic follows outstanding protection requests`() {
        // FR-015a: the common case needs no second opt-in, because an app protecting a screen
        // almost always wants that screen absent from the switcher too.
        assertFalse(registry.current.shouldProtectTaskSwitcher())

        val request = registry.acquire(window, setOf(Capability.CaptureDetection))
        assertTrue(registry.current.shouldProtectTaskSwitcher())

        registry.release(request)
        assertFalse(registry.current.shouldProtectTaskSwitcher())
    }

    @Test
    fun `Always protects the switcher with no boundary composed at all`() {
        // FR-015c: usable purely against a shoulder-surfer in the task list, with no capture
        // prevention involved.
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
            "FR-015b disables only the switcher — capture prevention must stay fully active",
        )
    }

    @Test
    fun `FR-015d - the recents primitive is suppressed where prevention already covers it`() {
        registry.acquire(window, setOf(Capability.ScreenshotPrevention))

        assertFalse(
            window in platform.appSwitcherProtectedWindows,
            "FLAG_SECURE obscures recents inseparably; applying the recents-only primitive on top " +
                "is redundant work with a visible artifact to show for it",
        )
    }

    @Test
    fun `the recents primitive is applied where prevention does not cover it`() {
        // Detection-only protection does not obscure recents, so the standalone primitive is needed.
        registry.acquire(window, setOf(Capability.CaptureDetection))

        assertContains(platform.appSwitcherProtectedWindows, window)
    }
}
