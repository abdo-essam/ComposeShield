package io.github.composeshield.securebank

import io.github.composeshield.TaskSwitcherProtection

/**
 * The bank's protection posture, derived from a single switch.
 *
 * Pure logic — no Android imports — so the host unit tests can assert the exact posture each
 * demo-mode value produces without a device or Robolectric.
 *
 * @param demoModeEnabled when true, every protection request is withdrawn. This is the manual
 *   negative control: captures succeed and the recents preview shows real content, so the
 *   difference between protected and unprotected states can be seen live.
 */
data class SecurityPolicy(
    val demoModeEnabled: Boolean,
) {
    /** Sensitive routes request full prevention while this is true. */
    val protectsScreens: Boolean
        get() = !demoModeEnabled

    /** The lock overlay still appears when backgrounded — but holds no protection claim. */
    val lockHoldsProtection: Boolean
        get() = !demoModeEnabled

    /**
     * [TaskSwitcherProtection.Always] blanks the task-switcher preview unconditionally, which is
     * what a bank wants between screens; demo mode hands the preview back.
     */
    val taskSwitcherMode: TaskSwitcherProtection
        get() = if (demoModeEnabled) TaskSwitcherProtection.Disabled else TaskSwitcherProtection.Always
}
