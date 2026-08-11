package io.github.composeguard

import kotlin.test.Test

/**
 * Contract test C10 — no public operation throws, on any platform, in any support state.
 *
 * FR-021 is unusually strict for a reason: this library is wrapped around the most sensitive screens
 * in an application, and it runs on devices whose OS version and vendor behaviour it cannot predict.
 * A capability that threw on an unsupported device would crash the host app precisely where the
 * stakes are highest. Unsupported is reported through [SupportLevel]; it is never raised.
 *
 * Every public entry point is exercised here against the real platform for whichever target this
 * runs on — the iOS simulator reports prevention as `RequiresOptIn` and Android host tests report
 * per-API-level support, so between the two targets both the supported and unsupported paths are
 * covered without either needing a stub.
 *
 * No assertions: the contract is "does not throw", so completing the test body *is* the assertion.
 */
class NoThrowContractTest {
    @Test
    fun `every query operation completes without throwing`() {
        Capability.entries.forEach { ComposeGuard.supportLevel(it) }
        ComposeGuard.isProtectionActive()
        ComposeGuard.grantedOptIns()
        ComposeGuard.captureState.value
        ComposeGuard.screenshotEvents
        ComposeGuard.protectionFailures
    }

    @Test
    fun `acquire and release complete without throwing`() {
        val handle = ComposeGuard.acquire()
        handle.release()
        // Idempotent by contract, and a double release must not throw either.
        handle.release()
    }

    @Test
    fun `acquiring an unsupported capability does not throw`() {
        // Detection capabilities have no prevention mechanism to apply; requesting them as though
        // they did must degrade quietly rather than fail.
        ComposeGuard.acquire(setOf(Capability.CaptureDetection, Capability.ScreenshotEvents)).release()
    }

    @Test
    fun `setting every app-switcher mode completes without throwing`() {
        AppSwitcherProtection.entries.forEach { ComposeGuard.appSwitcherProtection = it }
        ComposeGuard.appSwitcherProtection = AppSwitcherProtection.Automatic
    }

    @Test
    fun `opting in does not throw - including on the paths that are deliberately no-ops`() {
        Capability.entries.forEach { capability ->
            ComposeGuard.optInToUnsanctionedCapability(
                capability = capability,
                failurePosture = FailurePosture.FailOpen,
                acknowledgement = UnsanctionedMechanismAcknowledgement(capability, acceptedPolicyRisk = true),
            )
        }
    }

    @Test
    fun `a mismatched or unaccepted acknowledgement is ignored rather than throwing`() {
        ComposeGuard.optInToUnsanctionedCapability(
            capability = Capability.ScreenshotPrevention,
            failurePosture = FailurePosture.FailClosed,
            acknowledgement =
                UnsanctionedMechanismAcknowledgement(
                    // Names a different capability than the one being enabled.
                    capability = Capability.CaptureDetection,
                    acceptedPolicyRisk = true,
                ),
        )

        ComposeGuard.optInToUnsanctionedCapability(
            capability = Capability.ScreenshotPrevention,
            failurePosture = FailurePosture.FailClosed,
            acknowledgement =
                UnsanctionedMechanismAcknowledgement(
                    capability = Capability.ScreenshotPrevention,
                    acceptedPolicyRisk = false,
                ),
        )
    }
}
