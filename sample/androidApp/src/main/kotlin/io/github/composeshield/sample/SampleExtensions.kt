package io.github.composeshield.sample

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import io.github.composeshield.CaptureState
import io.github.composeshield.SupportLevel
import kotlinx.coroutines.flow.StateFlow

/** Converts a [SupportLevel] to a short human-readable label. */
internal fun SupportLevel.describe(): String =
    when (this) {
        SupportLevel.Supported -> "Supported"
        is SupportLevel.Unsupported -> "Unsupported(${reason.name})"
    }

/** Converts a [CaptureState] to a short human-readable label. */
internal fun CaptureState.describe(): String =
    when (this) {
        CaptureState.Active -> "Active"
        CaptureState.Inactive -> "Inactive"
        CaptureState.Unknown -> "Unknown"
    }

/**
 * Thin alias for [collectAsState] that avoids importing the full coroutines qualifier
 * at every call site in the sample. Named `Safely` as a reminder that initial values
 * are not needed — [StateFlow] always has a value.
 */
@Composable
internal fun <T> StateFlow<T>.collectAsStateSafely() = collectAsState()
