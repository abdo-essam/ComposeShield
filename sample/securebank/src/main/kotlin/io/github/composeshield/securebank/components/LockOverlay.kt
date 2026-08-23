@file:Suppress("MagicNumber")

package io.github.composeshield.securebank.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Full-screen session lock shown whenever the app was backgrounded.
 *
 * This is the defense-in-depth half of "completely block the content": FLAG_SECURE already blanks
 * captures, but the lock also guarantees nothing sensitive is even *rendered* while the app is not
 * interactive — so the recents snapshot, third-party overlays and accidental shoulder-surfing all
 * see only this screen.
 */
@Composable
internal fun LockOverlay(onUnlock: () -> Unit) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(BankTheme.Navy)
                .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(96.dp))
        Box(
            modifier =
                Modifier
                    .size(72.dp)
                    .background(BankTheme.Accent, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            BasicText(text = "\uD83D\uDD12", style = TextStyle(fontSize = 30.sp))
        }
        Spacer(Modifier.height(24.dp))
        BasicText(
            text = "SecureBank is locked",
            style = TextStyle(color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold),
        )
        Spacer(Modifier.height(8.dp))
        BasicText(
            text = "Session locked after leaving the app. Unlock to continue.",
            style = TextStyle(color = Color(0xFF9AA5B1), fontSize = 13.sp),
        )
        Spacer(Modifier.weight(1f))
        Row(modifier = Modifier.fillMaxWidth().clickable(onClick = onUnlock)) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .border(1.dp, BankTheme.Accent)
                        .background(Color.Transparent),
                contentAlignment = Alignment.Center,
            ) {
                BasicText(
                    text = "Unlock session",
                    style = TextStyle(color = BankTheme.Accent, fontSize = 15.sp, fontWeight = FontWeight.Bold),
                )
            }
        }
        Spacer(Modifier.height(32.dp))
    }
}
