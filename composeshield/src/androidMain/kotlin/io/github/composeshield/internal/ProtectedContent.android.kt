package io.github.composeshield.internal

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import io.github.composeshield.Capability

@Composable
internal actual fun ProtectedContent(
    capabilities: Set<Capability>,
    content: @Composable () -> Unit,
) {
    val base = LocalContext.current
    val isActive = capabilities.isNotEmpty()
    val effectiveContext =
        remember(base, isActive) {
            if (isActive) SecureContextWrapper(base) else base
        }
    CompositionLocalProvider(LocalContext provides effectiveContext) {
        content()
    }
}

internal class SecureContextWrapper(
    base: Context,
) : android.content.ContextWrapper(base) {
    override fun getSystemService(name: String): Any? {
        val service = super.getSystemService(name)
        return if (name == WINDOW_SERVICE && service is WindowManager) {
            SecureWindowManager(service)
        } else {
            service
        }
    }
}

internal class SecureWindowManager(
    private val delegate: WindowManager,
) : WindowManager by delegate {
    override fun addView(
        view: View,
        params: ViewGroup.LayoutParams,
    ) {
        params.stampSecureFlag()
        delegate.addView(view, params)
    }

    override fun updateViewLayout(
        view: View,
        params: ViewGroup.LayoutParams,
    ) {
        params.stampSecureFlag()
        delegate.updateViewLayout(view, params)
    }

    private fun ViewGroup.LayoutParams.stampSecureFlag() {
        if (this is WindowManager.LayoutParams) {
            flags = flags or WindowManager.LayoutParams.FLAG_SECURE
        }
    }
}
