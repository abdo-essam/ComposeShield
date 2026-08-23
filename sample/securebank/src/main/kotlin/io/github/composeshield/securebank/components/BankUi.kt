@file:Suppress("MagicNumber", "TooManyFunctions")

package io.github.composeshield.securebank.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** SecureBank palette — navy/green, deliberately not Material so no extra dependency is needed. */
object BankTheme {
    val Navy = Color(0xFF0A2540)
    val Ink = Color(0xFF14213D)
    val Accent = Color(0xFF1B7F3B)
    val Danger = Color(0xFFC62828)
    val Surface = Color.White
    val Backdrop = Color(0xFFF2F4F8)
    val Border = Color(0xFFD9DEE7)
    val Muted = Color(0xFF6B7487)
    val CardDark = Color(0xFF101820)
    val OnCardMuted = Color(0xFF9AA5B1)
}

/** Large page heading. */
@Composable
internal fun Heading(
    text: String,
    color: Color = BankTheme.Ink,
) {
    BasicText(text = text, style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold, color = color))
}

/** Secondary explanatory line. */
@Composable
internal fun Note(text: String) {
    BasicText(text = text, style = TextStyle(fontSize = 12.sp, color = BankTheme.Muted))
}

/** Monospace log/status line. */
@Composable
internal fun Mono(
    text: String,
    color: Color = BankTheme.Ink,
) {
    BasicText(text = text, style = TextStyle(fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = color))
}

/** White card with border grouping related rows. */
@Composable
internal fun Card(
    title: String? = null,
    content: @Composable () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(BankTheme.Surface)
                .border(1.dp, BankTheme.Border)
                .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (title != null) {
            BasicText(
                text = title,
                style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = BankTheme.Navy),
            )
        }
        content()
    }
}

/** Filled action button. */
@Composable
internal fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(BankTheme.Accent)
                .clickable(onClick = onClick)
                .padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        BasicText(text = text, style = TextStyle(color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold))
    }
}

/** Single-line text field used on the login screen. */
@Composable
internal fun Field(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    password: Boolean = false,
) {
    Column {
        BasicText(text = label, style = TextStyle(fontSize = 11.sp, color = BankTheme.Muted))
        Spacer(Modifier.height(4.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(fontSize = 15.sp, color = BankTheme.Ink),
            cursorBrush = SolidColor(BankTheme.Accent),
            visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(BankTheme.Backdrop)
                    .border(1.dp, BankTheme.Border)
                    .padding(12.dp),
        )
    }
}

/** Label/value readout row. */
@Composable
internal fun Readout(
    label: String,
    value: String,
    valueColor: Color = BankTheme.Ink,
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        BasicText(text = label, style = TextStyle(fontSize = 13.sp, color = BankTheme.Muted))
        BasicText(text = value, style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = valueColor))
    }
}

/** Tappable ON/OFF row. */
@Composable
internal fun ToggleRow(
    label: String,
    on: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onToggle(!on) },
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier =
                Modifier
                    .background(if (on) BankTheme.Accent else BankTheme.Border)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
        ) {
            BasicText(
                text = if (on) "ON" else "OFF",
                style = TextStyle(color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold),
            )
        }
        BasicText(text = label, modifier = Modifier.weight(1f), style = TextStyle(fontSize = 13.sp))
    }
}

/** Full-width banner for security-critical notices (recording detected). */
@Composable
internal fun Banner(
    text: String,
    background: Color,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(background)
                .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        BasicText(text = text, style = TextStyle(color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold))
    }
}
