package io.github.composeshield.internal

import io.github.composeshield.CaptureState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

internal class CaptureStateSource(
    private val platform: PlatformProtection,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(CaptureState.Unknown)

    val state: StateFlow<CaptureState> = _state.asStateFlow()

    private var pendingInactive: Job? = null

    private var collection: Job? = null

    private var foregrounds: Job? = null

    fun start() {
        if (collection?.isActive == true) return
        collection = collectReadings()

        if (foregrounds?.isActive != true) {
            foregrounds = scope.launch { platform.observeForegroundEvents().collect { refresh() } }
        }
    }

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
            PlatformCaptureReading.Capturing -> {
                cancelPendingInactive()
                _state.value = CaptureState.Active
            }

            PlatformCaptureReading.NotCapturing -> {
                suppressThenPublishInactive()
            }

            PlatformCaptureReading.Indeterminate -> {
                cancelPendingInactive()
                if (_state.value != CaptureState.Active) _state.value = CaptureState.Unknown
            }
        }
    }

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
        val SUPPRESSION_WINDOW: Duration = 750.milliseconds
    }
}
