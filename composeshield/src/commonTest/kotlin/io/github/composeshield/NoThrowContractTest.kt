package io.github.composeshield

import kotlin.test.Test

class NoThrowContractTest {
    @Test
    fun `every query operation completes without throwing`() {
        Capability.entries.forEach { ComposeShield.supportLevel(it) }
        ComposeShield.isProtectionActive()
        ComposeShield.captureState.value
        ComposeShield.screenshotEvents
        ComposeShield.protectionFailures
    }

    @Test
    fun `protect and unprotect complete without throwing`() {
        val handle = ComposeShield.protect()
        handle.unprotect()
        handle.unprotect()

        ComposeShield.protect()
        ComposeShield.unprotect()
        ComposeShield.unprotect()
    }

    @Test
    fun `protecting an unsupported capability does not throw`() {
        ComposeShield.protect(setOf(Capability.CaptureDetection, Capability.ScreenshotEvents)).unprotect()
    }

    @Test
    fun `setting every app-switcher mode completes without throwing`() {
        TaskSwitcherProtection.entries.forEach { ComposeShield.taskSwitcherProtection = it }
        ComposeShield.taskSwitcherProtection = TaskSwitcherProtection.Automatic
    }
}
