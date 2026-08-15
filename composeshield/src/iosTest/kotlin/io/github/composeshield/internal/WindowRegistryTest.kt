package io.github.composeshield.internal

import kotlinx.cinterop.ExperimentalForeignApi
import platform.CoreGraphics.CGRectMake
import platform.UIKit.UIWindow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Pins the iOS window-table lifecycle (SC-007 teardown path).
 *
 * The table holds strong references — there is no weak map in Kotlin/Native's stdlib — so `forget`
 * is what stops a dismissed window from being retained. This test pins the contract that the
 * scene-disconnect handler relies on: a registered window resolves to a stable key, and forgetting
 * the key makes both directions return `null`.
 */
@OptIn(ExperimentalForeignApi::class)
class WindowRegistryTest {
    @Test
    fun `a registered window resolves to its key until forgotten`() {
        val window = UIWindow(frame = CGRectMake(0.0, 0.0, 320.0, 480.0))
        val key = registerWindow(window)

        assertEquals(key, keyForWindow(window))
        assertEquals(window, windowFor(key))

        forget(key)

        assertNull(keyForWindow(window))
        assertNull(windowFor(key))
    }
}
