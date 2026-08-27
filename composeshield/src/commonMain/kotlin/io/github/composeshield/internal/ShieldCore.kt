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

/**
 * The library's assembled internals, wired once per process.
 *
 * [ComposeShield][io.github.composeshield.ComposeShield] and
 * [SecureContent][io.github.composeshield.SecureContent] are two entry points onto one state machine,
 * not two independent features: imperative and declarative requests must compose through the same
 * reference counting, so they reach the same [ProtectionRegistry] instance.
 *
 * Separated from the public facade so the wiring can be constructed against a fake platform in
 * tests. A facade building its own dependencies in an `init` block would be untestable without a
 * device.
 */
internal class ShieldCore(
    platform: PlatformProtection,
) {
    /**
     * Scope for the library's own long-lived collections.
     *
     * A [SupervisorJob] so a failure in one platform observer cannot cancel the others — losing
     * screenshot events should not also silence capture-state detection — and a
     * [CoroutineExceptionHandler] so a failing observer cannot take the host process down: these
     * coroutines outlive the caller that triggered them, and an unhandled error there would crash
     * the app. Main-dispatched because every platform observer this drives touches UI-thread-affine APIs.
     */
    private val scope =
        CoroutineScope(
            SupervisorJob() +
                mainDispatcher() +
                CoroutineName("ComposeShield") +
                // Deliberately contained; see the scope's KDoc.
                CoroutineExceptionHandler { _, _ -> },
        )

    private val _protectionFailures =
        MutableSharedFlow<Capability>(replay = FAILURE_REPLAY, extraBufferCapacity = FAILURE_BUFFER)

    /**
     * Mechanism failures as they happen.
     *
     * Buffered and non-suspending to publish: the registry reports failures from inside a
     * reconcile, so emission must neither suspend nor lose the signal when no collector is attached
     * at that instant. [extraBufferCapacity] absorbs subscriber backpressure; [replay] covers the
     * window *before* any collector attaches — the first boundary acquires during composition,
     * ahead of its own failure collector, so without replay that first failure would be silently
     * discarded. A late collector always hears the most recent failure.
     */
    val protectionFailures: Flow<Capability> = _protectionFailures.asSharedFlow()

    val registry: ProtectionRegistry =
        ProtectionRegistry(
            platform = platform,
            onProtectionFailure = { _protectionFailures.tryEmit(it) },
        )

    val supportResolver: SupportResolver = SupportResolver(platform)

    val captureStates: CaptureStateSource = CaptureStateSource(platform, scope)

    /** Screenshot events, straight from the platform. Empty where unsupported, never an error. */
    val screenshotEvents: Flow<Unit> = platform.observeScreenshotEvents()

    init {
        // Capture-state observation is a long-lived library concern, not a consequence of reading
        // the public property, so it starts here rather than as a side effect of the getter — a
        // property read must stay a pure read. start() is idempotent.
        captureStates.start()

        // Cold-start healing. Both Android observers resolve their host through the first
        // registered activity, so collections begun before any window exists (an imperative
        // `protect()` from Application.onCreate, say) sit subscribed-but-silent forever: the
        // foreground flow that drives refresh() is itself one of the dead ones. The first
        // concrete window binding is the event that changes that — force a genuine re-read
        // exactly then, and never again. Event-driven; no polling.
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
        /** The one failure emitted before any collector attached is still owed to the next collector. */
        const val FAILURE_REPLAY = 1

        const val FAILURE_BUFFER = 8
    }
}

/**
 * The main dispatcher, or an unconfined fallback where the platform has none.
 *
 * `Dispatchers.Main` is absent in a JVM host test, a background-only process, or a JVM consumer with
 * no UI toolkit — and with kotlinx-coroutines-test on the classpath resolving it can even *succeed*
 * while every dispatch fails, so availability cannot be read off a property. The dispatcher is
 * therefore probed with a real dispatch. No public operation may throw because a capability is
 * unavailable, and the probe itself must stay invisible: it runs under a scope that carries its own
 * [CoroutineExceptionHandler], because cancelling a coroutine queued on a broken dispatcher makes
 * the failure surface asynchronously from the dispatch machinery — past the caller's try/catch,
 * and otherwise straight into whatever uncaught-exception reporting runs next.
 *
 * The fallback does not weaken protection: platform effects are marshalled to the main thread by the
 * actuals themselves, so this scope governs only where the library's own observer coroutines resume.
 *
 * Known limit: a dispatcher whose dispatch fails *asynchronously*, after this probe has returned it,
 * cannot be detected here — verifying completion would mean blocking a constructor thread. The
 * consequence is contained rather than prevented: such observers fail into the scope's
 * [CoroutineExceptionHandler] instead of crashing anything.
 */
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
                // The probe's failures end here, deliberately — see the KDoc above.
                CoroutineExceptionHandler { _, _ -> },
        )
    return try {
        probe.launch { }.cancel()
        candidate
    } catch (unavailable: Throwable) {
        Dispatchers.Unconfined
    }
}

/**
 * The process-wide instance.
 *
 * A single mutable protection state per process is not a convenience — it is what the platform
 * imposes. One physical flag exists per window, so two registries would each believe they owned it
 * and the second to reconcile would silently undo the first.
 */
internal val shieldCore: ShieldCore by lazy { ShieldCore(createPlatformProtection()) }
