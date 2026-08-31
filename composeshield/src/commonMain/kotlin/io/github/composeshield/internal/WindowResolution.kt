package io.github.composeshield.internal

import androidx.compose.runtime.Composable

@Composable
internal expect fun rememberWindowKey(): WindowKey

internal expect fun resolveCurrentWindowKey(): WindowKey
