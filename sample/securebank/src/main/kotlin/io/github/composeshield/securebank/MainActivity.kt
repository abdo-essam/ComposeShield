package io.github.composeshield.securebank

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

/**
 * Host activity for SecureBank.
 *
 * Intent extras exist for deep-linking and for the instrumented tests:
 * - `start_route`: accounts | card | transactions | security (default: login)
 * - `demo_mode`:   "true" launches with protection disabled (manual negative control)
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycle.addObserver(BackgroundTracker)

        val startRoute = intent.getStringExtra(EXTRA_START_ROUTE)
        val demoMode = intent.getBooleanExtra(EXTRA_DEMO_MODE, false)

        setContent {
            SecureBankApp(
                startScreen = Screen.fromRouteName(startRoute),
                initialDemoMode = demoMode,
            )
        }
    }

    companion object {
        const val EXTRA_START_ROUTE = "start_route"
        const val EXTRA_DEMO_MODE = "demo_mode"
    }
}
