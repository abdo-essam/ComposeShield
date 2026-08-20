@file:Suppress("MagicNumber", "TooManyFunctions")

package io.github.composeshield.sample

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Large page heading. */
@Composable
internal fun Heading(text: String) {
    BasicText(
        text = text,
        style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold),
    )
}

/** Titled card grouping related controls. */
@Composable
internal fun Section(
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

/** Tappable ON/OFF row labelled with [label]. */
@Composable
internal fun Toggle(
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

/** Selectable pill for a single option within an option group. */
@Composable
internal fun Chip(
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

/** Key-value row with a monospace value. */
@Composable
internal fun Readout(
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
            style =
                TextStyle(
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                ),
        )
    }
}

/** Dimmed explanatory note, smaller than body text. */
@Composable
internal fun Note(text: String) {
    BasicText(
        text = text,
        style = TextStyle(fontSize = 11.sp, color = Color(0xFF5F5F6B)),
    )
}

/** Monospace log line. */
@Composable
internal fun Mono(text: String) {
    BasicText(
        text = text,
        style = TextStyle(fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = Color(0xFF33333B)),
    )
}
