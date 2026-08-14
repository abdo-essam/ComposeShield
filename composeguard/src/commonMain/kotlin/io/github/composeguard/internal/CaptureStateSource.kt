package io.github.composeguard.internal

import io.github.composeguard.CaptureState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Turns raw platform readings into the capture state consumers actually see.
 *
 * Three transformations sit between the platform's word and the library's answer, each existing
 * because of a documented platform defect rather than as defensive padding:
 *
 * 1. **Cold-launch seeding.** The published state starts at [CaptureState.Unknown] and stays there
 *    until the platform affirmatively says otherwise — never seeded to [CaptureState.Inactive].
 * 2. **Spurious-inactive suppression.** A transition *to* inactive is held for [SUPPRESSION_WINDOW]
 *    and published only if it survives.
 * 3. **Asymmetry.** A transition to [CaptureState.Active] is published immediately and never
 *    delayed, because a false negative is far worse than a false positive.
 *
 * See `docs/platform-notes.md` for the underlying platform defects. [CaptureState.Unknown] is never
 * coerced to [CaptureState.Inactive] anywhere in this class.
 *
 * **A single shared upstream**: one collection of the platform flow feeds one [MutableStateFlow], so
 * every collector and every read of `.value` observe the same value by construction. Per-observer
 * polling would let two collectors disagree about whether the screen is being recorded.
 */
internal class CaptureStateSource(
    private val platform: PlatformProtection,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(CaptureState.Unknown)

    /** The published state. Hot, shared, and safe to collect from anywhere. */
    val state: StateFlow<CaptureState> = _state.asStateFlow()

    /**
     * The pending "capture stopped" transition, held until it proves itself.
     *
     * Cancelled — not just ignored — whenever a later reading contradicts it, so a spurious inactive
     * followed by a genuine active never publishes the inactive at all.
     */
    private var pendingInactive: Job? = null

    /** The in-flight collection of platform readings. Replaced, never duplicated, by [refresh]. */
    private var collection: Job? = null

    /** The foreground subscription driving [refresh]. Started once, never replaced. */
    private var foregrounds: Job? = null

    /**
     * Begins collecting platform readings.
     *
     * Idempotent: calling it twice does not open a second subscription, which would double every
     * emission and let two collections race to publish contradictory states.
     */
    fun start() {
        if (collection?.isActive == true) return
        collection = collectReadings()

        // Subscribed here rather than left to a caller: a re-poll that has to be remembered is one
        // that eventually is not, and the symptom — a stale Inactive after backgrounding — is
        // invisible until it matters. Started once alongside the first collection.
        if (foregrounds?.isActive != true) {
            foregrounds =
                scope.launch {
                    platform.observeForegroundEvents().collect { refresh() }
                }
        }
    }

    /**
     * Re-reads capture state on return to foreground.
     *
     * Capture that began while the app was backgrounded may have produced no transition the app was
     * alive to observe. Re-subscribing forces a fresh read — both actuals emit the current reading
     * on collection — so the old subscription is cancelled first rather than left running alongside
     * the new one.
     *
     * The published state is deliberately *not* reset to [CaptureState.Unknown] first: a live
     * `Active` is a stronger claim than the absence of a fresh reading.
     */
    fun refresh() {
        collection?.cancel()
        collection = collectReadings()
    }

    private fun collectReadings(): Job =
        scope.launch {
            platform.observeCaptureState().collect(::onReading)
        }

    private fun onReading(reading: PlatformCaptureReading) {
        when (reading) {
            // Never delayed, and it cancels any in-flight suppression: if the platform says capture
            // is happening, a pending "it stopped" is now known to have been wrong.
            PlatformCaptureReading.Capturing -> {
                cancelPendingInactive()
                _state.value = CaptureState.Active
            }

            PlatformCaptureReading.NotCapturing -> {
                suppressThenPublishInactive()
            }

            // The platform cannot say. Publish that honestly rather than assuming the safe-looking
            // answer — but do not let it retract a live Active reading, which is a stronger claim.
            PlatformCaptureReading.Indeterminate -> {
                cancelPendingInactive()
                if (_state.value != CaptureState.Active) _state.value = CaptureState.Unknown
            }
        }
    }

    /**
     * Holds a transition to inactive for [SUPPRESSION_WINDOW], publishing only if nothing contradicts
     * it.
     *
     * An already-pending suppression is left alone rather than restarted, so a platform emitting
     * inactive repeatedly cannot keep pushing the deadline out and stall the transition forever.
     */
    private fun suppressThenPublishInactive() {
        if (_state.value == CaptureState.Inactive) return
        if (pendingInactive?.isActive == true) return

        pendingInactive =
            scope.launch {
                delay(SUPPRESSION_WINDOW)
                _state.value = CaptureState.Inactive
            }
    }

    private fun cancelPendingInactive() {
        pendingInactive?.cancel()
        pendingInactive = null
    }

    private companion object {
        /**
         * How long a "capture stopped" reading must hold before it is believed.
         *
         * Long enough to absorb the iOS Live Activity flap, short enough that genuine transitions
         * stay observable within the 1 s budget. Only ever delays the *reassuring* direction.
         */
        val SUPPRESSION_WINDOW: Duration = 750.milliseconds
    }
}
