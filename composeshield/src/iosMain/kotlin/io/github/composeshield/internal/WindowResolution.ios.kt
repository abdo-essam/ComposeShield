package io.github.composeshield.internal

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

@Composable
internal actual fun rememberWindowKey(): WindowKey =
    remember {
        activeWindow()?.let(::registerWindow) ?: WindowKey.Unbound
    }

internal actual fun resolveCurrentWindowKey(): WindowKey = activeWindow()?.let(::registerWindow) ?: WindowKey.Unbound
