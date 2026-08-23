package io.github.composeshield.securebank

import io.github.composeshield.TaskSwitcherProtection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure posture logic of [SecurityPolicy] — the manual negative control must flip everything. */
class SecurityPolicyTest {
    @Test
    fun protectedByDefault() {
        val policy = SecurityPolicy(demoModeEnabled = false)
        assertTrue(policy.protectsScreens)
        assertTrue(policy.lockHoldsProtection)
    }

    @Test
    fun demoModeWithdrawsEverything() {
        val policy = SecurityPolicy(demoModeEnabled = true)
        assertFalse(policy.protectsScreens)
        assertFalse(policy.lockHoldsProtection)
    }

    @Test
    fun switcherModeFollowsDemoMode() {
        assertEquals(TaskSwitcherProtection.Always, SecurityPolicy(demoModeEnabled = false).taskSwitcherMode)
        assertEquals(TaskSwitcherProtection.Disabled, SecurityPolicy(demoModeEnabled = true).taskSwitcherMode)
    }
}

/** Back-stack behaviour backing the app's navigation. */
class BackStackTest {
    @Test
    fun startsAtGivenScreen() {
        val stack = BackStack(Screen.Login)
        assertEquals(Screen.Login, stack.current)
        assertFalse(stack.canPop)
    }

    @Test
    fun pushAndPop() {
        val stack = BackStack(Screen.Login)
        stack.push(Screen.Accounts)
        assertTrue(stack.canPop)
        assertEquals(Screen.Accounts, stack.current)
        stack.pop()
        assertEquals(Screen.Login, stack.current)
        assertFalse(stack.canPop)
    }

    @Test
    fun popOnRootIsNoOp() {
        val stack = BackStack(Screen.Login)
        stack.pop()
        assertEquals(Screen.Login, stack.current)
    }

    @Test
    fun resetToReplacesWholeStack() {
        val stack = BackStack(Screen.Login)
        stack.push(Screen.Accounts)
        stack.push(Screen.CardDetail)
        stack.resetTo(Screen.Accounts)
        assertEquals(Screen.Accounts, stack.current)
        assertFalse(stack.canPop)
    }

    @Test
    fun routeParsing() {
        assertEquals(Screen.Accounts, Screen.fromRouteName("accounts"))
        assertEquals(Screen.CardDetail, Screen.fromRouteName("card"))
        assertEquals(Screen.Transactions, Screen.fromRouteName("transactions"))
        assertEquals(Screen.Security, Screen.fromRouteName("security"))
        assertEquals(Screen.Login, Screen.fromRouteName(null))
        assertEquals(Screen.Login, Screen.fromRouteName("bogus"))
    }
}
