package io.github.composeshield.internal

import androidx.compose.runtime.Composable

/**
 * Identifies the window the calling composable is rendered into.
 *
 * `expect` because "which window am I in?" has no common answer: Android resolves an `Activity` (or
 * a dialog's own window) from the composition, iOS resolves a `UIWindowScene`. The result is an
 * opaque key, so common code coordinates per-window state without ever holding a platform window it
 * might outlive.
 *
 * Returns [WindowKey.Unbound] when no host is available yet. Callers must treat that as a real key
 * and let [ProtectionRegistry.bindWindow] re-point the request later, rather than skipping the
 * request — the spec requires a request made before a host exists to be honoured once one appears,
 * not dropped.
 */
@Composable
internal expect fun rememberWindowKey(): WindowKey

/**
 * Resolves the current foreground window key without requiring a composition context.
 *
 * Used by the imperative [io.github.composeshield.ComposeShield.acquire] path to obtain a real
 * [WindowKey] rather than [WindowKey.Unbound] when a window already exists on screen. If no window
 * can be resolved (cold start, no active scene/activity), returns [WindowKey.Unbound] so the request
 * is held and applied once [ProtectionRegistry.bindWindow] is called from the first composition.
 */
internal expect fun resolveCurrentWindowKey(): WindowKey
