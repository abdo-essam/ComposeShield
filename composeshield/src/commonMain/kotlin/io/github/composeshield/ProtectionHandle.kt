package io.github.composeshield

/**
 * A claim on protection, held for as long as the caller needs it.
 *
 * Returned by [ComposeShield.protect]. Protection stays active on the window while **any** handle
 * remains unreleased, so calling [unprotect] never unprotects content another claim still needs.
 *
 * **Thread-safety**: [unprotect] is safe to call from any thread.
 */
public interface ProtectionHandle {
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
}
