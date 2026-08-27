@file:Suppress("MagicNumber")

package io.github.composeshield.sample

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Renders a visible secret used by both the human sample demo and the instrumentation test pipeline.
 *
 * **Human demo**: screenshot with protection ON — the marker should be absent. Screenshot with
 * protection OFF — the marker should be present.
 *
 * **Pipeline**: [ScreenshotValidationTest] and [AppSwitcherValidationTest] detect presence or
 * absence of this marker by sampling the `SHIELD_TEST_SECRET_001` region in captured bitmaps.
 * The `contentDescription` tag ties the sample app marker to the test pipeline identity.
 *
 * The test asserts *marker absent* (not a specific output colour).
 */
@Composable
internal fun SecretMarker() {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(Color(0xFF1B1B1F))
                // Stable pipeline identifier — do NOT rename without updating MarkerDetector.
                .semantics { contentDescription = "SHIELD_TEST_SECRET_001" }
                .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        BasicText(
            text = "TOP SECRET",
            style =
                TextStyle(
                    color = Color(0xFF7CF5A0),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                ),
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
