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
    var showDialog by remember { mutableStateOf(false) }
    var showPopup by remember { mutableStateOf(false) }
    var showBottomSheet by remember { mutableStateOf(false) }

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
        // protected — not just the marker region. Any Dialog, Popup, or BottomSheet spawned inside
        // inherits protection automatically.
        if (boundaryActive) {
            SecureContent(
                onProtectionFailure = { log.add("boundary reported failure: $it") },
            ) {
                SecretMarker()
                if (showDialog) {
                    SampleDialog(onDismiss = { showDialog = false })
                }
                if (showPopup) {
                    SamplePopup(onDismiss = { showPopup = false })
                }
                if (showBottomSheet) {
                    SampleBottomSheet(onDismiss = { showBottomSheet = false })
                }
            }
        } else {
            SecretMarker()
            if (showDialog) {
                SampleDialog(onDismiss = { showDialog = false })
            }
            if (showPopup) {
                SamplePopup(onDismiss = { showPopup = false })
            }
            if (showBottomSheet) {
                SampleBottomSheet(onDismiss = { showBottomSheet = false })
            }
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
                    label = if (showDialog) "Hide Dialog" else "Dialog",
                    onClick = {
                        showDialog = !showDialog
                        log.add(if (showDialog) "opened dialog" else "closed dialog")
                    },
                )
                ActionButton(
                    label = if (showBottomSheet) "Hide Sheet" else "BottomSheet",
                    onClick = {
                        showBottomSheet = !showBottomSheet
                        log.add(if (showBottomSheet) "opened bottom sheet" else "closed bottom sheet")
                    },
                )
                ActionButton(
                    label = if (showPopup) "Hide Popup" else "Popup",
                    onClick = {
                        showPopup = !showPopup
                        log.add(if (showPopup) "opened popup" else "closed popup")
                    },
                )
            }
            Note(
                "When Declarative boundary (SecureContent) is ON, Dialogs, Popups, and Material3 " +
                    "ModalBottomSheets declared inside it automatically inherit FLAG_SECURE on their " +
                    "separate windows — without needing SecureContent inside them.",
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

/**
 * A dialog window with secret content.
 *
 * Notice there is NO [SecureContent] call inside this composable. Protection is inherited
 * automatically from the host [SecureContent] boundary.
 */
@Composable
private fun SampleDialog(onDismiss: () -> Unit) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1B1B1F))
                    .border(2.dp, Color(0xFF7CF5A0))
                    .padding(20.dp),
            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            androidx.compose.foundation.text.BasicText(
                text = "DIALOG SECRET WINDOW",
                style =
                    androidx.compose.ui.text.TextStyle(
                        color = Color(0xFF7CF5A0),
                        fontSize = 13.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    ),
            )
            androidx.compose.foundation.text.BasicText(
                text = "PIN: 9876-5432",
                style =
                    androidx.compose.ui.text.TextStyle(
                        color = Color.White,
                        fontSize = 20.sp,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    ),
            )
            androidx.compose.foundation.text.BasicText(
                text = "If you can read this in a screenshot while SecureContent is ON, dialog protection failed.",
                style =
                    androidx.compose.ui.text.TextStyle(
                        color = Color(0xFFBBBBC4),
                        fontSize = 11.sp,
                    ),
            )
            ActionButton(label = "Dismiss Dialog", onClick = onDismiss)
        }
    }
}

/**
 * A popup window with secret content.
 */
@Composable
private fun SamplePopup(onDismiss: () -> Unit) {
    androidx.compose.ui.window.Popup(
        alignment = androidx.compose.ui.Alignment.Center,
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier =
                Modifier
                    .background(Color(0xFF2A2A32))
                    .border(1.dp, Color(0xFF7CF5A0))
                    .padding(16.dp),
            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            androidx.compose.foundation.text.BasicText(
                text = "POPUP SECRET WINDOW",
                style =
                    androidx.compose.ui.text.TextStyle(
                        color = Color(0xFF7CF5A0),
                        fontSize = 12.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    ),
            )
            androidx.compose.foundation.text.BasicText(
                text = "TOKEN: #CS-8831",
                style =
                    androidx.compose.ui.text.TextStyle(
                        color = Color.White,
                        fontSize = 16.sp,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    ),
            )
            ActionButton(label = "Dismiss Popup", onClick = onDismiss)
        }
    }
}

/**
 * A bottom sheet window with secret content.
 *
 * This bottom sheet creates its own separate [android.view.Window]. Notice there is NO
 * [SecureContent] call inside this composable: protection is inherited automatically from the host
 * [SecureContent] boundary.
 */
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
                // Drag handle
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
                    text = "This BottomSheet creates its own window. Inside SecureContent, it is protected automatically!",
                    style =
                        TextStyle(
                            color = Color(0xFFBBBBC4),
                            fontSize = 11.sp,
                        ),
                )
                ActionButton(label = "Dismiss BottomSheet", onClick = onDismiss)
            }
        }
    }
}



