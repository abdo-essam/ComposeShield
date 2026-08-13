package io.github.composeguard

import android.content.ComponentName
import androidx.activity.ComponentActivity
import org.junit.rules.ExternalResource
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

/**
 * Registers [ComponentActivity] with the package manager so `runComposeUiTest` can launch it.
 *
 * `runComposeUiTest` starts a `ComponentActivity` through `ActivityScenario`, which resolves the
 * class via the package manager. A library module's manifest declares no activity, so resolution
 * fails with "Unable to resolve activity for Intent". This is the equivalent of the `<activity>`
 * entry an application module's debug manifest would supply.
 */
internal class RobolectricComposeHost : ExternalResource() {
    override fun before() {
        val context = RuntimeEnvironment.getApplication()
        shadowOf(context.packageManager)
            .addActivityIfNotPresent(ComponentName(context, ComponentActivity::class.java))
    }
}
