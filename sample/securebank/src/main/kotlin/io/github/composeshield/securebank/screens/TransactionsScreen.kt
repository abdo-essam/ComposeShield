@file:Suppress("MagicNumber", "LongMethod")

package io.github.composeshield.securebank.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.composeshield.securebank.components.BankTheme
import io.github.composeshield.securebank.components.Card
import io.github.composeshield.securebank.components.Heading
import io.github.composeshield.securebank.components.Note
import io.github.composeshield.securebank.data.DemoRepository

/** Posted transactions. Amounts are treated as sensitive alongside balances. */
@Composable
internal fun TransactionsScreen() {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Heading("Transactions")
        Card(title = "Recent activity") {
            DemoRepository.transactions.forEach { transaction ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    BasicText(text = transaction.label, style = TextStyle(fontSize = 13.sp))
                    BasicText(
                        text = transaction.formatted(),
                        style =
                            TextStyle(
                                fontSize = 13.sp,
                                fontFamily = FontFamily.Monospace,
                                color = if (transaction.amountMinor < 0) BankTheme.Ink else BankTheme.Accent,
                            ),
                    )
                }
            }
        }
        Note("This route is protected like every other sensitive screen.")
    }
}
