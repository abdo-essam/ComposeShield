package io.github.composeshield.internal

import kotlinx.cinterop.ExperimentalForeignApi
import platform.CoreGraphics.CGRectMake
import platform.UIKit.UIWindow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

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
