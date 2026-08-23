package io.github.composeshield.securebank

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner

/**
 * Tracks foreground/background state and the sticky session lock.
 *
 * [isSessionLocked] latches on backgrounding and only clears when the user explicitly unlocks —
 * so returning from the recents screen lands on [components.LockOverlay], not on balances.
 */
object BackgroundTracker : DefaultLifecycleObserver {
    var isBackgrounded by mutableStateOf(false)
        private set

    var isSessionLocked by mutableStateOf(false)
        private set

    override fun onStart(owner: LifecycleOwner) {
        isBackgrounded = false
    }

    override fun onStop(owner: LifecycleOwner) {
        isBackgrounded = true
        isSessionLocked = true
    }

    /** Called by the lock overlay's unlock action. */
    fun unlockSession() {
        isSessionLocked = false
    }
}
