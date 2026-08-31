package io.github.composeshield.sample

import androidx.compose.runtime.mutableStateListOf

internal class EventLog {
    val entries = mutableStateListOf<String>()

    fun add(message: String) {
        if (entries.size >= MAX_ENTRIES) entries.removeAt(0)
        entries.add(message)
    }

    private companion object {
        const val MAX_ENTRIES = 30
    }
}
