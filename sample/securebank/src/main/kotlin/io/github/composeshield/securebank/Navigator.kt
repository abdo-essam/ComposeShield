package io.github.composeshield.securebank

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList

/** Every destination in SecureBank. Sensitive ones are wrapped in a protection boundary. */
sealed interface Screen {
    data object Login : Screen

    /** Account overview — balances. */
    data object Accounts : Screen

    /** Virtual card — full number and CVV. */
    data object CardDetail : Screen

    data object Transactions : Screen

    /** Protection status, demo-mode switch, event log. */
    data object Security : Screen

    companion object {
        /** Deep-link/intent-extra parser; unknown names fall back to [Login]. */
        fun fromRouteName(name: String?): Screen =
            when (name) {
                "accounts" -> Accounts
                "card" -> CardDetail
                "transactions" -> Transactions
                "security" -> Security
                else -> Login
            }
    }
}

/**
 * Minimal navigation state: a linear stack observed by Compose.
 *
 * Deliberately framework-free (no navigation dependency) — the point of this app is exercising
 * ComposeShield on realistic screens, not showcasing a navigator.
 */
class BackStack(
    start: Screen,
) {
    private val stack: SnapshotStateList<Screen> = mutableStateListOf(start)

    val current: Screen
        get() = stack.last()

    val canPop: Boolean
        get() = stack.size > 1

    fun push(screen: Screen) {
        stack.add(screen)
    }

    fun pop() {
        if (canPop) stack.removeAt(stack.lastIndex)
    }

    /** Used by sign-out: clears everything above the root and replaces it. */
    fun resetTo(screen: Screen) {
        stack.clear()
        stack.add(screen)
    }
}
