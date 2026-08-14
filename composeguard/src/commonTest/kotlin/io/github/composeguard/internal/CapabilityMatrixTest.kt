package io.github.composeguard.internal

import io.github.composeguard.Capability
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * SC-005 — runtime support matches the published capability matrix.
 *
 * `docs/capability-matrix.md` is a contract, not documentation (FR-024). A consumer decides whether
 * this library meets a security standard by reading it, so a row that disagrees with what the device
 * actually reports is worse than no matrix at all — it is a written claim of protection that does not
 * exist.
 *
 * The expectation is declared per platform in [expectedSupport] rather than derived from the platform
 * code, deliberately. Deriving it would make the test tautological: it would assert the
 * implementation agrees with itself and pass through any change, including one that silently
 * downgrades a capability. Written out by hand, a change to platform support fails here and forces
 * the matrix to be updated in the same commit — which is what FR-024 requires.
 */
class CapabilityMatrixTest {
    private val resolver = SupportResolver(createPlatformProtection())
    private val pristine = RegistryState()

    @Test
    fun `every capability resolves a support level on this platform`() {
        Capability.entries.forEach { capability ->
            assertNotNull(resolver.resolve(capability, pristine), "$capability resolved no support level")
        }
    }

    @Test
    fun `runtime support matches the published matrix`() {
        Capability.entries.forEach { capability ->
            assertEquals(
                expectedSupport(capability),
                resolver.resolve(capability, pristine),
                "$capability disagrees with docs/capability-matrix.md — update both, in one commit",
            )
        }
    }
}

/**
 * What `docs/capability-matrix.md` promises for [capability] on this platform, at this OS version.
 *
 * Transcribed from the matrix by hand. Where a row is version-gated the actual reads the running OS
 * version, so the same test covers every tier the library supports rather than only the newest.
 */
internal expect fun expectedSupport(capability: Capability): io.github.composeguard.SupportLevel
