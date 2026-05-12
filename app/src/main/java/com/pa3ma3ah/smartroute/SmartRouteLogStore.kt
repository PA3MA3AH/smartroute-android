package com.pa3ma3ah.smartroute

import androidx.compose.runtime.mutableStateListOf
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object SmartRouteLogStore {
    private const val MAX_LINES = 300

    val logs = mutableStateListOf<String>()

    @Synchronized
    fun add(message: String) {
        val time = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
        logs.add(0, "[$time] $message")

        while (logs.size > MAX_LINES) {
            logs.removeAt(logs.lastIndex)
        }
    }

    @Synchronized
    fun clear() {
        logs.clear()
    }

    @Synchronized
    fun asText(): String {
        return logs.joinToString("\n")
    }
}