package io.github.composeshield.sample

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.composeshield.Capability
import io.github.composeshield.ComposeShield
import io.github.composeshield.ProtectionHandle
import io.github.composeshield.SecureContent
import io.github.composeshield.TaskSwitcherProtection

/**
 * Demonstrates all five capabilities against a visible marker.
 *
 * The marker is the point: screenshot the app with protection on and it should be absent, then
 * again with protection off and it should be present. Everything else on screen — the live support
 * readout and the event log — exists to explain *why* a given attempt behaved the way it did.
 *
 * Automated tests assert the library *requested* protection. Only a device screenshot confirms the
 * OS *honoured* it.
 */
@Composable
fun SampleApp() {
    val log = remember { EventLog() }

    var boundaryActive by remember { mutableStateOf(false) }
    var imperativeHandle by remember { mutableStateOf<ProtectionHandle?>(null) }
    var switcherMode by remember { mutableStateOf(ComposeShield.taskSwitcherProtection) }

    val captureState by ComposeShield.captureState.collectAsStateSafely()

    LaunchedEffect(Unit) {
        ComposeShield.screenshotEvents.collect { log.add("screenshot taken") }
    }
    LaunchedEffect(Unit) {
        ComposeShield.protectionFailures.collect { log.add("PROTECTION FAILED: $it") }
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color(0xFFF7F7F9))
                .systemBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Heading("ComposeShield sample")

        // The marker is window-scoped: while SecureContent is composed, the whole screen is
        // protected — not just the marker region.
        if (boundaryActive) {
            SecureContent(
                onProtectionFailure = { log.add("boundary reported failure: $it") },
            ) { SecretMarker() }
        } else {
            SecretMarker()
        }

        Section("Prevention") {
            Toggle(
                label = "Declarative boundary (SecureContent)",
                on = boundaryActive,
                onToggle = {
                    boundaryActive = it
                    log.add(if (it) "boundary entered composition" else "boundary left composition")
                },
            )
            Toggle(
                label = "Imperative claim (ComposeShield.protect)",
                on = imperativeHandle != null,
                onToggle = { wanted ->
                    if (wanted) {
                        imperativeHandle = ComposeShield.protect()
                        log.add("imperative claim protected")
                    } else {
                        imperativeHandle?.unprotect()
                        imperativeHandle = null
                        log.add("imperative claim unprotected")
                    }
                },
            )
            Note(
                "Both claims are reference-counted together: protection is withdrawn only when the " +
                    "last one is gone. Turn both on, unprotect one, and the marker stays protected.",
            )
            Note(
                "protect() records a protection claim — it does not guarantee the OS has applied " +
                    "it. Automated tests verify the library requested protection; a real device " +
                    "screenshot verifies the OS honoured it.",
            )
        }

        Section("Detection") {
            Readout("Capture state", captureState.describe())
            Note(
                "Inactive means \"no evidence of capture\", never a guarantee. Unknown is expected " +
                    "at cold launch and is never coerced to Inactive.",
            )
        }

        Section("App switcher") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TaskSwitcherProtection.entries.forEach { mode ->
                    Chip(
                        label = mode.name,
                        selected = switcherMode == mode,
                        onClick = {
                            switcherMode = mode
                            ComposeShield.taskSwitcherProtection = mode
                            log.add("app-switcher mode = $mode")
                        },
                    )
                }
            }
            Note("Background the app and check the task switcher. Always works with no boundary composed.")
        }

        // Re-read on every recomposition: support is a function of platform, OS version, AND
        // currently-active capabilities. Toggling prevention changes ScreenshotEvents on API 34+.
        Section("Live support readout") {
            Capability.entries.forEach { capability ->
                Readout(capability.name, ComposeShield.supportLevel(capability).describe())
            }
            Note("Toggle prevention and watch ScreenshotEvents change on API 34+.")
        }

        Section("Event log") {
            if (log.entries.isEmpty()) {
                Note("Nothing yet. Take a screenshot, or toggle something above.")
            } else {
                log.entries.forEach { Mono(it) }
            }
        }
    }
}
