package io.github.composeshield

import kotlin.test.Test

/**
 * Asserts that no public operation throws exceptions, on any platform, in any support state.
 *
 * This library is wrapped around sensitive screens in an application, and it runs on devices
 * whose OS version and vendor behaviour vary widely. Unsupported capabilities and failures are
 * reported through [SupportLevel] and flows, never as uncaught exceptions.
 *
 * No assertions: the contract is "does not throw", so completing the test body *is* the assertion.
 */
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
        // Idempotent by contract, and a double unprotect must not throw either.
        handle.unprotect()

        // Also test direct singleton calls
        ComposeShield.protect()
        ComposeShield.unprotect()
        ComposeShield.unprotect()
    }

    @Test
    fun `protecting an unsupported capability does not throw`() {
        // Detection capabilities have no prevention mechanism to apply; requesting them as though
        // they did must degrade quietly rather than fail.
        ComposeShield.protect(setOf(Capability.CaptureDetection, Capability.ScreenshotEvents)).unprotect()
    }

    @Test
    fun `setting every app-switcher mode completes without throwing`() {
        TaskSwitcherProtection.entries.forEach { ComposeShield.taskSwitcherProtection = it }
        ComposeShield.taskSwitcherProtection = TaskSwitcherProtection.Automatic
    }
}
