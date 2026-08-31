package io.github.composeshield.validation

import io.github.composeshield.ComposeShield
import io.github.composeshield.TaskSwitcherProtection
import kotlin.test.Test
import kotlin.test.assertEquals

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
