package io.github.composeguard.internal

import android.os.Build
import io.github.composeguard.SupportLevel

/**
 * Standalone recents protection via `Activity.setRecentsScreenshotEnabled(false)` (API 33+).
 *
 * Used only when the switcher must be protected *without* capture prevention. Where prevention is
 * active, `FLAG_SECURE` already obscures the recents thumbnail as an inseparable side effect, and
 * common code suppresses this call rather than double-applying.
 *
 * This primitive is preferred over toggling `FLAG_SECURE` for the switcher because it is
 * flicker-free: flipping `FLAG_SECURE` on a visible window tears down and recreates its surface,
 * which the user sees as a black frame. It also has the right semantics — AOSP confirms it affects
 * only the Overview representation, leaving user and Assistant screenshots working.
 *
 * Below API 33 this reports unsupported. The hidden `setDisablePreviewScreenshots` is rejected
 * rather than used: it is `@UnsupportedAppUsage` with `maxTargetSdk S`, and reaching it requires
 * reflection, which the zero-reflection guarantee forbids outright.
 */
internal class AppSwitcher {
    fun support(): SupportLevel = supportedFromApi(Build.VERSION_CODES.TIRAMISU)

    fun apply(
        window: WindowKey,
        enabled: Boolean,
    ) {
        if (sdkInt < Build.VERSION_CODES.TIRAMISU) return
        val activity = activityFor(window) ?: return

        onMainThread(ifDeferred = Unit) {
            // Inverted deliberately: the platform names the flag for the screenshot being *enabled*,
            // whereas the library names it for protection being enabled.
            activity.setRecentsScreenshotEnabled(!enabled)
        }
    }
}
