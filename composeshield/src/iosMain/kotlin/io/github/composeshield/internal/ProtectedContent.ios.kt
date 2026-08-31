package io.github.composeshield.internal

import androidx.compose.runtime.Composable
import io.github.composeshield.Capability

@Composable
internal actual fun ProtectedContent(
    capabilities: Set<Capability>,
    content: @Composable () -> Unit,
) {
    content()
}
