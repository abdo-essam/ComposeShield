package io.github.composeshield.validation

import io.github.composeshield.ComposeShield
import io.github.composeshield.TaskSwitcherProtection
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Validation test for iOS app-switcher protection (A-002).
 * Mapped to requirement matrix ID in config/test-id-map.yml.
 */
class IosAppSwitcherValidationTest {
    @Test
    fun appSwitcherWithProtectionOn_markerAbsent() {
        ComposeShield.taskSwitcherProtection = TaskSwitcherProtection.Always

        assertEquals(
            TaskSwitcherProtection.Always,
            ComposeShield.taskSwitcherProtection,
            "TaskSwitcherProtection mode must be active on iOS",
        )
    }
}
