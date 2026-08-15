@file:Suppress("LongMethod", "TooManyFunctions", "MagicNumber")

package io.github.composeshield.sample

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.composeshield.AppSwitcherProtection
import io.github.composeshield.Capability
import io.github.composeshield.CaptureState
import io.github.composeshield.ComposeShield
import io.github.composeshield.ProtectionHandle
import io.github.composeshield.SecureContent
import io.github.composeshield.SupportLevel

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
    var switcherMode by remember { mutableStateOf(ComposeShield.appSwitcherProtection) }

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

        // The marker, wrapped only while the boundary is active. Note that protection is
        // window-scoped: while this is composed, the whole screen is protected, not just the marker.
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
                label = "Imperative claim (ComposeShield.acquire)",
                on = imperativeHandle != null,
                onToggle = { wanted ->
                    if (wanted) {
                        imperativeHandle = ComposeShield.acquire()
                        log.add("imperative claim acquired")
                    } else {
                        imperativeHandle?.release()
                        imperativeHandle = null
                        log.add("imperative claim released")
                    }
                },
            )
            Note(
                "Both claims are reference-counted together: protection is withdrawn only when the " +
                    "last one is gone. Turn both on, release one, and the marker stays protected.",
            )
            Note(
                "acquire() records a protection claim — it does not guarantee the OS has applied " +
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
                AppSwitcherProtection.entries.forEach { mode ->
                    Chip(
                        label = mode.name,
                        selected = switcherMode == mode,
                        onClick = {
                            switcherMode = mode
                            ComposeShield.appSwitcherProtection = mode
                            log.add("app-switcher mode = $mode")
                        },
                    )
                }
            }
            Note("Background the app and check the task switcher. Always works with no boundary composed.")
        }

        // Re-read on every recomposition rather than caching: support is a function of platform, OS
        // version, AND currently-active capabilities. Toggling prevention above changes the
        // ScreenshotEvents row on API 34+.
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

/**
 * A visible secret. Should be absent from screenshots when screenshot prevention is active
 * and honoured by the platform.
 *
 * Automated tests assert the library *requested* protection. Only a real device screenshot
 * confirms the OS *honoured* it — those are two separate guarantees.
 */
@Composable
private fun SecretMarker() {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(Color(0xFF1B1B1F))
                .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        BasicText(
            text = "TOP SECRET",
            style = TextStyle(color = Color(0xFF7CF5A0), fontSize = 12.sp, fontWeight = FontWeight.Bold),
        )
        BasicText(
            text = "4111 1111 1111 1111",
            style =
                TextStyle(
                    color = Color.White,
                    fontSize = 22.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                ),
        )
        BasicText(
            text = "If you can read this in a screenshot, prevention is NOT active.",
            style = TextStyle(color = Color(0xFFBBBBC4), fontSize = 11.sp),
        )
    }
}

private fun SupportLevel.describe(): String =
    when (this) {
        SupportLevel.Supported -> "Supported"
        is SupportLevel.Unsupported -> "Unsupported(${reason.name})"
    }

private fun CaptureState.describe(): String =
    when (this) {
        CaptureState.Active -> "Active"
        CaptureState.Inactive -> "Inactive"
        CaptureState.Unknown -> "Unknown"
    }

/** A bounded, newest-last log. Bounded so a long session cannot grow it without limit. */
private class EventLog {
    val entries = mutableStateListOf<String>()

    fun add(message: String) {
        if (entries.size >= MAX_ENTRIES) entries.removeAt(0)
        entries.add(message)
    }

    private companion object {
        const val MAX_ENTRIES = 30
    }
}

@Composable
private fun <T> kotlinx.coroutines.flow.StateFlow<T>.collectAsStateSafely() = collectAsState()

@Composable
private fun Heading(text: String) {
    BasicText(text = text, style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold))
}

@Composable
private fun Section(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(Color.White)
                .border(1.dp, Color(0xFFE0E0E6))
                .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        BasicText(text = title, style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold))
        content()
    }
}

@Composable
private fun Toggle(
    label: String,
    on: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onToggle(!on) },
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier =
                Modifier
                    .background(if (on) Color(0xFF1B7F3B) else Color(0xFFCFCFD6))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
        ) {
            BasicText(
                text = if (on) "ON" else "OFF",
                style = TextStyle(color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold),
            )
        }
        BasicText(text = label, style = TextStyle(fontSize = 13.sp))
    }
}

@Composable
private fun Chip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .background(if (selected) Color(0xFF1B1B1F) else Color(0xFFECECF1))
                .clickable(onClick = onClick)
                .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        BasicText(
            text = label,
            style =
                TextStyle(
                    color = if (selected) Color.White else Color(0xFF1B1B1F),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                ),
        )
    }
}

@Composable
private fun Readout(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        BasicText(text = label, style = TextStyle(fontSize = 12.sp))
        BasicText(
            text = value,
            style = TextStyle(fontSize = 12.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold),
        )
    }
}

@Composable
private fun Note(text: String) {
    BasicText(text = text, style = TextStyle(fontSize = 11.sp, color = Color(0xFF5F5F6B)))
}

@Composable
private fun Mono(text: String) {
    BasicText(
        text = text,
        style = TextStyle(fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = Color(0xFF33333B)),
    )
}
