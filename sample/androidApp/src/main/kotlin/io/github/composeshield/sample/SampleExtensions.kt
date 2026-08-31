package io.github.composeshield.sample

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import io.github.composeshield.CaptureState
import io.github.composeshield.SupportLevel
import kotlinx.coroutines.flow.StateFlow

internal fun SupportLevel.describe(): String =
    when (this) {
        SupportLevel.Supported -> "Supported"
        is SupportLevel.Unsupported -> "Unsupported(${reason.name})"
    }

internal fun CaptureState.describe(): String =
    when (this) {
        CaptureState.Active -> "Active"
        CaptureState.Inactive -> "Inactive"
        CaptureState.Unknown -> "Unknown"
    }

@Composable
internal fun <T> StateFlow<T>.collectAsStateSafely() = collectAsState()
