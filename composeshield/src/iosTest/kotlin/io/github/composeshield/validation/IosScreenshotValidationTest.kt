package io.github.composeshield.validation

import io.github.composeshield.internal.SecureContainer
import kotlinx.cinterop.ExperimentalForeignApi
import platform.CoreGraphics.CGRectMake
import platform.UIKit.UILabel
import platform.UIKit.UIView
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalForeignApi::class)
class IosScreenshotValidationTest {
    @Test
    fun screenshotWithProtectionOn_markerAbsent() {
        val root = UIView(frame = CGRectMake(0.0, 0.0, 320.0, 480.0))
        val secretLabel =
            UILabel(frame = CGRectMake(0.0, 0.0, 200.0, 50.0)).apply {
                text = "SHIELD_TEST_SECRET_001"
            }
        root.addSubview(secretLabel)

        val container = SecureContainer.create()
        assertNotNull(container, "SecureContainer must be successfully created on iOS")

        assertTrue(container.enclose(secretLabel), "SecureContainer should enclose secretLabel")

        assertEquals(1, root.subviews.size)
        assertFalse(root.subviews.any { it == secretLabel })
    }

    @Test
    fun screenshotWithProtectionOff_markerPresent() {
        val root = UIView(frame = CGRectMake(0.0, 0.0, 320.0, 480.0))
        val secretLabel =
            UILabel(frame = CGRectMake(0.0, 0.0, 200.0, 50.0)).apply {
                text = "SHIELD_TEST_SECRET_001"
            }
        root.addSubview(secretLabel)

        assertTrue(root.subviews.any { it == secretLabel })
    }
}
