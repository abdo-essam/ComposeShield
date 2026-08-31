package io.github.composeshield

import android.content.Context
import android.view.WindowManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogWindowProvider
import io.github.composeshield.internal.SecureContextWrapper
import io.github.composeshield.internal.SecureWindowManager
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertIsNot
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@OptIn(ExperimentalTestApi::class)
class DialogWindowProtectionTest {
    @get:Rule
    internal val host = RobolectricComposeHost()

    @Test
    fun `SecureContextWrapper returns SecureWindowManager for WINDOW_SERVICE`() =
        runComposeUiTest {
            setContent {
                val base = LocalContext.current
                val wrapper = SecureContextWrapper(base)
                val wm = wrapper.getSystemService(Context.WINDOW_SERVICE)
                assertIs<SecureWindowManager>(wm)
            }
        }

    @Test
    fun `SecureContextWrapper does not intercept non-WindowManager services`() =
        runComposeUiTest {
            setContent {
                val base = LocalContext.current
                val wrapper = SecureContextWrapper(base)
                val service = wrapper.getSystemService(Context.LAYOUT_INFLATER_SERVICE)
                assertIsNot<SecureWindowManager>(service)
            }
        }

    @Test
    fun `Dialog window inside SecureContent has FLAG_SECURE`() =
        runComposeUiTest {
            var dialogWindow: android.view.Window? = null

            setContent {
                SecureContent {
                    Dialog(onDismissRequest = {}) {
                        val view = LocalView.current
                        dialogWindow =
                            (view.parent as? DialogWindowProvider)?.window
                    }
                }
            }
            waitForIdle()

            dialogWindow?.let { window ->
                assertTrue(
                    window.isFlagSecureSet(),
                    "A dialog declared inside SecureContent must have FLAG_SECURE on its own " +
                        "window without SecureContent being placed inside the dialog body",
                )
            }
        }

    @Test
    fun `Dialog window outside SecureContent does NOT have FLAG_SECURE`() =
        runComposeUiTest {
            var dialogWindow: android.view.Window? = null

            setContent {
                Dialog(onDismissRequest = {}) {
                    val view = LocalView.current
                    dialogWindow = (view.parent as? DialogWindowProvider)?.window
                }
            }
            waitForIdle()

            dialogWindow?.let { window ->
                assertFalse(
                    window.isFlagSecureSet(),
                    "A dialog outside SecureContent must NOT have FLAG_SECURE",
                )
            }
        }

    @Test
    fun `Dialog shown after SecureContent disposes does NOT receive FLAG_SECURE`() =
        runComposeUiTest {
            var showSecure by mutableStateOf(true)
            var showDialog by mutableStateOf(false)
            var dialogWindow: android.view.Window? = null

            setContent {
                if (showSecure) {
                    SecureContent { /* no dialog here while SecureContent is active */ }
                }
                if (showDialog) {
                    Dialog(onDismissRequest = {}) {
                        val view = LocalView.current
                        dialogWindow = (view.parent as? DialogWindowProvider)?.window
                    }
                }
            }
            waitForIdle()

            showSecure = false
            waitForIdle()
            showDialog = true
            waitForIdle()

            dialogWindow?.let { window ->
                assertFalse(
                    window.isFlagSecureSet(),
                    "Once SecureContent is gone, new dialogs must not be intercepted",
                )
            }
        }
}

private fun android.view.Window.isFlagSecureSet(): Boolean =
    attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0
