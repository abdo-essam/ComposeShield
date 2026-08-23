@file:Suppress("MagicNumber")

package io.github.composeshield.securebank.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.composeshield.securebank.components.BankTheme
import io.github.composeshield.securebank.components.Field
import io.github.composeshield.securebank.components.Note
import io.github.composeshield.securebank.components.PrimaryButton

/**
 * Sign-in screen. Credentials are sensitive, so this route is protected unconditionally — even a
 * partially-typed password must never land in a screenshot or the recents preview.
 */
@Composable
internal fun LoginScreen(onSignedIn: () -> Unit) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(BankTheme.Backdrop)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Spacer(Modifier.height(32.dp))
        BasicText(
            text = "SecureBank",
            style = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.Bold, color = BankTheme.Navy),
        )
        BasicText(text = "Mobile banking", style = TextStyle(fontSize = 14.sp, color = BankTheme.Muted))
        Spacer(Modifier.height(16.dp))
        Field(label = "Username", value = username, onValueChange = { username = it })
        Field(label = "Password", value = password, onValueChange = { password = it }, password = true)
        Spacer(Modifier.height(8.dp))
        PrimaryButton(text = "Sign in") { onSignedIn() }
        Note("Any credentials work in this demo. This screen is capture-protected at all times.")
        Spacer(Modifier.weight(1f))
        BasicText(
            text = "Protected by ComposeShield",
            style = TextStyle(fontSize = 11.sp, color = BankTheme.Muted),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
