package com.pa3ma3ah.smartroute

import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object SmartRouteLogStore {
    private const val MAX_LINES = 300
    private const val TAG = "SmartRoute"

    val logs = mutableStateListOf<String>()

    @Synchronized
    fun add(message: String) {
        val time = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
        val line = "[$time] $message"

        logs.add(0, line)

        while (logs.size > MAX_LINES) {
            logs.removeAt(logs.lastIndex)
        }

        if (
            message.startsWith("ERROR:", ignoreCase = true) ||
            message.startsWith("Warning:", ignoreCase = true) ||
            message.contains("failed", ignoreCase = true)
        ) {
            Log.w(TAG, message)
        } else {
            Log.i(TAG, message)
        }
    }

    @Synchronized
    fun clear() {
        logs.clear()
        Log.i(TAG, "Logs cleared")
    }

    @Synchronized
    fun asText(): String {
        return logs.joinToString("\n")
    }
}
