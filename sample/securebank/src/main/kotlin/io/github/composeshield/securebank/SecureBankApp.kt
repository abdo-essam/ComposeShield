@file:Suppress("MagicNumber", "LongMethod", "TooManyFunctions")

package io.github.composeshield.securebank

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import io.github.composeshield.CaptureState
import io.github.composeshield.ComposeShield
import io.github.composeshield.SecureContent
import io.github.composeshield.securebank.components.BankTheme
import io.github.composeshield.securebank.components.Banner
import io.github.composeshield.securebank.components.LockOverlay
import io.github.composeshield.securebank.screens.AccountsScreen
import io.github.composeshield.securebank.screens.CardDetailScreen
import io.github.composeshield.securebank.screens.LoginScreen
import io.github.composeshield.securebank.screens.SecurityScreen
import io.github.composeshield.securebank.screens.TransactionsScreen
import kotlinx.coroutines.flow.StateFlow

/**
 * SecureBank root: navigation, the global protection posture, and the capture-reactive UI.
 *
 * Protection model ("completely block the content"):
 *  1. Every sensitive screen composes [SecureContent] — window-scoped FLAG_SECURE, so screenshots,
 *     screen recording AND the recents preview are blanked while any sensitive route is visible.
 *  2. [ComposeShield.taskSwitcherProtection] is Always, covering the gap between screens.
 *  3. While backgrounded/locked, a full-screen lock replaces the UI and an imperative
 *     [ComposeShield.protect] handle keeps the window secured even with no boundary composed.
 *  4. When the OS reports an active recording, balances mask themselves.
 *
 * Demo mode (Security screen) withdraws all of it for side-by-side manual comparison.
 */
@Composable
fun SecureBankApp(
    startScreen: Screen,
    initialDemoMode: Boolean,
) {
    var demoMode by remember { mutableStateOf(initialDemoMode) }
    val policy = remember(demoMode) { SecurityPolicy(demoMode) }
    val backStack = remember { BackStack(startScreen) }
    val log = remember { SecurityLog() }

    val captureState by ComposeShield.captureState.collectAsStateSafely()
    val recordingDetected = captureState == CaptureState.Active

    CollectSecurityEvents(log)

    // Global posture: switcher protection follows the policy immediately, boundary or not.
    DisposableEffect(policy) {
        ComposeShield.taskSwitcherProtection = policy.taskSwitcherMode
        onDispose { }
    }

    if (BackgroundTracker.isSessionLocked || BackgroundTracker.isBackgrounded) {
        // Imperative claim: keeps protection alive while no sensitive route is composed.
        if (policy.lockHoldsProtection) {
            DisposableEffect(Unit) {
                val handle = ComposeShield.protect()
                onDispose { handle.unprotect() }
            }
        }
        LockOverlay(onUnlock = { BackgroundTracker.unlockSession() })
        return
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(BankTheme.Backdrop),
    ) {
        if (recordingDetected) {
            Banner("Screen recording detected — sensitive values hidden", BankTheme.Danger)
        }

        BackHandler(enabled = backStack.canPop) { backStack.pop() }
        when (backStack.current) {
            Screen.Login -> {
                LoginScreen(
                    onSignedIn = {
                        backStack.resetTo(Screen.Accounts)
                        log.add("signed in")
                    },
                )
            }

            Screen.Accounts -> {
                ProtectedScreen(policy, log) {
                    AccountsScreen(
                        masked = recordingDetected,
                        onOpenCard = { backStack.push(Screen.CardDetail) },
                        onOpenTransactions = { backStack.push(Screen.Transactions) },
                        onOpenSecurity = { backStack.push(Screen.Security) },
                    )
                }
            }

            Screen.CardDetail -> {
                ProtectedScreen(policy, log) {
                    CardDetailScreen(masked = recordingDetected)
                }
            }

            Screen.Transactions -> {
                ProtectedScreen(policy, log) {
                    TransactionsScreen()
                }
            }

            Screen.Security -> {
                ProtectedScreen(policy, log) {
                    SecurityScreen(
                        demoMode = demoMode,
                        onDemoModeChange = {
                            demoMode = it
                            log.add(if (it) "DEMO MODE — protection disabled" else "protection restored")
                        },
                        logEntries = log.all,
                        captureStateLabel = describe(captureState),
                        onSignOut = {
                            backStack.resetTo(Screen.Login)
                            log.add("signed out")
                        },
                    )
                }
            }
        }
    }
}

/**
 * Wraps a sensitive route in the library's declarative boundary when the policy wants protection.
 *
 * [SecureContent] is intentionally composed/uncomposed per route: its claim lives exactly as long
 * as the sensitive screen does. Protection is window-scoped, so the whole screen is covered — not
 * only the content passed here.
 */
@Composable
private fun ProtectedScreen(
    policy: SecurityPolicy,
    log: SecurityLog,
    content: @Composable () -> Unit,
) {
    if (!policy.protectsScreens) {
        content()
        return
    }
    SecureContent(
        onProtectionFailure = { failed -> log.add("PROTECTION FAILED at boundary: $failed") },
    ) {
        content()
    }
}

/** Collects the library's event flows into the security log for the lifetime of the app. */
@Composable
private fun CollectSecurityEvents(log: SecurityLog) {
    LaunchedEffect(Unit) {
        ComposeShield.screenshotEvents.collect { log.add("screenshot taken") }
    }
    LaunchedEffect(Unit) {
        ComposeShield.protectionFailures.collect { failed -> log.add("PROTECTION FAILED: $failed") }
    }
}

private fun describe(state: CaptureState): String =
    when (state) {
        CaptureState.Active -> "Active (capture evidence)"
        CaptureState.Inactive -> "Inactive"
        CaptureState.Unknown -> "Unknown"
    }

/** Small helper mirroring the sample app's flow-to-state bridge. */
@Composable
private fun <T> StateFlow<T>.collectAsStateSafely(): State<T> =
    produceState(initialValue = value, this) {
        collect { value = it }
    }
