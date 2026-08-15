package io.github.composeshield

/**
 * A claim on protection, held for as long as the caller needs it.
 *
 * Returned by [ComposeShield.acquire]. Protection stays active on the window while **any** handle
 * remains unreleased, so releasing one never unprotects content another still claims.
 *
 * That is why the imperative API hands back a handle rather than offering `enable()`/`disable()`: a
 * global disable would let a screen tearing down unprotect a still-visible one, and the resulting
 * exposure is invisible until someone screenshots it.
 *
 * **Thread-safety**: [release] is safe to call from any thread.
 */
public interface ProtectionHandle {
    /**
     * Releases this claim.
     *
     * **Idempotent** — calling it repeatedly is harmless and never decrements another handle's
     * claim. Protection is withdrawn from the window only once every outstanding claim, imperative
     * and declarative alike, has been released.
     *
     * Never throws, including when protection was never applied because the capability is
     * unsupported.
     */
    public fun release()
}
