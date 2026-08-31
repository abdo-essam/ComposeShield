package io.github.composeshield

import android.content.ComponentName
import androidx.activity.ComponentActivity
import org.junit.rules.ExternalResource
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

internal class RobolectricComposeHost : ExternalResource() {
    override fun before() {
        val context = RuntimeEnvironment.getApplication()
        shadowOf(context.packageManager)
            .addActivityIfNotPresent(ComponentName(context, ComponentActivity::class.java))
    }
}
