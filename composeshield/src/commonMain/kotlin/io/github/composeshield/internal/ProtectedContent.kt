package io.github.composeshield.internal

import androidx.compose.runtime.Composable
import io.github.composeshield.Capability

@Composable
internal expect fun ProtectedContent(
    capabilities: Set<Capability>,
    content: @Composable () -> Unit,
)
