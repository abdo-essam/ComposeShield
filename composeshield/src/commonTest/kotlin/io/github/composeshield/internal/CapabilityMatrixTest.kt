package io.github.composeshield.internal

import io.github.composeshield.Capability
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

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

internal expect fun expectedSupport(capability: Capability): io.github.composeshield.SupportLevel
