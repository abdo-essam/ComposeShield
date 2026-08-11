package io.github.composeguard.internal

import io.github.composeguard.Capability
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
 * [ComposeGuard][io.github.composeguard.ComposeGuard] and
 * [SecureContent][io.github.composeguard.SecureContent] are two entry points onto one state
 * machine, not two independent features — FR-019 requires imperative and declarative requests to
 * compose through the same reference counting, so they must reach the same [ProtectionRegistry]
 * instance. This is that instance and the collaborators it owns.
 *
 * Separated from the public facade so the wiring can be constructed against a fake platform in
 * tests. A facade that built its own dependencies in an `init` block would be untestable without a
 * device, which is most of the value of Principle II's common-first split.
 */
internal class GuardCore(
    platform: PlatformProtection,
) {
    /**
     * Scope for the library's own long-lived collections.
     *
     * A [SupervisorJob] so a failure in one platform observer cannot cancel the others — losing
     * screenshot events should not also silence capture-state detection. Main-dispatched because
     * every platform observer this drives ultimately touches UI-thread-affine APIs.
     */
    private val scope = CoroutineScope(SupervisorJob() + mainDispatcher() + CoroutineName("ComposeGuard"))

    private val _protectionFailures = MutableSharedFlow<Capability>(extraBufferCapacity = FAILURE_BUFFER)

    /**
     * Mechanism failures as they happen (FR-022c).
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

    /** Screenshot events, straight from the platform. Empty where unsupported, never an error (FR-014). */
    val screenshotEvents: Flow<Unit> = platform.observeScreenshotEvents()

    private companion object {
        /**
         * Room for failures reported while nothing is collecting.
         *
         * Small on purpose. Failures are rare and arrive per reconcile; a large buffer would only
         * serve to replay a long-stale backlog to a collector that arrived much later.
         */
        const val FAILURE_BUFFER = 8
    }
}

/**
 * The main dispatcher, or an unconfined fallback where the platform has none.
 *
 * `Dispatchers.Main` is not always usable: a JVM host test, a background-only process, or a JVM
 * consumer with no UI toolkit on the classpath all leave it absent, and it then throws
 * `IllegalStateException`. Critically it throws on **first dispatch**, not on property access, so a
 * plain `try { Dispatchers.Main }` catches nothing — the exception instead escapes from whichever
 * public member first starts a coroutine, which for [io.github.composeguard.ComposeGuard.captureState]
 * is a simple property read. FR-021 forbids that: no public operation may throw because a capability
 * is unavailable.
 *
 * So the dispatcher is probed with a real dispatch rather than inspected. The probe is the only way
 * to ask the question the failure actually answers.
 *
 * The fallback does not quietly weaken protection. Platform effects are marshalled to the main
 * thread by the actuals themselves (research.md R8), so this scope governs only where the library's
 * own observer coroutines resume — and a process with no main dispatcher has no UI to resume onto.
 */
@Suppress("SwallowedException", "TooGenericExceptionCaught")
private fun mainDispatcher(): CoroutineDispatcher =
    try {
        val candidate = Dispatchers.Main
        // A real dispatch, because that is where the failure surfaces. The coroutine is cancelled
        // immediately — the probe is the dispatch attempt, not the body.
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
internal val guardCore: GuardCore by lazy { GuardCore(createPlatformProtection()) }
