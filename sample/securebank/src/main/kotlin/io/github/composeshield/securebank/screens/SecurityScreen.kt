@file:Suppress("MagicNumber", "LongMethod", "TooManyFunctions")

package io.github.composeshield.securebank.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.composeshield.Capability
import io.github.composeshield.ComposeShield
import io.github.composeshield.SupportLevel
import io.github.composeshield.securebank.components.BankTheme
import io.github.composeshield.securebank.components.Card
import io.github.composeshield.securebank.components.Heading
import io.github.composeshield.securebank.components.Mono
import io.github.composeshield.securebank.components.Note
import io.github.composeshield.securebank.components.PrimaryButton
import io.github.composeshield.securebank.components.Readout
import io.github.composeshield.securebank.components.ToggleRow

/**
 * Security status + demo-mode control.
 *
 * The demo-mode switch is the manual negative control: flip it off and screenshots/recording/
 * recents previews immediately start succeeding — the contrast makes the protection observable.
 */
@Composable
internal fun SecurityScreen(
    demoMode: Boolean,
    onDemoModeChange: (Boolean) -> Unit,
    logEntries: List<String>,
    captureStateLabel: String,
    onSignOut: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Heading("Security")

        Card(title = "Live status") {
            Readout(
                label = "Protection active",
                value = ComposeShield.isProtectionActive().toString(),
                valueColor = if (ComposeShield.isProtectionActive()) BankTheme.Accent else BankTheme.Danger,
            )
            Readout("Capture state", captureStateLabel)
            Note("Support is re-evaluated on every recomposition; prevention changes it dynamically.")
        }

        Card(title = "Capability support") {
            Capability.entries.forEach { capability ->
                Readout(capability.name, describe(ComposeShield.supportLevel(capability)))
            }
        }

        Card(title = "Demo mode") {
            ToggleRow(label = "Disable ALL protection (negative control)", on = demoMode, onToggle = onDemoModeChange)
            Note(
                "ON = screenshots, recording and the task-switcher preview all show real content. " +
                    "OFF = everything is blocked. Compare captures in both states.",
            )
        }

        Card(title = "Event log") {
            if (logEntries.isEmpty()) {
                Note("Nothing yet. Take a screenshot or toggle something.")
            } else {
                logEntries.forEach { entry -> Mono(entry) }
            }
        }

        PrimaryButton(text = "Sign out") { onSignOut() }
    }
}

private fun describe(level: SupportLevel): String =
    when (level) {
        is SupportLevel.Supported -> "Supported"
        is SupportLevel.Unsupported -> "Unsupported(${level.reason.name})"
    }
