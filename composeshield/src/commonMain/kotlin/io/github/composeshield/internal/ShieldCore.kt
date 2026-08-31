package io.github.composeshield.internal

import io.github.composeshield.Capability
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

internal class ShieldCore(
    platform: PlatformProtection,
) {
    private val scope =
        CoroutineScope(
            SupervisorJob() +
                mainDispatcher() +
                CoroutineName("ComposeShield") +
                CoroutineExceptionHandler { _, _ -> },
        )

    private val _protectionFailures =
        MutableSharedFlow<Capability>(replay = FAILURE_REPLAY, extraBufferCapacity = FAILURE_BUFFER)

    val protectionFailures: Flow<Capability> = _protectionFailures.asSharedFlow()

    val registry: ProtectionRegistry =
        ProtectionRegistry(
            platform = platform,
            onProtectionFailure = { _protectionFailures.tryEmit(it) },
        )

    val supportResolver: SupportResolver = SupportResolver(platform)

    val captureStates: CaptureStateSource = CaptureStateSource(platform, scope)

    val screenshotEvents: Flow<Unit> = platform.observeScreenshotEvents()

    init {
        captureStates.start()

        scope.launch {
            var hadConcreteWindow = false
            registry.snapshots.collect { snapshot ->
                val hasConcreteWindow = snapshot.requests.keys.any { it != WindowKey.Unbound }
                if (hasConcreteWindow && !hadConcreteWindow) captureStates.refresh()
                hadConcreteWindow = hadConcreteWindow or hasConcreteWindow
            }
        }
    }

    private companion object {
        const val FAILURE_REPLAY = 1

        const val FAILURE_BUFFER = 8
    }
}

@Suppress("SwallowedException", "TooGenericExceptionCaught")
private fun mainDispatcher(): CoroutineDispatcher {
    val candidate =
        try {
            Dispatchers.Main
        } catch (unavailable: Throwable) {
            return Dispatchers.Unconfined
        }

    val probe =
        CoroutineScope(
            SupervisorJob() +
                candidate +
                CoroutineExceptionHandler { _, _ -> },
        )
    return try {
        probe.launch { }.cancel()
        candidate
    } catch (unavailable: Throwable) {
        Dispatchers.Unconfined
    }
}

internal val shieldCore: ShieldCore by lazy { ShieldCore(createPlatformProtection()) }
