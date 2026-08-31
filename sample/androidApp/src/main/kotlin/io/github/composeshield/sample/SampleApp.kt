@file:Suppress("MagicNumber", "LongMethod", "TooManyFunctions")

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
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Popup
import io.github.composeshield.Capability
import io.github.composeshield.ComposeShield
import io.github.composeshield.ProtectionHandle
import io.github.composeshield.SecureContent
import io.github.composeshield.TaskSwitcherProtection

@Composable
fun SampleApp() {
    val log = remember { EventLog() }

    var boundaryActive by remember { mutableStateOf(false) }
    var imperativeHandle by remember { mutableStateOf<ProtectionHandle?>(null) }
    var switcherMode by remember { mutableStateOf(ComposeShield.taskSwitcherProtection) }
    var activeOverlay by remember { mutableStateOf(ActiveOverlay.NONE) }

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

        if (boundaryActive) {
            SecureContent(
                onProtectionFailure = { log.add("boundary reported failure: $it") },
            ) {
                SampleHostContent(
                    activeOverlay = activeOverlay,
                    onDismiss = { activeOverlay = ActiveOverlay.NONE },
                )
            }
        } else {
            SampleHostContent(
                activeOverlay = activeOverlay,
                onDismiss = { activeOverlay = ActiveOverlay.NONE },
            )
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

        Section("Dialog, popup & bottom sheet window protection") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionButton(
                    label = if (activeOverlay == ActiveOverlay.DIALOG) "Hide Dialog" else "Dialog",
                    onClick = {
                        activeOverlay =
                            if (activeOverlay == ActiveOverlay.DIALOG) {
                                ActiveOverlay.NONE
                            } else {
                                ActiveOverlay.DIALOG
                            }
                        log.add("overlay = $activeOverlay")
                    },
                )
                ActionButton(
                    label =
                        if (activeOverlay == ActiveOverlay.BOTTOM_SHEET) {
                            "Hide Sheet"
                        } else {
                            "BottomSheet"
                        },
                    onClick = {
                        activeOverlay =
                            if (activeOverlay == ActiveOverlay.BOTTOM_SHEET) {
                                ActiveOverlay.NONE
                            } else {
                                ActiveOverlay.BOTTOM_SHEET
                            }
                        log.add("overlay = $activeOverlay")
                    },
                )
                ActionButton(
                    label = if (activeOverlay == ActiveOverlay.POPUP) "Hide Popup" else "Popup",
                    onClick = {
                        activeOverlay =
                            if (activeOverlay == ActiveOverlay.POPUP) {
                                ActiveOverlay.NONE
                            } else {
                                ActiveOverlay.POPUP
                            }
                        log.add("overlay = $activeOverlay")
                    },
                )
            }
            Note(
                "When Declarative boundary (SecureContent) is ON, Dialogs, Popups, and BottomSheets " +
                    "declared inside automatically inherit FLAG_SECURE on their separate windows.",
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

private enum class ActiveOverlay {
    NONE,
    DIALOG,
    BOTTOM_SHEET,
    POPUP,
}

@Composable
private fun SampleHostContent(
    activeOverlay: ActiveOverlay,
    onDismiss: () -> Unit,
) {
    SecretMarker()
    when (activeOverlay) {
        ActiveOverlay.NONE -> Unit
        ActiveOverlay.DIALOG -> SampleDialog(onDismiss = onDismiss)
        ActiveOverlay.BOTTOM_SHEET -> SampleBottomSheet(onDismiss = onDismiss)
        ActiveOverlay.POPUP -> SamplePopup(onDismiss = onDismiss)
    }
}

@Composable
private fun SampleDialog(onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1B1B1F))
                    .border(2.dp, Color(0xFF7CF5A0))
                    .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            BasicText(
                text = "DIALOG SECRET WINDOW",
                style =
                    TextStyle(
                        color = Color(0xFF7CF5A0),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                    ),
            )
            BasicText(
                text = "PIN: 9876-5432",
                style =
                    TextStyle(
                        color = Color.White,
                        fontSize = 20.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                    ),
            )
            BasicText(
                text = "If readable in a screenshot with SecureContent ON, protection failed.",
                style = TextStyle(color = Color(0xFFBBBBC4), fontSize = 11.sp),
            )
            ActionButton(label = "Dismiss Dialog", onClick = onDismiss)
        }
    }
}

@Composable
private fun SamplePopup(onDismiss: () -> Unit) {
    Popup(
        alignment = Alignment.Center,
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier =
                Modifier
                    .background(Color(0xFF2A2A32))
                    .border(1.dp, Color(0xFF7CF5A0))
                    .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            BasicText(
                text = "POPUP SECRET WINDOW",
                style =
                    TextStyle(
                        color = Color(0xFF7CF5A0),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                    ),
            )
            BasicText(
                text = "TOKEN: #CS-8831",
                style =
                    TextStyle(
                        color = Color.White,
                        fontSize = 16.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                    ),
            )
            ActionButton(label = "Dismiss Popup", onClick = onDismiss)
        }
    }
}

@Composable
private fun SampleBottomSheet(onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .clickable(onClick = onDismiss),
            verticalArrangement = Arrangement.Bottom,
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF1B1B1F))
                        .border(2.dp, Color(0xFF7CF5A0))
                        .clickable(enabled = false) {}
                        .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Column(
                    modifier =
                        Modifier
                            .padding(bottom = 8.dp)
                            .background(Color(0xFF5F5F6B))
                            .padding(horizontal = 20.dp, vertical = 2.dp),
                ) {}
                BasicText(
                    text = "BOTTOM SHEET SECRET WINDOW",
                    style =
                        TextStyle(
                            color = Color(0xFF7CF5A0),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                )
                BasicText(
                    text = "CARD CVC: 998",
                    style =
                        TextStyle(
                            color = Color.White,
                            fontSize = 20.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                        ),
                )
                BasicText(
                    text = "BottomSheet has its own window. Inside SecureContent, it is protected!",
                    style = TextStyle(color = Color(0xFFBBBBC4), fontSize = 11.sp),
                )
                ActionButton(label = "Dismiss BottomSheet", onClick = onDismiss)
            }
        }
    }
}
