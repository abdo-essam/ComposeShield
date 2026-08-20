package io.github.composeshield.sample

import androidx.compose.runtime.mutableStateListOf

/**
 * A bounded, newest-last in-memory log of sample app events.
 *
 * Capped at [MAX_ENTRIES] so a long session cannot grow it without limit.
 * Backed by [mutableStateListOf] so Compose reacts to additions automatically.
 */
internal class EventLog {

    val entries = mutableStateListOf<String>()

    /** Appends [message], evicting the oldest entry if the cap is reached. */
    fun add(message: String) {
        if (entries.size >= MAX_ENTRIES) entries.removeAt(0)
        entries.add(message)
    }

    private companion object {
        const val MAX_ENTRIES = 30
    }
}
