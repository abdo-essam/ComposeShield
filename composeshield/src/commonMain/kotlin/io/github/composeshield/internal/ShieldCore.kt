package io.github.composeshield.internal

import io.github.composeshield.Capability
import kotlinx.coroutines.CoroutineDispatcher
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
     * screenshot events should not also silence capture-state detection. Main-dispatched because
     * every platform observer this drives ultimately touches UI-thread-affine APIs.
     */
    private val scope = CoroutineScope(SupervisorJob() + mainDispatcher() + CoroutineName("ComposeShield"))

    private val _protectionFailures = MutableSharedFlow<Capability>(extraBufferCapacity = FAILURE_BUFFER)

    /**
     * Mechanism failures as they happen.
     *
     * Buffered and non-suspending to publish: the registry reports failures from inside a
     * reconcile, and a security-relevant signal must never be dropped or block the caller because
     * nothing happened to be collecting at that instant.
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

    private companion object {
        const val FAILURE_BUFFER = 8
    }
}

/**
 * The main dispatcher, or an unconfined fallback where the platform has none.
 *
 * `Dispatchers.Main` is absent in a JVM host test, a background-only process, or a JVM consumer with
 * no UI toolkit, and it then throws on **first dispatch** rather than on property access — so a
 * plain `try { Dispatchers.Main }` catches nothing and the exception escapes from whichever public
 * member first starts a coroutine. No public operation may throw because a capability is
 * unavailable, so the dispatcher is probed with a real dispatch rather than inspected.
 *
 * The fallback does not weaken protection: platform effects are marshalled to the main thread by the
 * actuals themselves, so this scope governs only where the library's own observer coroutines resume.
 */
@Suppress("SwallowedException", "TooGenericExceptionCaught")
private fun mainDispatcher(): CoroutineDispatcher =
    try {
        val candidate = Dispatchers.Main
        CoroutineScope(candidate).launch { }.cancel()
        candidate
    } catch (unavailable: Throwable) {
        Dispatchers.Unconfined
    }

/**
 * The process-wide instance.
 *
 * A single mutable protection state per process is not a convenience — it is what the platform
 * imposes. One physical flag exists per window, so two registries would each believe they owned it
 * and the second to reconcile would silently undo the first.
 */
internal val shieldCore: ShieldCore by lazy { ShieldCore(createPlatformProtection()) }
