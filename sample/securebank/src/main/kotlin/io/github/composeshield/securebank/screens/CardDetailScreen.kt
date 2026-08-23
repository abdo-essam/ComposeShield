@file:Suppress("MagicNumber", "LongMethod", "TooManyFunctions")

package io.github.composeshield.securebank.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.composeshield.securebank.components.BankTheme
import io.github.composeshield.securebank.components.Card
import io.github.composeshield.securebank.components.Heading
import io.github.composeshield.securebank.components.Note
import io.github.composeshield.securebank.components.Readout
import io.github.composeshield.securebank.components.ToggleRow
import io.github.composeshield.securebank.data.DemoRepository

/**
 * Virtual card screen — the highest-value content in the app: full PAN, expiry and CVV.
 * The CVV is hidden behind an explicit reveal, and everything masks while capture is detected.
 */
@Composable
internal fun CardDetailScreen(masked: Boolean) {
    var cvvRevealed by remember { mutableStateOf(false) }
    val showCvv = cvvRevealed && !masked

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Heading("Virtual card")

        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(190.dp)
                    .background(BankTheme.CardDark, RoundedCornerShape(16.dp))
                    .padding(20.dp),
        ) {
            Column(verticalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxSize()) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    BasicText(
                        text = "SecureBank Debit",
                        style = TextStyle(color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold),
                    )
                    BasicText(
                        text = "VISA",
                        style = TextStyle(color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold),
                    )
                }
                BasicText(
                    text = if (masked) "•••• •••• •••• ••••" else DemoRepository.CARD_NUMBER,
                    style =
                        TextStyle(
                            color = Color.White,
                            fontSize = 20.sp,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 2.sp,
                        ),
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    BasicText(
                        text = DemoRepository.CARD_HOLDER,
                        style = TextStyle(color = BankTheme.OnCardMuted, fontSize = 12.sp),
                    )
                    BasicText(
                        text = "EXP ${DemoRepository.CARD_EXPIRY}",
                        style = TextStyle(color = BankTheme.OnCardMuted, fontSize = 12.sp),
                    )
                }
            }
        }

        Card(title = "Card details") {
            Readout("Card number (last 4)", DemoRepository.CARD_LAST4)
            Readout("Expiry", if (masked) "••/••" else DemoRepository.CARD_EXPIRY)
            Readout("CVV", if (showCvv) DemoRepository.CARD_CVV else "•••")
            ToggleRow(label = "Reveal CVV", on = showCvv, onToggle = { cvvRevealed = it })
            Note("CVV auto-masks while screen capture is detected.")
        }

        Card(title = "Freeze card") {
            ToggleRow(label = "Frozen", on = false, onToggle = { })
            Note("Decorative — demonstrates ordinary interactive content inside the protected window.")
        }

        Note("Full number, expiry and CVV are visible only while the window blocks all capture.")
    }
}
