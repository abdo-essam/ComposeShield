@file:Suppress("MagicNumber", "LongMethod", "TooManyFunctions")

package io.github.composeshield.securebank.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.composeshield.securebank.components.BankTheme
import io.github.composeshield.securebank.components.Card
import io.github.composeshield.securebank.components.Heading
import io.github.composeshield.securebank.components.Note
import io.github.composeshield.securebank.components.PrimaryButton
import io.github.composeshield.securebank.data.DemoRepository

/** Account overview — the total balance and per-account rows. Whole screen is sensitive. */
@Composable
internal fun AccountsScreen(
    masked: Boolean,
    onOpenCard: () -> Unit,
    onOpenTransactions: () -> Unit,
    onOpenSecurity: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Heading("Your accounts")
        }

        Card {
            BasicText(text = "Total balance", style = TextStyle(fontSize = 12.sp, color = BankTheme.Muted))
            BasicText(
                text = if (masked) "$ ••••••" else DemoRepository.totalBalance,
                style = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.Bold, color = BankTheme.Navy),
            )
            if (masked) {
                Note("Hidden while capture is detected")
            }
        }

        DemoRepository.accounts.forEach { account ->
            Card {
                BasicText(text = account.name, style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold))
                BasicText(text = account.maskedNumber, style = TextStyle(fontSize = 11.sp, color = BankTheme.Muted))
                BasicText(
                    text = if (masked) "$ ••••••" else account.formatted(),
                    style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold, color = BankTheme.Accent),
                )
            }
        }

        PrimaryButton(text = "Virtual card") { onOpenCard() }
        PrimaryButton(text = "Transactions") { onOpenTransactions() }
        PrimaryButton(text = "Security & demo mode") { onOpenSecurity() }
        Spacer(Modifier.height(8.dp))
        Note("While this screen is visible the whole window blocks screenshots and recording.")
    }
}
