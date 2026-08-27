package io.github.composeshield

/**
 * A claim on protection, held for as long as the caller needs it.
 *
 * Returned by [ComposeShield.protect]. Protection stays active on the window while **any** handle
 * remains unreleased, so calling [unprotect] never unprotects content another claim still needs.
 *
 * Implements [AutoCloseable] so the handle can be used with Kotlin's `use {}` block for
 * automatically scoped protection:
 * ```kotlin
 * ComposeShield.protect().use {
 *     // protection is active here
 * }  // released automatically
 * ```
 * [close] delegates to [unprotect] — they are identical in effect.
 *
 * **Thread-safety**: [unprotect] and [close] are safe to call from any thread.
 */
public interface ProtectionHandle : AutoCloseable {
    /**
     * Relinquishes this claim on protection.
     *
     * **Idempotent** — calling it repeatedly is harmless and never affects another handle's claim.
     * Protection is withdrawn from the window only once every outstanding claim, imperative
     * and declarative alike, has been released.
     *
     * Never throws, including when protection was never applied because the capability is
     * unsupported.
     */
    public fun unprotect()

    /**
     * Alias for [unprotect], satisfying [AutoCloseable].
     *
     * Provided as a default so existing implementations of this interface do not need to change.
     */
    override fun close(): Unit = unprotect()
}
