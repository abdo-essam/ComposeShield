package io.github.composeshield.securebank

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Append-only, timestamped log of security-relevant events (screenshots taken, protection
 * failures) rendered on the Security screen.
 */
class SecurityLog {
    private val entries: SnapshotStateList<String> = mutableStateListOf()

    val all: List<String> get() = entries.toList()

    fun add(message: String) {
        val stamp = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
        entries.add(0, "[$stamp] $message")
        if (entries.size > MAX_ENTRIES) entries.removeAt(entries.lastIndex)
    }

    private companion object {
        const val MAX_ENTRIES = 30
    }
}
